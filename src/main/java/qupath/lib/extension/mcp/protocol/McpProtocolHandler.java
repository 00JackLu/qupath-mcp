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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.extension.mcp.protocol.models.ToolDefinition;
import qupath.lib.extension.mcp.protocol.models.ToolResult;
import qupath.lib.extension.mcp.tools.McpTool;
import qupath.lib.extension.mcp.tools.McpToolRegistry;

import java.nio.charset.StandardCharsets;

/**
 * Handles MCP protocol messages.
 *
 * <p>This implements the Model Context Protocol specification for JSON-RPC
 * communication between MCP clients (like Claude Desktop) and the QuPath server.
 */
public class McpProtocolHandler {

    private static final Logger logger = LoggerFactory.getLogger(McpProtocolHandler.class);

    // Compact (single-line) serialization is required on the wire: both the stdio
    // transport (newline-delimited JSON) and the SSE framing of the Streamable HTTP
    // transport treat a newline as a message/field boundary, so pretty-printed,
    // multi-line JSON would corrupt every response. Do not enable pretty-printing here.
    private static final Gson GSON = new GsonBuilder().create();

    // JSON-RPC error codes
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    // MCP-specific error codes (server-defined, start at -32000)
    public static final int NO_IMAGE_OPEN = -32001;
    public static final int NO_PROJECT_OPEN = -32002;
    public static final int SCRIPT_ERROR = -32003;
    public static final int PLUGIN_ERROR = -32004;
    public static final int TIMEOUT_ERROR = -32005;
    public static final int PERMISSION_DENIED = -32006;

    private final McpToolRegistry toolRegistry;
    private final String serverName;
    private final String serverVersion;

    public McpProtocolHandler(McpToolRegistry toolRegistry, String serverName, String serverVersion) {
        this.toolRegistry = toolRegistry;
        this.serverName = serverName;
        this.serverVersion = serverVersion;
    }

    /**
     * Process an incoming MCP request.
     *
     * @param requestBody JSON-RPC request string
     * @return JSON-RPC response string
     */
    public String handleRequest(String requestBody) {
        try {
            JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();
            return handleRequest(request);
        } catch (Exception e) {
            logger.error("Failed to parse request: {}", e.getMessage());
            return buildErrorResponse(null, PARSE_ERROR, "Parse error: " + e.getMessage());
        }
    }

    /**
     * Process an incoming MCP request.
     *
     * @param request JSON-RPC request object
     * @return JSON-RPC response string
     */
    public String handleRequest(JsonObject request) {
        JsonElement id = request.has("id") && !request.get("id").isJsonNull()
                ? request.get("id").deepCopy()
                : null;
        String method = request.has("method") ? request.get("method").getAsString() : null;
        JsonObject params = request.has("params") && !request.get("params").isJsonNull()
                ? request.getAsJsonObject("params")
                : new JsonObject();

        try {
            if (method == null) {
                return buildErrorResponse(id, INVALID_REQUEST, "Missing method");
            }

            JsonElement result = switch (method) {
                case "initialize" -> handleInitialize(params);
                case "initialized" -> null; // Notification after initialization
                case "notifications/initialized" -> null; // Notification, no response needed
                case "ping" -> handlePing();
                case "shutdown" -> handleShutdown();
                case "tools/list" -> handleToolsList();
                case "tools/call" -> handleToolsCall(params);
                case "resources/list" -> handleResourcesList();
                case "resources/read" -> handleResourcesRead(params);
                case "prompts/list" -> handlePromptsList();
                case "prompts/get" -> handlePromptsGet(params);
                default -> throw new McpException(METHOD_NOT_FOUND, "Unknown method: " + method);
            };

            // Notifications don't need a response
            if (result == null && method.startsWith("notifications/")) {
                return null;
            }

            return buildSuccessResponse(id, result);

        } catch (McpException e) {
            logger.warn("MCP error for method {}: {}", method, e.getMessage());
            return buildErrorResponse(id, e.getCode(), e.getMessage());
        } catch (Exception e) {
            logger.error("Internal error handling method {}: {}", method, e.getMessage(), e);
            return buildErrorResponse(id, INTERNAL_ERROR, "Internal error: " + e.getMessage());
        }
    }

