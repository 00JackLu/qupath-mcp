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
import qupath.lib.plugins.workflow.ScriptableWorkflowStep;
import qupath.lib.plugins.workflow.Workflow;
import qupath.lib.plugins.workflow.WorkflowStep;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
                "Returns the recorded steps and a runnable Groovy 'script' that reproduces them " +
                "(QuPath's workflow-to-script), so the analysis can be re-run or saved for later. " +
                "Note: only operations QuPath records (e.g. runPlugin, classifiers) appear here - custom " +
                "logic run via qupath_run_script is not captured. Use qupath_save_workflow to persist it.",
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
                    if (step instanceof ScriptableWorkflowStep sws)
                        stepInfo.addProperty("script", sws.getScript());
                    steps.add(stepInfo);
                }

                JsonObject result = new JsonObject();
                result.addProperty("count", steps.size());
                result.add("steps", steps);
                result.addProperty("script", workflow.createScript());

                return ToolResult.success(result);

            } catch (Exception e) {
                logger.error("Failed to get workflow", e);
                return ToolResult.error("Failed to get workflow: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Persist a reproducible Groovy workflow script to a file on the local machine.
     */
    public static class SaveWorkflow implements McpTool {

        private static final String NAME = "qupath_save_workflow";

        private final McpToolRegistry registry;

        public SaveWorkflow(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");

            JsonObject properties = new JsonObject();

            JsonObject path = new JsonObject();
            path.addProperty("type", "string");
            path.addProperty("description", "Where to save the script. An absolute path is used as-is; a " +
                "relative path or a bare filename is resolved against the current project's 'scripts' " +
                "directory when a project is open, otherwise the user home directory. A '.groovy' extension " +
                "is appended if missing.");
            properties.add("path", path);

            JsonObject content = new JsonObject();
            content.addProperty("type", "string");
            content.addProperty("description", "The script text to save. If omitted, the current image's " +
                "recorded history workflow is exported automatically (QuPath's workflow-to-script). Provide " +
                "this to persist a curated pipeline - e.g. steps you ran via qupath_run_script that the " +
                "recorded workflow does not capture.");
            properties.add("content", content);

            JsonObject overwrite = new JsonObject();
            overwrite.addProperty("type", "boolean");
            overwrite.addProperty("description", "Overwrite the file if it already exists (default: false).");
            overwrite.addProperty("default", false);
            properties.add("overwrite", overwrite);

            inputSchema.add("properties", properties);
            JsonArray required = new JsonArray();
            required.add("path");
            inputSchema.add("required", required);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Save a reproducible Groovy workflow script to a file on the local computer, so a successful " +
                "analysis can be reused or run headless later (qupath script <file> -p project.qpproj). " +
                "With no 'content', it exports the current image's recorded history workflow; pass 'content' " +
                "to save a curated script the agent assembled. Requires file access to be enabled.",
                inputSchema,
                null,
                ToolDefinition.Annotations.destructive()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            if (!registry.getConfig().isEnableFileAccess()) {
                return ToolResult.error("File access is disabled in MCP configuration "
                    + "(set QUPATH_MCP_ENABLE_FILE_ACCESS=true to allow saving workflows).");
            }
            if (!arguments.has("path") || arguments.get("path").isJsonNull()
                    || arguments.get("path").getAsString().isBlank()) {
                return ToolResult.error("Missing required parameter 'path'.");
            }

            QuPathGUI qupath = registry.getQuPath();
            boolean overwrite = arguments.has("overwrite") && arguments.get("overwrite").getAsBoolean();

            // Resolve the script content: explicit 'content', else the recorded workflow.
            String script;
            String source;
            if (arguments.has("content") && !arguments.get("content").isJsonNull()) {
                script = arguments.get("content").getAsString();
                source = "provided";
            } else {
                if (qupath == null)
                    return ToolResult.error("No QuPath instance available (headless mode); pass 'content'.");
                ImageData<?> imageData = qupath.getImageData();
                if (imageData == null)
                    return ToolResult.error("No image is open to export a recorded workflow from; "
                        + "open an image or pass 'content'.");
                String body = imageData.getHistoryWorkflow().createScript();
                if (body == null || body.isBlank())
                    return ToolResult.error("The recorded workflow is empty - nothing to save. "
                        + "Run some operations first, or pass 'content'.");
                script = "// QuPath workflow exported by the MCP extension\n"
                    + "// Reproduces the recorded analysis; review parameters before reuse.\n\n" + body;
                source = "recorded-workflow";
            }

            try {
                Path target = resolvePath(qupath, arguments.get("path").getAsString());
                if (Files.exists(target) && !overwrite) {
                    return ToolResult.error("File already exists: " + target.toAbsolutePath()
                        + " (pass overwrite:true to replace it).");
                }
                if (target.getParent() != null)
                    Files.createDirectories(target.getParent());
                Files.writeString(target, script, StandardCharsets.UTF_8);

                JsonObject result = new JsonObject();
                result.addProperty("saved", target.toAbsolutePath().toString());
                result.addProperty("bytes", script.getBytes(StandardCharsets.UTF_8).length);
                result.addProperty("source", source);
                return ToolResult.success(result);

            } catch (IOException e) {
                logger.error("Failed to save workflow", e);
                return ToolResult.error("Failed to save workflow: " + e.getMessage(), e);
            }
        }

        private static Path resolvePath(QuPathGUI qupath, String rawPath) {
            Path p = Paths.get(rawPath);
            // Ensure a .groovy extension on the filename.
            String fileName = p.getFileName().toString();
            if (!fileName.contains("."))
                p = p.resolveSibling(fileName + ".groovy");

            if (p.isAbsolute())
                return p;

            // Relative: prefer the project's scripts directory, else the user home.
            Path base = null;
            if (qupath != null && qupath.getProject() != null && qupath.getProject().getPath() != null) {
                // Project path points at the .qpproj file; its parent is the project directory.
                Path projectDir = qupath.getProject().getPath().getParent();
                if (projectDir != null)
                    base = projectDir.resolve("scripts");
            }
            if (base == null)
                base = Paths.get(System.getProperty("user.home"));
            return base.resolve(p);
        }
    }
}
