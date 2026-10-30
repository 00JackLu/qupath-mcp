/*
 * Copyright 2026 Zhaoqi Lu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package qupath.lib.extension.mcp.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.extension.mcp.McpConfiguration;
import qupath.lib.extension.mcp.McpServer;
import qupath.lib.extension.mcp.protocol.McpProtocolHandler;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
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
 * <p>This is implemented directly on top of {@link ServerSocket} with a minimal HTTP/1.1
 * request parser rather than {@code com.sun.net.httpserver}. That JDK HTTP server lives in the
 * {@code jdk.httpserver} module, which is <em>not</em> included in the trimmed runtime images that
 * ship with QuPath (jpackage/jlink only bundle a hand-picked set of modules). Relying on it caused
 * {@code NoClassDefFoundError: com/sun/net/httpserver/HttpHandler} when the extension JAR was dropped
 * into a stock QuPath install. {@code java.net.ServerSocket} is part of {@code java.base}, so it is
 * always present regardless of how QuPath's runtime was assembled.
 *
 * @see <a href="https://modelcontextprotocol.io/specification/2025-03-26/basic/transports#streamable-http">MCP Streamable HTTP Transport</a>
 */
public class HttpTransport {

    private static final Logger logger = LoggerFactory.getLogger(HttpTransport.class);

