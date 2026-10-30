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
