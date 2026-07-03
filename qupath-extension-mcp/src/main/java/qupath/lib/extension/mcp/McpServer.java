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

package qupath.lib.extension.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.extension.mcp.protocol.McpProtocolHandler;
import qupath.lib.extension.mcp.tools.ImageTools;
import qupath.lib.extension.mcp.tools.McpToolRegistry;
import qupath.lib.extension.mcp.tools.ObjectTools;
import qupath.lib.extension.mcp.tools.PipelineTools;
import qupath.lib.extension.mcp.tools.ProjectTools;
import qupath.lib.extension.mcp.tools.ScriptTools;
import qupath.lib.extension.mcp.transport.HttpTransport;
import qupath.lib.extension.mcp.transport.StdioTransport;
import qupath.lib.gui.QuPathGUI;

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
    private static final String SERVER_VERSION = "0.1.0";

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

        // Object tools
        toolRegistry.register(new ObjectTools.ListObjects(toolRegistry));
        toolRegistry.register(new ObjectTools.GetObjectDetails(toolRegistry));
        toolRegistry.register(new ObjectTools.CreateAnnotation(toolRegistry));
        toolRegistry.register(new ObjectTools.DeleteObjects(toolRegistry));
        toolRegistry.register(new ObjectTools.ImportGeoJson(toolRegistry));
        toolRegistry.register(new ObjectTools.ExportGeoJson(toolRegistry));

        // Script tools
        toolRegistry.register(new ScriptTools.RunScript(toolRegistry));
        toolRegistry.register(new ScriptTools.ListScripts(toolRegistry));

        // Project tools
        toolRegistry.register(new ProjectTools.GetProjectInfo(toolRegistry));
        toolRegistry.register(new ProjectTools.ListImages(toolRegistry));
        toolRegistry.register(new ProjectTools.OpenImage(toolRegistry));
        toolRegistry.register(new ProjectTools.SaveData(toolRegistry));

        // Pipeline tools
        toolRegistry.register(new PipelineTools.ListPlugins(toolRegistry));
        toolRegistry.register(new PipelineTools.RunPlugin(toolRegistry));
        toolRegistry.register(new PipelineTools.GetWorkflow(toolRegistry));
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
