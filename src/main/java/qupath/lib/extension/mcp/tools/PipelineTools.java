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
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.scripting.languages.GroovyLanguage;
import qupath.lib.images.ImageData;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.workflow.ScriptableWorkflowStep;
import qupath.lib.plugins.workflow.Workflow;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.scripting.ScriptParameters;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Tools for invoking plugins and workflows.
 */
public class PipelineTools {

    private static final Logger logger = LoggerFactory.getLogger(PipelineTools.class);

    // Shared state for the explicit workflow-recording session markers.
    private static volatile boolean recording = false;
    private static volatile int recordingBaseline = 0;

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

            JsonObject name = new JsonObject();
            name.addProperty("type", "string");
            name.addProperty("description", "Short name for the workflow. The file is saved as " +
                "'<name>_<yyyy-MM-dd_HH-mm-ss>.groovy'. Default 'workflow'. Illegal filename characters " +
                "are replaced with underscores.");
            properties.add("name", name);

            JsonObject path = new JsonObject();
            path.addProperty("type", "string");
            path.addProperty("description", "Directory to save into. Optional: by default it goes to a " +
                "'qupath_workflow' folder inside the current project (or your home directory if no project " +
                "is open). A relative path is resolved against that same base. If you instead pass a path " +
                "ending in '.groovy', it is used as the exact file name (ignoring 'name'/date).");
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
            overwrite.addProperty("description", "Overwrite the file if it already exists (default: false). " +
                "Rarely needed since the filename includes a timestamp.");
            overwrite.addProperty("default", false);
            properties.add("overwrite", overwrite);

            inputSchema.add("properties", properties);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Save a reproducible Groovy workflow script to a file on the local computer, so a successful " +
                "analysis can be reused or run headless later (qupath script <file> -p project.qpproj). " +
                "By default it writes '<name>_<timestamp>.groovy' into a 'qupath_workflow' folder inside the " +
                "current project (or the user home directory if no project is open). With no 'content', it " +
                "exports the current image's recorded history workflow; pass 'content' to save a curated " +
                "script the agent assembled. Requires file access to be enabled.",
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

            QuPathGUI qupath = registry.getQuPath();
            boolean overwrite = arguments.has("overwrite") && arguments.get("overwrite").getAsBoolean();
            String name = arguments.has("name") && !arguments.get("name").isJsonNull()
                    ? arguments.get("name").getAsString() : "workflow";
            String rawPath = arguments.has("path") && !arguments.get("path").isJsonNull()
                    ? arguments.get("path").getAsString() : null;

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
                Path target = resolveTarget(qupath, rawPath, name);
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

        /**
         * Work out where to write the workflow. By default this is
         * {@code <base>/qupath_workflow/<name>_<timestamp>.groovy}, where {@code base} is the open
         * project's directory or, failing that, the user home. A caller-supplied {@code rawPath} is
         * treated as a target directory (relative paths resolve against {@code base}) unless it ends
         * in {@code .groovy}, in which case it is used as the exact file name.
         */
        private static Path resolveTarget(QuPathGUI qupath, String rawPath, String name) {
            Path base = defaultBase(qupath);

            // Explicit '.groovy' path => exact file.
            if (rawPath != null && rawPath.toLowerCase().endsWith(".groovy")) {
                Path p = Paths.get(rawPath);
                return p.isAbsolute() ? p : base.resolve(p);
            }

            // Otherwise choose a directory and generate a dated, named filename inside it.
            Path dir;
            if (rawPath != null && !rawPath.isBlank()) {
                Path p = Paths.get(rawPath);
                dir = p.isAbsolute() ? p : base.resolve(p);
            } else {
                dir = base.resolve("qupath_workflow");
            }
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            return dir.resolve(sanitizeName(name) + "_" + timestamp + ".groovy");
        }

        /** Project directory if a project is open, otherwise the user home directory. */
        private static Path defaultBase(QuPathGUI qupath) {
            if (qupath != null && qupath.getProject() != null && qupath.getProject().getPath() != null) {
                // Project path points at the .qpproj file; its parent is the project directory.
                Path projectDir = qupath.getProject().getPath().getParent();
                if (projectDir != null)
                    return projectDir;
            }
            return Paths.get(System.getProperty("user.home"));
        }

