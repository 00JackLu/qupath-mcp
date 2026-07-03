/*-
 * #%L
 * QuPath MCP Extension
 * %%
 * Copyright (C) 2024 QuPath developers
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.extension.mcp.transport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.extension.mcp.McpConfiguration;
import qupath.lib.extension.mcp.protocol.McpProtocolHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * HTTP transport for MCP protocol using Streamable HTTP transport.
 *
 * <p>Implements MCP over HTTP as defined in the Streamable HTTP transport specification.
 * Supports:
 * <ul>
 *   <li>POST requests for JSON-RPC messages (stateless)</li>
 *   <li>GET requests for SSE stream (for server-to-client notifications)</li>
 *   <li>POST requests with Accept: text/event-stream for SSE responses</li>
 *   <li>Session management via Mcp-Session-Id header (optional)</li>
 * </ul>
 *
 * @see <a href="https://modelcontextprotocol.io/specification/2025-03-26/basic/transports#streamable-http">MCP Streamable HTTP Transport</a>
 */
public class HttpTransport {

    private static final Logger logger = LoggerFactory.getLogger(HttpTransport.class);

    private final McpConfiguration config;
    private final McpProtocolHandler protocolHandler;
    private HttpServer server;
    private volatile boolean running = false;

    public HttpTransport(McpConfiguration config, McpProtocolHandler protocolHandler) {
        this.config = config;
        this.protocolHandler = protocolHandler;
    }

    /**
     * Start the HTTP server.
     */
    public synchronized void start() throws IOException {
        if (running) {
            logger.warn("HTTP transport already running");
            return;
        }

        String host = config.getHost();
        int port = config.getPort();

        server = HttpServer.create(new InetSocketAddress(host, port), 0);

        // Single MCP endpoint for Streamable HTTP transport
        server.createContext("/mcp", new StreamableHttpHandler());

        // Health check endpoint
        server.createContext("/health", new HealthHandler());

        // Well-known endpoints for discovery
        server.createContext("/.well-known/oauth-authorization-server", new NotFoundHandler());
        server.createContext("/.well-known/mcp", new McpDiscoveryHandler());

        server.setExecutor(Executors.newFixedThreadPool(config.getMaxThreads()));
        server.start();
        running = true;

        logger.info("MCP HTTP server started on {}:{}", host, port);
        logger.info("MCP endpoint (Streamable HTTP): http://{}:{}/mcp", host, port);
        logger.info("Health check: http://{}:{}/health", host, port);
    }

    /**
     * Stop the HTTP server.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        if (server != null) {
            server.stop(0);
            logger.info("MCP HTTP server stopped");
        }
        running = false;
    }

    /**
     * Check if the server is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the port the server is listening on.
     */
    public int getPort() {
        return server != null ? server.getAddress().getPort() : -1;
    }

    /**
     * Handler for MCP Streamable HTTP transport.
     */
    private class StreamableHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();

            // Handle CORS preflight
            if ("OPTIONS".equals(method)) {
                sendCorsHeaders(exchange);
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers",
                    "Content-Type, Accept, Mcp-Session-Id");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Handle GET for SSE stream (persistent connection for notifications)
            if ("GET".equals(method)) {
                handleSseConnect(exchange);
                return;
            }

            // Handle POST for JSON-RPC messages
            if ("POST".equals(method)) {
                handlePostRequest(exchange);
                return;
            }

            sendError(exchange, 405, "Method not allowed");
        }

        /**
         * Handle POST request with JSON-RPC message(s).
         */
        private void handlePostRequest(HttpExchange exchange) throws IOException {
            // Check Accept header to determine response format
            String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");
            boolean acceptSse = acceptHeader != null && acceptHeader.contains("text/event-stream");

            // Read request body
            String requestBody;
            try (InputStream is = exchange.getRequestBody()) {
                requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            if (requestBody.isEmpty()) {
                sendError(exchange, 400, "Empty request body");
                return;
            }

            // Get or create session ID (optional in Streamable HTTP)
            String sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = UUID.randomUUID().toString();
            }

            // Process the request
            String responseBody = protocolHandler.handleRequest(requestBody);

            // Handle notifications (no response)
            if (responseBody == null) {
                sendCorsHeaders(exchange);
                exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
                exchange.sendResponseHeaders(202, -1);
                return;
            }

            // Send response
            if (acceptSse) {
                // Send as SSE stream
                sendSseResponse(exchange, responseBody, sessionId);
            } else {
                // Send as plain JSON (stateless)
                sendJsonResponse(exchange, responseBody, sessionId);
            }
        }

        /**
         * Handle GET request to establish SSE connection for server-to-client notifications.
         */
        private void handleSseConnect(HttpExchange exchange) throws IOException {
            // Check if client accepts SSE
            String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");

            // If client wants SSE stream, set up streaming response
            if (acceptHeader != null && acceptHeader.contains("text/event-stream")) {
                sendCorsHeaders(exchange);
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, 0);

                try (OutputStream os = exchange.getResponseBody();
                     PrintWriter writer = new PrintWriter(os, true, StandardCharsets.UTF_8)) {
                    // Send endpoint discovery event
                    writer.println("event: endpoint");
                    writer.println("data: /mcp");
                    writer.println();
                    writer.flush();

                    // Send a heartbeat and close (stateless mode)
                    writer.println(": connected");
                    writer.flush();
                }
                return;
            }

            // For other GET requests, return server info as JSON
            String response = String.format(
                "{\"name\":\"qupath-mcp-server\",\"version\":\"0.1.0\",\"protocolVersion\":\"2025-03-26\",\"endpoint\":\"/mcp\"}"
            );
            sendCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }

        /**
         * Send response as JSON.
         */
        private void sendJsonResponse(HttpExchange exchange, String responseBody, String sessionId) throws IOException {
            sendCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }

        /**
         * Send response as SSE stream.
         */
        private void sendSseResponse(HttpExchange exchange, String responseBody, String sessionId) throws IOException {
            sendCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
            exchange.sendResponseHeaders(200, 0);

            try (OutputStream os = exchange.getResponseBody();
                 PrintWriter writer = new PrintWriter(os, true, StandardCharsets.UTF_8)) {
                // Send response as SSE event
                writer.println("event: message");
                writer.print("data: ");
                writer.println(responseBody);
                writer.println();
                writer.flush();
            }
        }
    }

    /**
     * Handler for health check requests.
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"status\":\"ok\"}";
            sendCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    /**
     * Handler for MCP discovery endpoint.
     */
    private class McpDiscoveryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Return MCP server info for discovery
            String response = String.format(
                "{\"name\":\"qupath-mcp-server\",\"version\":\"0.1.0\",\"endpoint\":\"http://%s:%d/mcp\"}",
                config.getHost(), config.getPort()
            );
            sendCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    /**
     * Handler for endpoints that don't exist (returns proper JSON error).
     */
    private class NotFoundHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"error\":\"not_found\",\"message\":\"This endpoint is not supported\"}";
            sendCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    private void sendCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Accept, Mcp-Session-Id");
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        sendCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        byte[] responseBytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