    /**
     * Handle initialize request.
     */
    private JsonObject handleInitialize(JsonObject params) {
        JsonObject result = new JsonObject();

        // Protocol version (MCP 2025-03-26)
        result.addProperty("protocolVersion", "2025-03-26");

        // Server capabilities
        JsonObject capabilities = new JsonObject();

        // Tools capability
        JsonObject toolsCap = new JsonObject();
        toolsCap.addProperty("listChanged", true);
        capabilities.add("tools", toolsCap);

        // Resources capability
        JsonObject resourcesCap = new JsonObject();
        resourcesCap.addProperty("subscribe", true);
        resourcesCap.addProperty("listChanged", true);
        capabilities.add("resources", resourcesCap);

        result.add("capabilities", capabilities);

        // Server info
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", serverName);
        serverInfo.addProperty("version", serverVersion);
        result.add("serverInfo", serverInfo);

        return result;
    }

    /**
     * Handle ping request.
     */
    private JsonObject handlePing() {
        return new JsonObject();
    }

    /**
     * Handle shutdown request.
     */
    private JsonObject handleShutdown() {
        logger.info("Received shutdown request");
        // Return empty result - actual shutdown handled by transport layer if needed
        return new JsonObject();
    }

    /**
     * Handle tools/list request.
     */
    private JsonObject handleToolsList() {
        JsonObject result = new JsonObject();
        result.add("tools", GSON.toJsonTree(toolRegistry.getToolDefinitions()));
        return result;
    }

    /**
     * Handle tools/call request.
     */
    private JsonObject handleToolsCall(JsonObject params) {
        String toolName = params.has("name") ? params.get("name").getAsString() : null;
        JsonObject arguments = params.has("arguments") && !params.get("arguments").isJsonNull()
                ? params.getAsJsonObject("arguments")
                : new JsonObject();

        if (toolName == null) {
            throw new McpException(INVALID_PARAMS, "Missing tool name");
        }

        McpTool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            throw new McpException(METHOD_NOT_FOUND, "Unknown tool: " + toolName);
        }

        logger.debug("Executing tool: {} with arguments: {}", toolName, arguments);

        ToolResult result = toolRegistry.executeTool(toolName, arguments);
        return result.toJson();
    }

    /**
     * Handle resources/list request.
     */
    private JsonObject handleResourcesList() {
        JsonObject result = new JsonObject();
        result.add("resources", GSON.toJsonTree(toolRegistry.getResources()));
        return result;
    }

    /**
     * Handle resources/read request.
     */
    private JsonObject handleResourcesRead(JsonObject params) {
        String uri = params.has("uri") ? params.get("uri").getAsString() : null;
        if (uri == null) {
            throw new McpException(INVALID_PARAMS, "Missing resource URI");
        }

        ToolResult result = toolRegistry.readResource(uri);
        return result.toJson();
    }

    /**
     * Handle prompts/list request.
     */
    private JsonObject handlePromptsList() {
        JsonObject result = new JsonObject();
        result.add("prompts", GSON.toJsonTree(toolRegistry.getPrompts()));
        return result;
    }

    /**
     * Handle prompts/get request.
     */
    private JsonObject handlePromptsGet(JsonObject params) {
        String name = params.has("name") ? params.get("name").getAsString() : null;
        if (name == null) {
            throw new McpException(INVALID_PARAMS, "Missing prompt name");
        }

        return toolRegistry.getPrompt(name);
    }

    private String buildSuccessResponse(JsonElement id, JsonElement result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id != null) {
            response.add("id", id);
        }
        if (result != null) {
            response.add("result", result);
        }
        return GSON.toJson(response);
    }

    private String buildErrorResponse(JsonElement id, int code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id != null) {
            response.add("id", id);
        }

        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);

        return GSON.toJson(response);
    }

    /**
     * MCP-specific exception.
     */
    public static class McpException extends RuntimeException {
        private final int code;

        public McpException(int code, String message) {
            super(message);
            this.code = code;
        }

        public McpException(int code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }
}
