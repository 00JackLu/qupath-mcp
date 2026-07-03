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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.extension.mcp.McpConfiguration;
import qupath.lib.extension.mcp.protocol.models.ToolDefinition;
import qupath.lib.extension.mcp.protocol.models.ToolResult;
import qupath.lib.gui.QuPathGUI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for MCP tools.
 *
 * <p>Manages tool registration and execution. Tools are registered by name
 * and can be looked up and executed via the MCP protocol handler.
 */
public class McpToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(McpToolRegistry.class);

    private final Map<String, McpTool> tools = new LinkedHashMap<>();
    private final McpConfiguration config;
    private final QuPathGUI qupath;

    public McpToolRegistry(McpConfiguration config, QuPathGUI qupath) {
        this.config = config;
        this.qupath = qupath;
    }

    /**
     * Register a tool.
     */
    public void register(McpTool tool) {
        ToolDefinition def = tool.getDefinition();
        String name = def.getName();
        if (tools.containsKey(name)) {
            logger.warn("Overwriting existing tool: {}", name);
        }
        tools.put(name, tool);
        logger.debug("Registered tool: {}", name);
    }

    /**
     * Get a tool by name.
     */
    public McpTool getTool(String name) {
        return tools.get(name);
    }

    /**
     * Get all registered tool definitions.
     */
    public Collection<ToolDefinition> getToolDefinitions() {
        return tools.values().stream()
                .map(McpTool::getDefinition)
                .toList();
    }

    /**
     * Execute a tool by name.
     */
    public ToolResult executeTool(String name, JsonObject arguments) {
        McpTool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.error("Unknown tool: " + name);
        }

        try {
            return tool.execute(arguments);
        } catch (Exception e) {
            logger.error("Tool execution failed: {}", name, e);
            return ToolResult.error("Tool execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get all registered resources (empty by default, can be overridden).
     */
    public List<ResourceDefinition> getResources() {
        return Collections.emptyList();
    }

    /**
     * Read a resource by URI.
     */
    public ToolResult readResource(String uri) {
        return ToolResult.error("Resource not found: " + uri);
    }

    /**
     * Get all registered prompts (empty by default, can be overridden).
     */
    public List<PromptDefinition> getPrompts() {
        return Collections.emptyList();
    }

    /**
     * Get a prompt by name.
     */
    public JsonObject getPrompt(String name) {
        return null;
    }

    /**
     * Get the QuPathGUI instance.
     */
    public QuPathGUI getQuPath() {
        return qupath;
    }

    /**
     * Get the configuration.
     */
    public McpConfiguration getConfig() {
        return config;
    }

    /**
     * Resource definition for MCP resources.
     */
    public static class ResourceDefinition {
        private String uri;
        private String name;
        private String description;
        private String mimeType;

        public ResourceDefinition(String uri, String name, String description, String mimeType) {
            this.uri = uri;
            this.name = name;
            this.description = description;
            this.mimeType = mimeType;
        }

        public String getUri() { return uri; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getMimeType() { return mimeType; }
    }

    /**
     * Prompt definition for MCP prompts.
     */
    public static class PromptDefinition {
        private String name;
        private String description;
        private List<PromptArgument> arguments;

        public PromptDefinition(String name, String description, List<PromptArgument> arguments) {
            this.name = name;
            this.description = description;
            this.arguments = arguments != null ? arguments : Collections.emptyList();
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<PromptArgument> getArguments() { return arguments; }
    }

    /**
     * Prompt argument definition.
     */
    public static class PromptArgument {
        private String name;
        private String description;
        private boolean required;

        public PromptArgument(String name, String description, boolean required) {
            this.name = name;
            this.description = description;
            this.required = required;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isRequired() { return required; }
    }
}
