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

package qupath.lib.extension.mcp.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import qupath.lib.extension.mcp.McpConfiguration;
import qupath.lib.extension.mcp.McpServer;
import qupath.lib.extension.mcp.tools.McpToolRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("javadoc")
public class TestMcpProtocolHandler {

    @Test
    public void test_NumericIdPreservesType() {
        var response = handle("""
                {"jsonrpc":"2.0","id":1,"method":"ping","params":{}}
                """);

        var id = response.get("id");
        assertTrue(id.isJsonPrimitive());
        assertTrue(id.getAsJsonPrimitive().isNumber());
        assertEquals(1, id.getAsInt());
    }

    @Test
    public void test_StringIdPreservesType() {
        var response = handle("""
                {"jsonrpc":"2.0","id":"request-1","method":"ping","params":{}}
                """);

        var id = response.get("id");
        assertTrue(id.isJsonPrimitive());
        assertTrue(id.getAsJsonPrimitive().isString());
        assertEquals("request-1", id.getAsString());
    }

    @Test
    public void test_NullIdIsOmitted() {
        var response = handle("""
                {"jsonrpc":"2.0","id":null,"method":"ping","params":{}}
                """);

        assertFalse(response.has("id"));
    }

    @Test
    public void test_InitializeReturnsCapabilities() {
        var response = handle("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                """);

        // initialize is a request with an id, so it gets a result
        assertTrue(response.has("result"), "initialize should return a result");
        assertFalse(response.has("error"), "initialize should not return an error");

        JsonObject result = response.getAsJsonObject("result");
        assertEquals("2025-03-26", result.get("protocolVersion").getAsString(),
                "should report MCP 2025-03-26 protocol version");

        JsonObject capabilities = result.getAsJsonObject("capabilities");
        assertNotNull(capabilities, "should advertise capabilities");
        assertTrue(capabilities.has("tools"), "should advertise tools capability");
        assertTrue(capabilities.has("resources"), "should advertise resources capability");

        JsonObject serverInfo = result.getAsJsonObject("serverInfo");
        assertEquals("test-server", serverInfo.get("name").getAsString());
    }

    @Test
    public void test_InitializedNotificationReturnsNoResponse() {
        // notifications/initialized is a notification (no response expected)
        String raw = handleRaw("""
                {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                """);
        assertNullResponse(raw);
    }

    @Test
    public void test_ToolsListAdvertisesAllTools() {
        var response = handle("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
                """);

        assertTrue(response.has("result"), "tools/list should return a result");
        assertFalse(response.has("error"), "tools/list should not error");

        JsonArray tools = response.getAsJsonObject("result").getAsJsonArray("tools");
        assertNotNull(tools, "tools list should not be null");
        // 18 tools are registered by McpServer
        assertEquals(18, tools.size(), "expected all 18 registered tools");

        // Each tool must have name, description, inputSchema
        for (JsonElement element : tools) {
            JsonObject tool = element.getAsJsonObject();
            assertTrue(tool.has("name"), "tool should have a name");
            assertTrue(tool.has("description"), "tool should have a description");
            assertTrue(tool.has("inputSchema"), "tool should have an inputSchema");
            assertFalse(tool.get("name").getAsString().isBlank(), "tool name should not be blank");
        }
    }

    @Test
    public void test_ToolsListIncludesExpectedTools() {
        var response = handle("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
                """);

        JsonArray tools = response.getAsJsonObject("result").getAsJsonArray("tools");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (JsonElement element : tools) {
            names.add(element.getAsJsonObject().get("name").getAsString());
        }

        // Spot-check a representative tool from each category
        assertTrue(names.contains("qupath_run_script"), "should include qupath_run_script");
        assertTrue(names.contains("qupath_list_objects"), "should include qupath_list_objects");
        assertTrue(names.contains("qupath_read_region"), "should include qupath_read_region");
        assertTrue(names.contains("qupath_get_project_info"), "should include qupath_get_project_info");
        assertTrue(names.contains("qupath_run_plugin"), "should include qupath_run_plugin");
    }

    @Test
    public void test_UnknownToolReturnsError() {
        var response = handle("""
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"does_not_exist","arguments":{}}}
                """);

        // Unknown tool name is caught by the registry lookup and raised as a
        // METHOD_NOT_FOUND JSON-RPC error (the tool never runs, so no ToolResult).
        assertTrue(response.has("error"), "unknown tool should return a JSON-RPC error");
        JsonObject error = response.getAsJsonObject("error");
        assertEquals(-32601, error.get("code").getAsInt(), "should be METHOD_NOT_FOUND");
        assertTrue(error.get("message").getAsString().contains("does_not_exist"),
                "error message should name the unknown tool");
    }

    @Test
    public void test_UnknownMethodReturnsMethodNotFound() {
        var response = handle("""
                {"jsonrpc":"2.0","id":1,"method":"bogus/method","params":{}}
                """);

        assertTrue(response.has("error"), "unknown method should return an error");
        JsonObject error = response.getAsJsonObject("error");
        assertEquals(-32601, error.get("code").getAsInt(), "should be METHOD_NOT_FOUND");
    }

    @Test
    public void test_PingReturnsEmptyResult() {
        var response = handle("""
                {"jsonrpc":"2.0","id":1,"method":"ping","params":{}}
                """);

        assertTrue(response.has("result"), "ping should return a result");
        assertTrue(response.getAsJsonObject("result").isEmpty(), "ping result should be empty object");
    }

    @Test
    public void test_RunScriptExecutesGroovyScript() {
        var response = handle("""
                {"jsonrpc":"2.0","id":1,"method":"tools/call",
                 "params":{"name":"qupath_run_script","arguments":{"script":"println 'groovy-ok'","timeout":60000}}}
                """);

        assertFalse(response.has("error"), "script execution should not raise a JSON-RPC error");

        var result = response.getAsJsonObject("result");
        assertFalse(result.get("isError").getAsBoolean(), "script tool should not return an MCP error");

        var text = result.getAsJsonArray("content")
                .get(0).getAsJsonObject()
                .get("text").getAsString();
        var scriptResult = JsonParser.parseString(text).getAsJsonObject();

        assertTrue(scriptResult.get("success").getAsBoolean(), text);
        assertEquals("groovy-ok\n", scriptResult.get("output").getAsString());
    }

    /**
     * Handle a request and return the parsed response object. Uses a registry
     * with all tools registered (via McpServer) so tools/list reflects real state.
     */
    private static JsonObject handle(String request) {
        return JsonParser.parseString(handleRaw(request)).getAsJsonObject();
    }

    /**
     * Handle a request and return the raw response string (may be null for notifications).
     */
    private static String handleRaw(String request) {
        // McpServer registers all tools but needs a QuPathGUI for some of them.
        // Passing null is fine for listing/calling tools that guard on it.
        McpServer server = new McpServer(new McpConfiguration(), null);
        var handler = new McpProtocolHandler(server.getToolRegistry(), "test-server", "0.0.1");
        return handler.handleRequest(request);
    }

    private static void assertNullResponse(String raw) {
        // Notifications return null (no response body)
        assertTrue(raw == null || raw.isEmpty(),
                "notification should produce no response, got: " + raw);
    }
}
