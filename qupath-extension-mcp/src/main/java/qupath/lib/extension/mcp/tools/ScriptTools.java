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
import qupath.lib.extension.mcp.util.ThreadUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.scripting.languages.GroovyLanguage;
import qupath.lib.images.ImageData;
import qupath.lib.projects.Project;
import qupath.lib.scripting.ScriptParameters;
import qupath.lib.scripting.languages.ExecutableLanguage;
import qupath.lib.scripting.languages.ScriptLanguage;

import javax.script.ScriptException;
import java.io.File;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tools for script execution.
 */
public class ScriptTools {

    private static final Logger logger = LoggerFactory.getLogger(ScriptTools.class);

    /**
     * Run a Groovy script in QuPath context.
     */
    public static class RunScript implements McpTool {

        private static final String NAME = "qupath_run_script";

        private final McpToolRegistry registry;

        public RunScript(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");

            JsonObject properties = new JsonObject();

            JsonObject script = new JsonObject();
            script.addProperty("type", "string");
            script.addProperty("description", "Groovy script code to execute. " +
                "The script has access to QPEx static methods and the current image via 'getCurrentImageData()'.");
            properties.add("script", script);

            JsonObject language = new JsonObject();
            language.addProperty("type", "string");
            language.addProperty("description", "Script language (default: groovy)");
            JsonArray enumArray = new JsonArray();
            enumArray.add("groovy");
            language.add("enum", enumArray);
            language.addProperty("default", "groovy");
            properties.add("language", language);

            JsonObject timeout = new JsonObject();
            timeout.addProperty("type", "integer");
            timeout.addProperty("description", "Timeout in milliseconds (default: 60000, max: 300000)");
            timeout.addProperty("default", 60000);
            timeout.addProperty("maximum", 300000);
            properties.add("timeout", timeout);

            inputSchema.add("properties", properties);

            JsonArray required = new JsonArray();
            required.add("script");
            inputSchema.add("required", required);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Execute a Groovy script in the QuPath context. " +
                "The script has access to QPEx methods like selectObjects(), runPlugin(), etc. " +
                "Returns the script output and any results. " +
                "WARNING: Scripts can modify data. Use with caution.",
                inputSchema,
                null,
                ToolDefinition.Annotations.destructive()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            QuPathGUI qupath = registry.getQuPath();

            // Check if script execution is enabled
            if (!registry.getConfig().isEnableScriptExecution()) {
                return ToolResult.error("Script execution is disabled in MCP configuration");
            }

            String script = arguments.get("script").getAsString();
            String language = arguments.has("language") ? arguments.get("language").getAsString() : "groovy";
            long timeoutMs = arguments.has("timeout") ?
                    Math.min(arguments.get("timeout").getAsLong(), 300000) : 60000;

            // Capture output
            StringWriter outputWriter = new StringWriter();
            StringWriter errorWriter = new StringWriter();

            try {
                // Execute script in background thread with timeout
                Callable<Object> task = () -> {
                    // Build script parameters
                    ScriptParameters.Builder builder = ScriptParameters.builder()
                            .setScript(script)
                            .setWriter(outputWriter)
                            .setErrorWriter(errorWriter);

                    // Set project and image data if available
                    if (qupath != null) {
                        builder.setProject(qupath.getProject());
                        builder.setImageData(qupath.getImageData());
                    }

                    ScriptParameters params = builder.build();

                    // Get the language (only Groovy supported for now)
                    ExecutableLanguage lang;
                    if ("groovy".equalsIgnoreCase(language)) {
                        lang = GroovyLanguage.getInstance();
                    } else {
                        throw new IllegalArgumentException("Unsupported script language: " + language + ". Only Groovy is supported.");
                    }

                    // Execute
                    return lang.execute(params);
                };

                // Run with timeout
                Object result = ThreadUtils.runInBackground(task)
                        .get(timeoutMs, TimeUnit.MILLISECONDS);

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("output", outputWriter.toString());

                if (errorWriter.toString().length() > 0) {
                    response.addProperty("errors", errorWriter.toString());
                }

                if (result != null) {
                    if (result instanceof String) {
                        response.addProperty("result", (String) result);
                    } else {
                        response.addProperty("resultType", result.getClass().getSimpleName());
                        response.addProperty("resultString", result.toString());
                    }
                }

                return ToolResult.success(response);

            } catch (TimeoutException e) {
                logger.error("Script execution timed out", e);
                return ToolResult.error("Script execution timed out after " + timeoutMs + "ms");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                logger.error("Script execution failed", cause);
                JsonObject response = new JsonObject();
                response.addProperty("success", false);
                response.addProperty("error", cause != null ? cause.getMessage() : e.getMessage());
                response.addProperty("output", outputWriter.toString());
                if (errorWriter.toString().length() > 0) {
                    response.addProperty("errors", errorWriter.toString());
                }
                return ToolResult.success(response);
            } catch (Exception e) {
                logger.error("Script execution failed", e);
                return ToolResult.error("Script execution failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * List available script files.
     */
    public static class ListScripts implements McpTool {

        private static final String NAME = "qupath_list_scripts";

        private final McpToolRegistry registry;

        public ListScripts(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");

            JsonObject properties = new JsonObject();

            JsonObject directory = new JsonObject();
            directory.addProperty("type", "string");
            directory.addProperty("description", "Directory to search for scripts (optional, defaults to project scripts directory)");
            properties.add("directory", directory);

            JsonObject recursive = new JsonObject();
            recursive.addProperty("type", "boolean");
            recursive.addProperty("description", "Search subdirectories recursively (default: true)");
            recursive.addProperty("default", true);
            properties.add("recursive", recursive);

            inputSchema.add("properties", properties);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "List available script files in the project scripts directory or a specified directory. " +
                "Returns script names and paths.",
                inputSchema,
                null,
                ToolDefinition.Annotations.readOnly()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            QuPathGUI qupath = registry.getQuPath();

            String directory = arguments.has("directory") && !arguments.get("directory").isJsonNull()
                    ? arguments.get("directory").getAsString() : null;
            boolean recursive = !arguments.has("recursive") || arguments.get("recursive").getAsBoolean();

            try {
                Path scriptsDir = null;

                if (directory != null) {
                    scriptsDir = Paths.get(directory);
                } else if (qupath != null && qupath.getProject() != null) {
                    // Try project scripts directory
                    Path projectPath = qupath.getProject().getPath();
                    if (projectPath != null) {
                        scriptsDir = projectPath.resolve("scripts");
                    }
                }

                if (scriptsDir == null || !Files.exists(scriptsDir)) {
                    JsonObject result = new JsonObject();
                    result.addProperty("message", "No scripts directory found");
                    result.add("scripts", new JsonArray());
                    return ToolResult.success(result);
                }

                List<Path> scripts = new ArrayList<>();
                String[] extensions = {".groovy", ".java", ".py", ".jython"};

                if (recursive) {
                    Files.walk(scriptsDir)
                            .filter(Files::isRegularFile)
                            .filter(p -> {
                                String name = p.getFileName().toString().toLowerCase();
                                for (String ext : extensions) {
                                    if (name.endsWith(ext)) return true;
                                }
                                return false;
                            })
                            .forEach(scripts::add);
                } else {
                    Files.list(scriptsDir)
                            .filter(Files::isRegularFile)
                            .filter(p -> {
                                String name = p.getFileName().toString().toLowerCase();
                                for (String ext : extensions) {
                                    if (name.endsWith(ext)) return true;
                                }
                                return false;
                            })
                            .forEach(scripts::add);
                }

                JsonArray scriptsArray = new JsonArray();
                Path finalScriptsDir = scriptsDir;
                for (Path script : scripts) {
                    JsonObject scriptInfo = new JsonObject();
                    scriptInfo.addProperty("name", script.getFileName().toString());
                    scriptInfo.addProperty("path", finalScriptsDir.relativize(script).toString());
                    scriptInfo.addProperty("absolutePath", script.toAbsolutePath().toString());
                    scriptsArray.add(scriptInfo);
                }

                JsonObject result = new JsonObject();
                result.addProperty("directory", scriptsDir.toAbsolutePath().toString());
                result.addProperty("count", scripts.size());
                result.add("scripts", scriptsArray);

                return ToolResult.success(result);

            } catch (Exception e) {
                logger.error("Failed to list scripts", e);
                return ToolResult.error("Failed to list scripts: " + e.getMessage(), e);
            }
        }
    }
}
