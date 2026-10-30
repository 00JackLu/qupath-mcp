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

package qupath.lib.extension.mcp.tools;

import com.google.gson.JsonObject;
import qupath.lib.extension.mcp.protocol.models.ToolDefinition;
import qupath.lib.extension.mcp.protocol.models.ToolResult;

/**
 * Interface for MCP tools.
 *
 * <p>Each tool represents a single operation that can be invoked by an LLM
 * through the MCP protocol. Tools must provide:
 * <ul>
 *   <li>A definition (name, description, input schema)</li>
 *   <li>An execute method that performs the operation</li>
 * </ul>
 */
public interface McpTool {

    /**
     * Get the tool definition including name, description, and input schema.
     */
    ToolDefinition getDefinition();

    /**
     * Execute the tool with the given arguments.
     *
     * @param arguments The input arguments as a JSON object
     * @return The result of the tool execution
     */
    ToolResult execute(JsonObject arguments);
}