        /** Make a workflow name safe to use as a filename stem. */
        private static String sanitizeName(String name) {
            String s = name == null ? "" : name.trim();
            if (s.toLowerCase().endsWith(".groovy"))
                s = s.substring(0, s.length() - ".groovy".length());
            s = s.replaceAll("[^A-Za-z0-9._-]", "_");
            return s.isBlank() ? "workflow" : s;
        }
    }

    /**
     * Export the recorded workflow as a structured JSON provenance record.
     */
    public static class ExportWorkflowJson implements McpTool {

        private static final String NAME = "qupath_export_workflow_json";

        private final McpToolRegistry registry;

        public ExportWorkflowJson(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");
            JsonObject properties = new JsonObject();
            JsonObject path = new JsonObject();
            path.addProperty("type", "string");
            path.addProperty("description", "Optional file path to also write the JSON to (requires file access). " +
                "If omitted the JSON is only returned.");
            properties.add("path", path);
            inputSchema.add("properties", properties);
            inputSchema.addProperty("additionalProperties", false);
            return new ToolDefinition(
                NAME,
                "Export the current image's recorded workflow as a structured JSON provenance record: QuPath " +
                "version, image name, each step's name/parameters/generated Groovy, and a runnable script that " +
                "reproduces the analysis. This is the machine-readable trace used for deterministic replay.",
                inputSchema,
                null,
                ToolDefinition.Annotations.readOnly()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null)
                return ToolResult.error("No QuPath instance available (headless mode)");
            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null)
                return ToolResult.error("No image is currently open");
            try {
                Workflow workflow = imageData.getHistoryWorkflow();
                JsonObject record = new JsonObject();
                record.addProperty("qupathVersion", GeneralTools.getVersion());
                record.addProperty("image", imageData.getServer().getMetadata().getName());
                record.addProperty("imageType", String.valueOf(imageData.getImageType()));

                JsonArray steps = new JsonArray();
                for (WorkflowStep step : workflow.getSteps()) {
                    JsonObject s = new JsonObject();
                    s.addProperty("name", step.getName());
                    if (step instanceof ScriptableWorkflowStep sws)
                        s.addProperty("script", sws.getScript());
                    var pm = step.getParameterMap();
                    if (pm != null && !pm.isEmpty()) {
                        JsonObject params = new JsonObject();
                        for (var entry : pm.entrySet())
                            params.addProperty(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                        s.add("parameters", params);
                    }
                    steps.add(s);
                }
                record.addProperty("stepCount", steps.size());
                record.add("steps", steps);
                record.addProperty("script", workflow.createScript());

                if (arguments.has("path") && !arguments.get("path").isJsonNull()) {
                    if (!registry.getConfig().isEnableFileAccess())
                        return ToolResult.error("File access is disabled; omit 'path' to return JSON inline, or "
                            + "set QUPATH_MCP_ENABLE_FILE_ACCESS=true.");
                    Path target = Paths.get(arguments.get("path").getAsString());
                    if (target.getParent() != null)
                        Files.createDirectories(target.getParent());
                    Files.writeString(target, record.toString(), StandardCharsets.UTF_8);
                    record.addProperty("saved", target.toAbsolutePath().toString());
                }
                return ToolResult.success(record);
            } catch (Exception e) {
                logger.error("Failed to export workflow JSON", e);
                return ToolResult.error("Failed to export workflow JSON: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Replay a workflow deterministically, without any LLM involvement, by executing its Groovy script.
     */
    public static class ReplayWorkflow implements McpTool {

        private static final String NAME = "qupath_replay_workflow";

        private final McpToolRegistry registry;

        public ReplayWorkflow(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");
            JsonObject properties = new JsonObject();
            JsonObject path = new JsonObject();
            path.addProperty("type", "string");
            path.addProperty("description", "Path to a saved workflow: a .groovy script, or a .json record " +
                "produced by qupath_export_workflow_json (its 'script' field is executed).");
            properties.add("path", path);
            JsonObject script = new JsonObject();
            script.addProperty("type", "string");
            script.addProperty("description", "Inline Groovy to replay instead of a file. Provide either 'path' " +
                "or 'script'.");
            properties.add("script", script);
            inputSchema.add("properties", properties);
            inputSchema.addProperty("additionalProperties", false);
            return new ToolDefinition(
                NAME,
                "Replay a saved workflow deterministically by running its Groovy in the QuPath context - with no " +
                "LLM involvement. Reports object counts before and after so the replay can be compared with the " +
                "original run. Executes code, so it requires script execution to be enabled.",
                inputSchema,
                null,
                ToolDefinition.Annotations.destructive()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            if (!registry.getConfig().isEnableScriptExecution())
                return ToolResult.error("Script execution is disabled in MCP configuration "
                    + "(set QUPATH_MCP_ENABLE_SCRIPTS=true).");
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null)
                return ToolResult.error("No QuPath instance available (headless mode)");

            String scriptText;
            try {
                scriptText = resolveScript(arguments);
            } catch (Exception e) {
                return ToolResult.error(e.getMessage());
            }
            if (scriptText == null || scriptText.isBlank())
                return ToolResult.error("Nothing to replay: provide 'path' to a .groovy/.json workflow or inline "
                    + "'script'.");

            ImageData<?> imageData = qupath.getImageData();
            int annBefore = 0, detBefore = 0;
            if (imageData != null) {
                annBefore = imageData.getHierarchy().getAnnotationObjects().size();
                detBefore = imageData.getHierarchy().getDetectionObjects().size();
            }

            StringWriter out = new StringWriter();
            StringWriter err = new StringWriter();
            JsonObject result = new JsonObject();
            try {
                ScriptParameters params = ScriptParameters.builder()
                        .setScript(scriptText)
                        .setWriter(out)
                        .setErrorWriter(err)
                        .setDefaultImports(QPEx.getCoreClasses())
                        .setDefaultStaticImports(Collections.singletonList(QPEx.class))
                        .setProject(qupath.getProject())
                        .setImageData(qupath.getImageData())
                        .build();
                GroovyLanguage.getInstance().execute(params);
                result.addProperty("status", "success");
            } catch (Exception e) {
                logger.error("Workflow replay failed", e);
                result.addProperty("status", "failed");
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                result.addProperty("error", cause.getMessage() != null ? cause.getMessage() : cause.toString());
            }

            result.addProperty("output", out.toString());
            if (!err.toString().isBlank())
                result.addProperty("errors", err.toString());
            if (imageData != null) {
                result.addProperty("annotationsBefore", annBefore);
                result.addProperty("annotationsAfter", imageData.getHierarchy().getAnnotationObjects().size());
                result.addProperty("detectionsBefore", detBefore);
                result.addProperty("detectionsAfter", imageData.getHierarchy().getDetectionObjects().size());
            }
            return ToolResult.success(result);
        }

        private String resolveScript(JsonObject arguments) throws IOException {
            if (arguments.has("script") && !arguments.get("script").isJsonNull())
                return arguments.get("script").getAsString();
            if (!arguments.has("path") || arguments.get("path").isJsonNull())
                return null;
            if (!registry.getConfig().isEnableFileAccess())
                throw new IllegalStateException("File access is disabled; pass 'script' inline or set "
                    + "QUPATH_MCP_ENABLE_FILE_ACCESS=true.");
            Path p = Paths.get(arguments.get("path").getAsString());
            if (!Files.exists(p))
                throw new IllegalStateException("Workflow file not found: " + p.toAbsolutePath());
            String content = Files.readString(p, StandardCharsets.UTF_8);
            if (p.toString().toLowerCase().endsWith(".json")) {
                JsonObject record = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
                if (record.has("script"))
                    return record.get("script").getAsString();
                throw new IllegalStateException("JSON workflow has no 'script' field to replay.");
            }
            return content;
        }
    }

    /**
     * Mark the start of a workflow-recording session for the current image.
     */
    public static class StartWorkflowRecording implements McpTool {

        private static final String NAME = "qupath_start_workflow_recording";

        private final McpToolRegistry registry;

        public StartWorkflowRecording(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");
            JsonObject properties = new JsonObject();
            JsonObject clear = new JsonObject();
            clear.addProperty("type", "boolean");
            clear.addProperty("description", "Clear the existing recorded history so the session starts fresh " +
                "(default true). Set false to keep prior steps and only mark a new baseline.");
            clear.addProperty("default", true);
            properties.add("clear", clear);
            inputSchema.add("properties", properties);
            inputSchema.addProperty("additionalProperties", false);
            return new ToolDefinition(
                NAME,
                "Begin a workflow-recording session for the current image. QuPath records commands into the " +
                "image history automatically; this marks a start point (optionally clearing prior history) so " +
                "the session can later be exported (qupath_export_workflow_json) and replayed.",
                inputSchema,
                null,
                ToolDefinition.Annotations.write()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null)
                return ToolResult.error("No QuPath instance available (headless mode)");
            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null)
                return ToolResult.error("No image is currently open");
            boolean clear = !arguments.has("clear") || arguments.get("clear").getAsBoolean();
            Workflow workflow = imageData.getHistoryWorkflow();
            if (clear)
                workflow.clear();
            recordingBaseline = workflow.size();
            recording = true;
            JsonObject result = new JsonObject();
            result.addProperty("recording", true);
            result.addProperty("cleared", clear);
            result.addProperty("stepsAtStart", recordingBaseline);
            return ToolResult.success(result);
        }
    }

    /**
     * Mark the end of a workflow-recording session and report what was recorded.
     */
    public static class StopWorkflowRecording implements McpTool {

        private static final String NAME = "qupath_stop_workflow_recording";

        private final McpToolRegistry registry;

        public StopWorkflowRecording(McpToolRegistry registry) {
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
                "End the current workflow-recording session and report how many steps were recorded since the " +
                "matching start, plus the runnable Groovy script for the recorded workflow. Pair with " +
                "qupath_save_workflow / qupath_export_workflow_json to persist it.",
                inputSchema,
                null,
                ToolDefinition.Annotations.readOnly()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null)
                return ToolResult.error("No QuPath instance available (headless mode)");
            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null)
                return ToolResult.error("No image is currently open");
            Workflow workflow = imageData.getHistoryWorkflow();
            int recorded = Math.max(0, workflow.size() - recordingBaseline);
            boolean wasRecording = recording;
            recording = false;
            JsonObject result = new JsonObject();
            result.addProperty("recording", false);
            result.addProperty("wasRecording", wasRecording);
            result.addProperty("stepsRecorded", recorded);
            result.addProperty("totalSteps", workflow.size());
            result.addProperty("script", workflow.createScript());
            return ToolResult.success(result);
        }
    }
}
