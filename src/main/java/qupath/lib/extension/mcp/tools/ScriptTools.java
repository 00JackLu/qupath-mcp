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
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.extension.mcp.protocol.models.ToolDefinition;
import qupath.lib.extension.mcp.protocol.models.ToolResult;
import qupath.lib.extension.mcp.util.ThreadUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.logging.LogManager;
import qupath.lib.gui.logging.TextAppendable;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.scripting.languages.GroovyLanguage;
import qupath.lib.images.ImageData;
import qupath.lib.projects.Project;
import qupath.lib.scripting.ScriptParameters;
import qupath.lib.scripting.languages.ExecutableLanguage;
import qupath.lib.scripting.languages.ScriptLanguage;

import javax.script.ScriptException;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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

        /** Upper bound on captured log text, to avoid unbounded memory use for chatty scripts. */
        private static final int MAX_LOG_CAPTURE = 256 * 1024;

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
                "On failure the response includes 'error', 'errorType', 'rootCause'/'causeChain' and a " +
                "full 'stackTrace'. Note that some QuPath calls (notably runPlugin) catch their own " +
                "exceptions and return false instead of throwing; when that happens the script still " +
                "reports success:true, but the underlying error is surfaced in a 'logs' field along with " +
                "a 'warning' — always check those to confirm an operation actually did what you expected. " +
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

            // Capture WARN/ERROR log events emitted while the script runs. This is important
            // because some QuPath operations swallow their exceptions: notably QP.runPlugin()
            // catches any failure, logs it as "Unable to run plugin ...", and returns false
            // instead of throwing. Without this, such failures would leave no trace in the tool
            // response (the script "succeeds" with empty output) and only appear in QuPath's log.
            StringBuffer logBuffer = new StringBuffer();
            TextAppendable logCapture = text -> {
                if (text != null && logBuffer.length() < MAX_LOG_CAPTURE)
                    logBuffer.append(text);
            };
            boolean logCaptureRegistered = false;
            try {
                LogManager.addTextAppendableFX(logCapture);
                logCaptureRegistered = true;
            } catch (Throwable t) {
                logger.debug("Could not register log capture for script execution: {}", t.getMessage());
            }

            try {
                // Execute script in background thread with timeout
                Callable<Object> task = () -> {
                    // Build script parameters
                    ScriptParameters.Builder builder = ScriptParameters.builder()
                            .setScript(script)
                            .setWriter(outputWriter)
                            .setErrorWriter(errorWriter)
                            // Match the QuPath script editor: import the core classes and static
                            // QPEx methods so scripts can call getCurrentImageData(), getAnnotationObjects(),
                            // etc. without adding imports themselves.
                            .setDefaultImports(QPEx.getCoreClasses())
                            .setDefaultStaticImports(Collections.singletonList(QPEx.class));

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

                // Surface anything that was only logged (e.g. a plugin that failed silently).
                attachProblemLogs(response, logBuffer, true);

                return ToolResult.success(response);

            } catch (TimeoutException e) {
                JsonObject response = new JsonObject();
                response.addProperty("success", false);
                response.addProperty("error", "Script execution timed out after " + timeoutMs + "ms");
                response.addProperty("errorType", e.getClass().getName());
                response.addProperty("output", outputWriter.toString());
                // Read logs before we log server-side, so our own error line isn't echoed back.
                attachProblemLogs(response, logBuffer, false);
                logger.error("Script execution timed out", e);
                return ToolResult.success(response);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                JsonObject response = new JsonObject();
                response.addProperty("success", false);
                appendErrorDetails(response, cause);
                response.addProperty("output", outputWriter.toString());
                if (errorWriter.toString().length() > 0) {
                    response.addProperty("errors", errorWriter.toString());
                }
                // The exception is already reported structurally above; read any other logged
                // problems before we log server-side so we don't echo our own error line back.
                attachProblemLogs(response, logBuffer, false);
                logger.error("Script execution failed", cause);
                return ToolResult.success(response);
            } catch (Exception e) {
                JsonObject response = new JsonObject();
                response.addProperty("success", false);
                appendErrorDetails(response, e);
                response.addProperty("output", outputWriter.toString());
                attachProblemLogs(response, logBuffer, false);
                logger.error("Script execution failed", e);
                return ToolResult.success(response);
            } finally {
                if (logCaptureRegistered) {
                    try {
                        LogManager.removeTextAppendableFX(logCapture);
                    } catch (Throwable ignored) {
                        // best-effort cleanup
                    }
                }
            }
        }

        /**
         * Populate a response with detailed information about a thrown exception:
         * a concise message, the exception type, the full cause chain, and a stack trace.
         */
        private static void appendErrorDetails(JsonObject response, Throwable cause) {
            String message = cause.getMessage();
            if (message == null || message.isBlank())
                message = cause.getClass().getName();
            response.addProperty("error", message);
            response.addProperty("errorType", cause.getClass().getName());

            // Walk the cause chain; the root cause is often the most informative part.
            List<String> chain = new ArrayList<>();
            Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            Throwable root = cause;
            for (Throwable t = cause; t != null && seen.add(t); t = t.getCause()) {
                String m = t.getMessage();
                chain.add(t.getClass().getName() + (m != null ? ": " + m : ""));
                root = t;
            }
            if (chain.size() > 1) {
                JsonArray arr = new JsonArray();
                chain.forEach(arr::add);
                response.add("causeChain", arr);
                String rootMessage = root.getMessage();
                response.addProperty("rootCause",
                        root.getClass().getName() + (rootMessage != null ? ": " + rootMessage : ""));
            }
            response.addProperty("stackTrace", formatStackTrace(cause));
        }

        /** Format a full stack trace as a string, capped to a reasonable length. */
        private static String formatStackTrace(Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String trace = sw.toString();
            int max = 8000;
            if (trace.length() > max)
                trace = trace.substring(0, max) + "\n... (stack trace truncated)";
            return trace;
        }

        /**
         * Flush any pending FX-thread log deliveries, extract WARN/ERROR entries from the captured
         * log text, and attach them to the response. When {@code addSilentFailureHint} is true and
         * problems were found on an otherwise-successful run, also add a warning explaining that an
         * operation may have failed silently.
         */
        private void attachProblemLogs(JsonObject response, StringBuffer logBuffer, boolean addSilentFailureHint) {
            // Log text is delivered to TextAppendables on the JavaFX thread; make sure any queued
            // deliveries have run before we read the buffer.
            try {
                if (Platform.isFxApplicationThread()) {
                    // Already on FX thread: pending runLater tasks can't be flushed by waiting; skip.
                } else {
                    CountDownLatch latch = new CountDownLatch(1);
                    Platform.runLater(latch::countDown);
                    latch.await(2, TimeUnit.SECONDS);
                }
            } catch (Throwable ignored) {
                // FX toolkit may be unavailable (headless) or interrupted; use whatever we have.
            }

            String problems = filterProblemLines(logBuffer.toString());
            if (problems == null || problems.isEmpty())
                return;
            response.addProperty("logs", problems);
            if (addSilentFailureHint && response.has("success")
                    && response.get("success").getAsBoolean()) {
                response.addProperty("warning",
                        "The script finished, but warnings/errors were logged during execution (see 'logs'). "
                        + "Some operations may have failed silently — for example runPlugin() logs its error "
                        + "and returns false instead of throwing, so check the logged message and stack trace.");
            }
        }

        /**
         * Keep only WARN/ERROR blocks (header line plus their indented stack-trace/continuation
         * lines) from captured log text, dropping INFO/DEBUG/TRACE noise. Returns {@code null} if
         * nothing relevant remains.
         */
        private static String filterProblemLines(String raw) {
            if (raw == null || raw.isEmpty())
                return null;
            StringBuilder sb = new StringBuilder();
            boolean keep = false;
            for (String line : raw.split("\n", -1)) {
                if (line.startsWith("ERROR:") || line.startsWith("WARN:")) {
                    keep = true;
                    sb.append(line).append('\n');
                } else if (line.startsWith("INFO:") || line.startsWith("DEBUG:") || line.startsWith("TRACE:")) {
                    keep = false;
                } else if (keep) {
                    // Continuation line (stack-trace frame or "Caused by") belonging to a kept block.
                    sb.append(line).append('\n');
                }
            }
            String result = sb.toString().strip();
            return result.isEmpty() ? null : result;
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

    /**
     * Compile-check a Groovy script without executing it.
     */
    public static class ValidateScript implements McpTool {

        private static final String NAME = "qupath_validate_groovy_script";

        private final McpToolRegistry registry;

        public ValidateScript(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");
            JsonObject properties = new JsonObject();
            JsonObject script = new JsonObject();
            script.addProperty("type", "string");
            script.addProperty("description", "Groovy script to compile-check.");
            properties.add("script", script);
            inputSchema.add("properties", properties);
            JsonArray required = new JsonArray();
            required.add("script");
            inputSchema.add("required", required);
            inputSchema.addProperty("additionalProperties", false);
            return new ToolDefinition(
                NAME,
                "Validate a Groovy script by compiling it WITHOUT running it. Returns valid:true, or " +
                "valid:false with the compiler error message and line/column. Catches syntax and compilation " +
                "errors; note that Groovy is dynamic, so undefined methods/variables are only detected at run " +
                "time, not here. Use before qupath_run_script to catch mistakes cheaply and safely.",
                inputSchema,
                null,
                ToolDefinition.Annotations.readOnly()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            if (!arguments.has("script") || arguments.get("script").isJsonNull())
                return ToolResult.error("Missing required parameter 'script'.");
            String scriptText = arguments.get("script").getAsString();
            try {
                javax.script.ScriptEngine engine =
                        new javax.script.ScriptEngineManager(getClass().getClassLoader()).getEngineByName("groovy");
                if (engine == null)
                    return ToolResult.error("Groovy engine not available for validation");

                JsonObject result = new JsonObject();
                if (engine instanceof javax.script.Compilable compilable) {
                    try {
                        compilable.compile(scriptText);
                        result.addProperty("valid", true);
                    } catch (javax.script.ScriptException se) {
                        result.addProperty("valid", false);
                        result.addProperty("error", se.getMessage());
                        if (se.getLineNumber() >= 0)
                            result.addProperty("line", se.getLineNumber());
                        if (se.getColumnNumber() >= 0)
                            result.addProperty("column", se.getColumnNumber());
                    }
                } else {
                    result.addProperty("valid", true);
                    result.addProperty("note", "Groovy engine is not Compilable; syntax was not verified.");
                }
                return ToolResult.success(result);
            } catch (Exception e) {
                logger.error("Failed to validate script", e);
                return ToolResult.error("Failed to validate script: " + e.getMessage(), e);
            }
        }
    }
}