    private final McpConfiguration config;
    private final McpProtocolHandler protocolHandler;

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private Thread acceptThread;
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

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(host, port));

        executor = Executors.newFixedThreadPool(Math.max(1, config.getMaxThreads()));
        running = true;

        acceptThread = new Thread(this::acceptLoop, "mcp-http-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

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
        running = false;

        // Closing the server socket makes the blocked accept() call throw, ending the accept loop.
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.debug("Error closing MCP server socket: {}", e.getMessage());
            }
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        logger.info("MCP HTTP server stopped");
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
        return serverSocket != null && serverSocket.isBound() ? serverSocket.getLocalPort() : -1;
    }

    private void acceptLoop() {
        while (running) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (running)
                    logger.debug("Error accepting MCP connection: {}", e.getMessage());
                break;
            }
            try {
                executor.submit(() -> handleConnection(socket));
            } catch (Exception e) {
                // Executor rejected the task (e.g. during shutdown); close the socket.
                closeQuietly(socket);
            }
        }
    }

    /**
     * Read and handle a single HTTP request on the given connection, then close it.
     * Uses {@code Connection: close} semantics (one request per connection) to keep parsing simple.
     */
    private void handleConnection(Socket socket) {
        try (socket) {
            socket.setSoTimeout(Math.max(1000, config.getRequestTimeout()));
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();

            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty())
                return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                writeResponse(out, 400, "text/plain", "Bad request".getBytes(StandardCharsets.UTF_8), null);
                return;
            }
            String method = parts[0];
            String path = parts[1];
            int q = path.indexOf('?');
            if (q >= 0)
                path = path.substring(0, q);

            // Parse headers (keys lower-cased for case-insensitive lookup).
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int idx = line.indexOf(':');
                if (idx > 0)
                    headers.put(line.substring(0, idx).trim().toLowerCase(Locale.ROOT), line.substring(idx + 1).trim());
            }

            // Read body if a Content-Length was provided.
            String body = "";
            String contentLength = headers.get("content-length");
            if (contentLength != null) {
                int len = 0;
                try {
                    len = Integer.parseInt(contentLength.trim());
                } catch (NumberFormatException e) {
                    len = 0;
                }
                if (len > 0) {
                    byte[] buf = in.readNBytes(len);
                    body = new String(buf, StandardCharsets.UTF_8);
                }
            }

            route(method, path, headers, body, out);

        } catch (IOException e) {
            logger.debug("Error handling MCP connection: {}", e.getMessage());
        }
    }

    private void route(String method, String path, Map<String, String> headers, String body, OutputStream out) throws IOException {
        // CORS preflight
        if ("OPTIONS".equals(method)) {
            writeResponse(out, 204, null, null, null);
            return;
        }

        switch (path) {
            case "/mcp" -> handleMcp(method, headers, body, out);
            case "/health" -> writeResponse(out, 200, "application/json",
                    "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8), null);
            case "/.well-known/mcp" -> writeResponse(out, 200, "application/json",
                    String.format("{\"name\":\"qupath-mcp-server\",\"version\":\"%s\",\"endpoint\":\"http://%s:%d/mcp\"}",
                            McpServer.SERVER_VERSION, config.getHost(), config.getPort()).getBytes(StandardCharsets.UTF_8), null);
            // Anything else (including /.well-known/oauth-authorization-server) is an unsupported endpoint.
            default -> writeResponse(out, 404, "application/json",
                    "{\"error\":\"not_found\",\"message\":\"This endpoint is not supported\"}".getBytes(StandardCharsets.UTF_8), null);
        }
    }

    private void handleMcp(String method, Map<String, String> headers, String body, OutputStream out) throws IOException {
        if ("GET".equals(method)) {
            handleSseConnect(headers, out);
        } else if ("POST".equals(method)) {
            handlePostRequest(headers, body, out);
        } else {
            writeResponse(out, 405, "text/plain", "Method not allowed".getBytes(StandardCharsets.UTF_8), null);
        }
    }

    /**
     * Handle a POST request carrying JSON-RPC message(s).
     */
    private void handlePostRequest(Map<String, String> headers, String body, OutputStream out) throws IOException {
        if (body.isEmpty()) {
            writeResponse(out, 400, "text/plain", "Empty request body".getBytes(StandardCharsets.UTF_8), null);
            return;
        }

        // Session ID is optional in Streamable HTTP; echo back a stable value.
        String sessionId = headers.get("mcp-session-id");
        if (sessionId == null || sessionId.isEmpty())
            sessionId = UUID.randomUUID().toString();

        String responseBody = protocolHandler.handleRequest(body);

        // Notifications produce no response.
        if (responseBody == null) {
            writeResponse(out, 202, null, null, sessionId);
            return;
        }

        String acceptHeader = headers.get("accept");
        boolean acceptSse = acceptHeader != null && acceptHeader.contains("text/event-stream");
        if (acceptSse) {
            String sse = "event: message\ndata: " + responseBody + "\n\n";
            writeResponse(out, 200, "text/event-stream", sse.getBytes(StandardCharsets.UTF_8), sessionId);
        } else {
            writeResponse(out, 200, "application/json", responseBody.getBytes(StandardCharsets.UTF_8), sessionId);
        }
    }

    /**
     * Handle a GET request to /mcp: either open a (stateless) SSE stream advertising the endpoint,
     * or return server info as JSON.
     */
    private void handleSseConnect(Map<String, String> headers, OutputStream out) throws IOException {
        String acceptHeader = headers.get("accept");
        if (acceptHeader != null && acceptHeader.contains("text/event-stream")) {
            String sse = "event: endpoint\ndata: /mcp\n\n: connected\n";
            writeResponse(out, 200, "text/event-stream", sse.getBytes(StandardCharsets.UTF_8), null);
        } else {
            String info = String.format("{\"name\":\"qupath-mcp-server\",\"version\":\"%s\",\"protocolVersion\":\"2025-03-26\",\"endpoint\":\"/mcp\"}",
                    McpServer.SERVER_VERSION);
            writeResponse(out, 200, "application/json", info.getBytes(StandardCharsets.UTF_8), null);
        }
    }

    /**
     * Write a complete HTTP/1.1 response (headers + optional body) and close semantics.
     *
     * @param out         connection output stream
     * @param status      HTTP status code
     * @param contentType content type, or {@code null} for a body-less response
     * @param body        response body bytes, or {@code null} for none
     * @param sessionId   value for the Mcp-Session-Id header, or {@code null} to omit it
     */
    private void writeResponse(OutputStream out, int status, String contentType, byte[] body, String sessionId) throws IOException {
        int length = body != null ? body.length : 0;
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(status).append(' ').append(reasonPhrase(status)).append("\r\n");
        sb.append("Access-Control-Allow-Origin: *\r\n");
        sb.append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n");
        sb.append("Access-Control-Allow-Headers: Content-Type, Accept, Mcp-Session-Id\r\n");
        sb.append("Cache-Control: no-cache\r\n");
        if (contentType != null)
            sb.append("Content-Type: ").append(contentType).append("\r\n");
        if (sessionId != null)
            sb.append("Mcp-Session-Id: ").append(sessionId).append("\r\n");
        sb.append("Content-Length: ").append(length).append("\r\n");
        sb.append("Connection: close\r\n");
        sb.append("\r\n");

        out.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
        if (length > 0)
            out.write(body);
        out.flush();
    }

    private static String reasonPhrase(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            default -> "";
        };
    }

    /**
     * Read a single line terminated by LF (a trailing CR is stripped). Header/request lines are
     * ASCII, so bytes are decoded as ISO-8859-1. Returns {@code null} at end of stream.
     */
    private static String readLine(InputStream in) throws IOException {
        int b = in.read();
        if (b == -1)
            return null;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (b != -1 && b != '\n') {
            if (b != '\r')
                buf.write(b);
            b = in.read();
        }
        return buf.toString(StandardCharsets.ISO_8859_1);
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best effort
        }
    }
}
