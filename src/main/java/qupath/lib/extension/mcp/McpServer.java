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

package qupath.lib.extension.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.extension.mcp.protocol.McpProtocolHandler;
import qupath.lib.extension.mcp.tools.AnalysisTools;
import qupath.lib.extension.mcp.tools.ImageTools;
import qupath.lib.extension.mcp.tools.McpToolRegistry;
import qupath.lib.extension.mcp.tools.ObjectTools;
import qupath.lib.extension.mcp.tools.PipelineTools;
import qupath.lib.extension.mcp.tools.ProjectTools;
import qupath.lib.extension.mcp.tools.ScriptTools;
import qupath.lib.extension.mcp.transport.HttpTransport;
import qupath.lib.extension.mcp.transport.StdioTransport;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;

import java.util.Objects;

/**
 * Main MCP server for QuPath.
 *
 * <p>This server provides an MCP (Model Context Protocol) interface that allows
 * LLMs like Claude to interact with QuPath programmatically. It supports:
 * <ul>
 *   <li>Querying image data and metadata</li>
 *   <li>Managing objects and ROIs</li>
 *   <li>Running Groovy scripts</li>
 *   <li>Managing projects and images</li>
 *   <li>Invoking plugins and workflows</li>
 * </ul>
 *
 * <p>The server can run in two transport modes:
 * <ul>
 *   <li>HTTP - for remote access and multiple clients</li>
 *   <li>Stdio - for local LLM integration (e.g., Claude Desktop)</li>
 * </ul>
 */
public class McpServer {

    private static final Logger logger = LoggerFactory.getLogger(McpServer.class);

    private static final String SERVER_NAME = "qupath-mcp-server";
    /**
     * Server version reported to MCP clients, taken from the extension JAR manifest ("dev" when run from source).
     */
    public static final String SERVER_VERSION = Objects.requireNonNullElse(
            GeneralTools.getPackageVersion(McpServer.class), "dev");

    private final McpConfiguration config;
    private final McpToolRegistry toolRegistry;
    private final McpProtocolHandler protocolHandler;

    private HttpTransport httpTransport;
    private StdioTransport stdioTransport;
    private volatile boolean running = false;

    /**
     * Create a new MCP server.
     *
     * @param config The server configuration
     * @param qupath The QuPathGUI instance (may be null in headless mode)
     */
    public McpServer(McpConfiguration config, QuPathGUI qupath) {
        this.config = config;
        this.toolRegistry = new McpToolRegistry(config, qupath);
        this.protocolHandler = new McpProtocolHandler(toolRegistry, SERVER_NAME, SERVER_VERSION);

        // Register all tools
        registerTools(qupath);

        logger.info("MCP server initialized with {} tools", toolRegistry.getToolDefinitions().size());
    }

    /**
     * Register all available tools.
     */
    private void registerTools(QuPathGUI qupath) {
        // Image tools
        toolRegistry.register(new ImageTools.GetImageInfo(toolRegistry));
        toolRegistry.register(new ImageTools.GetImageMetadata(toolRegistry));
        toolRegistry.register(new ImageTools.ReadRegion(toolRegistry));
        toolRegistry.register(new ImageTools.CaptureView(toolRegistry));
        toolRegistry.register(new ImageTools.GetViewerState(toolRegistry));

        // Object tools
        toolRegistry.register(new ObjectTools.ListObjects(toolRegistry));
        toolRegistry.register(new ObjectTools.GetObjectDetails(toolRegistry));
        toolRegistry.register(new ObjectTools.CreateAnnotation(toolRegistry));
        toolRegistry.register(new ObjectTools.DeleteObjects(toolRegistry));
        toolRegistry.register(new ObjectTools.ImportGeoJson(toolRegistry));
        toolRegistry.register(new ObjectTools.ExportGeoJson(toolRegistry));
        toolRegistry.register(new ObjectTools.GetSelectionInfo(toolRegistry));
        toolRegistry.register(new ObjectTools.GetAnnotationSummary(toolRegistry));
        toolRegistry.register(new ObjectTools.GetDetectionSummary(toolRegistry));
        toolRegistry.register(new ObjectTools.SelectAnnotation(toolRegistry));
        toolRegistry.register(new ObjectTools.UpdateAnnotationClass(toolRegistry));

        // Script tools
        toolRegistry.register(new ScriptTools.RunScript(toolRegistry));
        toolRegistry.register(new ScriptTools.ListScripts(toolRegistry));
        toolRegistry.register(new ScriptTools.ValidateScript(toolRegistry));

        // Project tools
        toolRegistry.register(new ProjectTools.GetProjectInfo(toolRegistry));
        toolRegistry.register(new ProjectTools.ListImages(toolRegistry));
        toolRegistry.register(new ProjectTools.OpenImage(toolRegistry));
        toolRegistry.register(new ProjectTools.SaveData(toolRegistry));

        // Analysis tools
        toolRegistry.register(new AnalysisTools.RunCellDetection(toolRegistry));
        toolRegistry.register(new AnalysisTools.ExportMeasurements(toolRegistry));
        toolRegistry.register(new AnalysisTools.MeasureAnnotations(toolRegistry));

        // Pipeline tools
        toolRegistry.register(new PipelineTools.ListPlugins(toolRegistry));
        toolRegistry.register(new PipelineTools.RunPlugin(toolRegistry));
        toolRegistry.register(new PipelineTools.GetWorkflow(toolRegistry));
        toolRegistry.register(new PipelineTools.SaveWorkflow(toolRegistry));
        toolRegistry.register(new PipelineTools.ExportWorkflowJson(toolRegistry));
        toolRegistry.register(new PipelineTools.ReplayWorkflow(toolRegistry));
        toolRegistry.register(new PipelineTools.StartWorkflowRecording(toolRegistry));
        toolRegistry.register(new PipelineTools.StopWorkflowRecording(toolRegistry));
    }

    /**
     * Start the MCP server.
     */
    public synchronized void start() {
        if (running) {
            logger.warn("MCP server already running");
            return;
        }

        McpConfiguration.Transport transport = config.getTransport();

        try {
            // Start HTTP transport if configured
            if (transport == McpConfiguration.Transport.HTTP ||
                transport == McpConfiguration.Transport.BOTH) {
                httpTransport = new HttpTransport(config, protocolHandler);
                httpTransport.start();
                logger.info("HTTP transport started on port {}", httpTransport.getPort());
            }

            // Start stdio transport if configured
            if (transport == McpConfiguration.Transport.STDIO ||
                transport == McpConfiguration.Transport.BOTH) {
                stdioTransport = new StdioTransport(protocolHandler);
                stdioTransport.start();
                logger.info("Stdio transport started");
            }

            running = true;
            logger.info("MCP server started successfully");

        } catch (Exception e) {
            logger.error("Failed to start MCP server: {}", e.getMessage(), e);
            stop();
            throw new RuntimeException("Failed to start MCP server", e);
        }
    }

    /**
     * Stop the MCP server.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        logger.info("Stopping MCP server...");

        if (httpTransport != null) {
            httpTransport.stop();
            httpTransport = null;
        }

        if (stdioTransport != null) {
            stdioTransport.stop();
            stdioTransport = null;
        }

        running = false;
        logger.info("MCP server stopped");
    }

    /**
     * Check if the server is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the tool registry.
     */
    public McpToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    /**
     * Get the HTTP port (if HTTP transport is running).
     */
    public int getHttpPort() {
        return httpTransport != null ? httpTransport.getPort() : -1;
    }

    /**
     * Get the number of registered tools.
     */
    public int getToolCount() {
        return toolRegistry.getToolDefinitions().size();
    }
}
