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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import qupath.lib.extension.mcp.McpConfiguration;
import qupath.lib.extension.mcp.McpServer;
import qupath.lib.extension.mcp.protocol.McpProtocolHandler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for the {@link HttpTransport} Streamable HTTP transport,
 * exercising the real server over a loopback socket. This is the transport an
 * MCP client (e.g. Claude Code/Desktop) connects to via {@code http://127.0.0.1:<port>/mcp}.
 */
@SuppressWarnings("javadoc")
public class TestHttpTransport {

    private HttpTransport transport;
    private int port;
    private HttpClient client;

    @BeforeEach
    public void setUp() throws Exception {
        // Bind to an ephemeral port (0) so tests never collide with a running QuPath.
        McpConfiguration config = new McpConfiguration();
        config.setPort(0);

        McpServer server = new McpServer(config, null);
        McpProtocolHandler handler = new McpProtocolHandler(
                server.getToolRegistry(), "test-server", "0.0.1");

        transport = new HttpTransport(config, handler);
        transport.start();
        port = transport.getPort();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterEach
    public void tearDown() {
        if (transport != null)
            transport.stop();
    }

    @Test
    public void test_HealthEndpointReportsOk() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals("ok", body.get("status").getAsString());
    }

    @Test
    public void test_InitializeOverHttp() throws Exception {
        HttpResponse<String> response = postMcp("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                """);

        assertEquals(200, response.statusCode());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        assertTrue(json.has("result"), "initialize should return a result");
        assertEquals("2025-03-26",
                json.getAsJsonObject("result").get("protocolVersion").getAsString());
        // Session header should be issued for stateful clients.
        assertTrue(response.headers().firstValue("Mcp-Session-Id").isPresent(),
                "server should issue an Mcp-Session-Id header");
    }

    @Test
    public void test_ToolsListOverHttp() throws Exception {
        HttpResponse<String> response = postMcp("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """);

        assertEquals(200, response.statusCode());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(34, json.getAsJsonObject("result").getAsJsonArray("tools").size(),
                "tools/list over HTTP should advertise all 34 tools");
    }

    @Test
    public void test_ToolCallOverHttpReturnsContent() throws Exception {
        // qupath_list_objects guards on a null GUI/image and returns a tool
        // result (not a transport error), so it is safe to call headless.
        HttpResponse<String> response = postMcp("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call",
                 "params":{"name":"qupath_list_objects","arguments":{}}}
                """);

        assertEquals(200, response.statusCode());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        assertTrue(json.has("result"), "tools/call should return a result");
        assertFalse(json.has("error"), "a known tool should not raise a JSON-RPC error");
        assertTrue(json.getAsJsonObject("result").has("content"),
                "tool result should carry a content array");
    }

    @Test
    public void test_SseAcceptReturnsSingleLineJsonData() throws Exception {
        // Claude Code (and any Streamable-HTTP client) sends
        // "Accept: application/json, text/event-stream", which makes the server
        // reply as an SSE stream. SSE framing is line-based, so the JSON payload
        // on the "data:" line MUST be single-line and parseable on its own;
        // pretty-printed multi-line JSON silently corrupts the handshake.
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"jsonrpc":"2.0","id":7,"method":"initialize","params":{}}
                        """))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/event-stream"),
                "SSE-accepting client should get an event-stream response");

        // Pull the single data line and assert it parses as a complete JSON-RPC message.
        String dataLine = response.body().lines()
                .filter(l -> l.startsWith("data:"))
                .map(l -> l.substring("data:".length()).trim())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no SSE data line in response: " + response.body()));
        JsonObject json = JsonParser.parseString(dataLine).getAsJsonObject();
        assertEquals("2025-03-26",
                json.getAsJsonObject("result").get("protocolVersion").getAsString(),
                "the SSE data line must contain the full initialize result as single-line JSON");
    }

    @Test
    public void test_NotificationReturns202WithNoBody() throws Exception {
        HttpResponse<String> response = postMcp("""
                {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                """);

        assertEquals(202, response.statusCode(),
                "notifications should be accepted with 202 and no JSON-RPC body");
        assertTrue(response.body().isEmpty(), "notification response body should be empty");
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private HttpResponse<String> postMcp(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
