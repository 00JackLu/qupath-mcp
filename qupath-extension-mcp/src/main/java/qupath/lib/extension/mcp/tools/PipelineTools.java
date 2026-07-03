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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.extension.mcp.protocol.models.ToolDefinition;
import qupath.lib.extension.mcp.protocol.models.ToolResult;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.workflow.Workflow;
import qupath.lib.plugins.workflow.WorkflowStep;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Tools for invoking plugins and workflows.
 */
public class PipelineTools {

    private static final Logger logger = LoggerFactory.getLogger(PipelineTools.class);

    /**
     * List available plugins.
     */
    public static class ListPlugins implements McpTool {

        private static final String NAME = "qupath_list_plugins";

        private final McpToolRegistry registry;

        public ListPlugins(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");
            inputSchema.add("properties", new JsonObject());
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "List available QuPath plugins. " +
                "Returns plugin names that can be used with qupath_run_script to run plugins.",
                inputSchema,
                null,
                ToolDefinition.Annotations.readOnly()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null) {
                return ToolResult.error("No QuPath instance available (headless mode)");
            }

            try {
                // Load available plugins via ServiceLoader
                ServiceLoader<PathPlugin> loader = ServiceLoader.load(PathPlugin.class);
                List<PathPlugin> plugins = new ArrayList<>();
                loader.forEach(plugins::add);

                JsonArray pluginsArray = new JsonArray();
                for (PathPlugin plugin : plugins) {
                    JsonObject pluginInfo = new JsonObject();
                    pluginInfo.addProperty("name", plugin.getName());
                    if (plugin.getDescription() != null) {
                        pluginInfo.addProperty("description", plugin.getDescription());
                    }
                    pluginsArray.add(pluginInfo);
                }

                JsonObject result = new JsonObject();
                result.addProperty("count", plugins.size());
                result.add("plugins", pluginsArray);

                return ToolResult.success(result);

            } catch (Exception e) {
                logger.error("Failed to list plugins", e);
                return ToolResult.error("Failed to list plugins: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Run a QuPath plugin.
     */
    public static class RunPlugin implements McpTool {

        private static final Logger logger = LoggerFactory.getLogger(RunPlugin.class);
        private static final String NAME = "qupath_run_plugin";

        private final McpToolRegistry registry;

        public RunPlugin(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");

            JsonObject properties = new JsonObject();

            JsonObject pluginName = new JsonObject();
            pluginName.addProperty("type", "string");
            pluginName.addProperty("description", "Name of the plugin to run (use qupath_list_plugins to see available plugins). " +
                "Note: Running plugins via MCP is limited - consider using qupath_run_script for more control.");
            properties.add("pluginName", pluginName);

            JsonObject args = new JsonObject();
            args.addProperty("type", "string");
            args.addProperty("description", "Plugin arguments as a JSON string (plugin-specific)");
            properties.add("args", args);

            inputSchema.add("properties", properties);

            JsonArray required = new JsonArray();
            required.add("pluginName");
            inputSchema.add("required", required);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Run a QuPath plugin with the specified arguments. " +
                "Note: For complex plugin execution, consider using qupath_run_script with QP.runPlugin() instead. " +
                "WARNING: Plugins can modify data.",
                inputSchema,
                null,
                ToolDefinition.Annotations.destructive()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null) {
                return ToolResult.error("No QuPath instance available (headless mode)");
            }

            if (!registry.getConfig().isEnablePluginExecution()) {
                return ToolResult.error("Plugin execution is disabled in MCP configuration");
            }

            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null) {
                return ToolResult.error("No image is currently open");
            }

            String pluginName = arguments.get("pluginName").getAsString();
            String args = arguments.has("args") && !arguments.get("args").isJsonNull()
                    ? arguments.get("args").getAsString() : null;

            try {
                // Find the plugin by name
                ServiceLoader<PathPlugin> loader = ServiceLoader.load(PathPlugin.class);
                PathPlugin targetPlugin = null;
                for (PathPlugin plugin : loader) {
                    if (plugin.getName().equalsIgnoreCase(pluginName)) {
                        targetPlugin = plugin;
                        break;
                    }
                }

                if (targetPlugin == null) {
                    return ToolResult.error("Plugin not found: " + pluginName +
                            ". Use qupath_list_plugins to see available plugins.");
                }

                // For now, recommend using scripts for plugin execution
                // Plugin running requires TaskRunner which needs JavaFX thread handling
                JsonObject result = new JsonObject();
                result.addProperty("success", false);
                result.addProperty("message", "Direct plugin execution via MCP is not fully supported. " +
                    "Use qupath_run_script with QP.runPlugin('" + pluginName + "', imageData, '" + (args != null ? args : "") + "') instead.");
                result.add("pluginInfo", new JsonObject());
                result.get("pluginInfo").getAsJsonObject().addProperty("name", targetPlugin.getName());
                if (targetPlugin.getDescription() != null) {
                    result.get("pluginInfo").getAsJsonObject().addProperty("description", targetPlugin.getDescription());
                }

                return ToolResult.success(result);

            } catch (Exception e) {
                logger.error("Plugin execution failed", e);
                return ToolResult.error("Plugin execution failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Get the command history/workflow for the current image.
     */
    public static class GetWorkflow implements McpTool {

        private static final String NAME = "qupath_get_workflow";

        private final McpToolRegistry registry;

        public GetWorkflow(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");
            inputSchema.add("properties", new JsonObject());
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Get the command history/workflow for the current image. " +
                "This shows the sequence of operations that have been applied. " +
                "Useful for understanding what processing has been done and for reproducibility.",
                inputSchema,
                null,
                ToolDefinition.Annotations.readOnly()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null) {
                return ToolResult.error("No QuPath instance available (headless mode)");
            }

            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null) {
                return ToolResult.error("No image is currently open");
            }

            try {
                Workflow workflow = imageData.getHistoryWorkflow();

                JsonArray steps = new JsonArray();
                for (WorkflowStep step : workflow.getSteps()) {
                    JsonObject stepInfo = new JsonObject();
                    stepInfo.addProperty("name", step.getName());
                    steps.add(stepInfo);
                }

                JsonObject result = new JsonObject();
                result.addProperty("count", steps.size());
                result.add("steps", steps);

                return ToolResult.success(result);

            } catch (Exception e) {
                logger.error("Failed to get workflow", e);
                return ToolResult.error("Failed to get workflow: " + e.getMessage(), e);
            }
        }
    }
}
