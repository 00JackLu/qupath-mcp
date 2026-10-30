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

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;

/**
 * QuPath extension that provides an MCP (Model Context Protocol) server
 * for LLM integration. This allows external tools like Claude Desktop
 * to interact with QuPath programmatically.
 *
 * <p>The extension supports both GUI mode (via installExtension) and
 * headless mode (via installHeadless).
 */
public class McpExtension implements QuPathExtension {

    private static final Logger logger = LoggerFactory.getLogger(McpExtension.class);

    private McpServer server;
    private McpConfiguration config;
    private QuPathGUI qupath;

    @Override
    public void installExtension(QuPathGUI qupath) {
        logger.info("Installing MCP extension for GUI mode");
        this.qupath = qupath;
        this.config = McpConfiguration.load();
        server = new McpServer(config, qupath);

        // Add menu item after GUI is fully initialized
        Platform.runLater(() -> {
            try {
                addMenuItem(qupath);
                logger.info("MCP menu added to Tools menu");
            } catch (Exception e) {
                logger.error("Failed to add MCP menu: {}", e.getMessage());
            }
        });

        if (config.isAutoStart()) {
            server.start();
            logger.info("MCP server auto-started on port {}", config.getPort());
        }
    }

    private void addMenuItem(QuPathGUI qupath) {
        Menu extensionsMenu = qupath.getMenu("Extensions", true);
        if (extensionsMenu != null) {
            MenuTools.addMenuItems(extensionsMenu, createMcpMenu());
        }
    }

    private MenuItem createMcpMenu() {
        Menu menu = new Menu("MCP Server");

        MenuItem statusItem = new MenuItem("Show Status...");
        statusItem.setOnAction(e -> showStatusDialog());

        MenuItem startItem = new MenuItem("Start Server");
        startItem.setOnAction(e -> {
            if (server != null && !server.isRunning()) {
                try {
                    server.start();
                    showInfo("MCP Server started on port " + config.getPort());
                } catch (Exception ex) {
                    showError("Failed to start MCP server: " + ex.getMessage());
                }
            } else if (server != null && server.isRunning()) {
                showInfo("MCP Server is already running");
            }
        });

        MenuItem stopItem = new MenuItem("Stop Server");
        stopItem.setOnAction(e -> {
            if (server != null && server.isRunning()) {
                server.stop();
                showInfo("MCP Server stopped");
            } else {
                showInfo("MCP Server is not running");
            }
        });

        // Runtime toggle for view/screen capture. Off by default because it streams on-screen
        // pixels to the connected agent; the user opts in explicitly.
        CheckMenuItem captureViewItem = new CheckMenuItem("Enable View Capture");
        captureViewItem.setSelected(config.isEnableCaptureView());
        captureViewItem.selectedProperty().addListener((v, was, isNow) -> {
            config.setEnableCaptureView(isNow);
            logger.info("View capture {}", isNow ? "enabled" : "disabled");
        });

        MenuItem configItem = new MenuItem("Configuration...");
        configItem.setOnAction(e -> showConfigDialog());

        menu.getItems().addAll(statusItem, new SeparatorMenuItem(), startItem, stopItem,
                new SeparatorMenuItem(), captureViewItem, new SeparatorMenuItem(), configItem);

        return menu;
    }

    private void showStatusDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("MCP Server Status");
        alert.setHeaderText("MCP Server Extension");

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        // Status
        Label statusLabel = new Label("Status: " + (server != null && server.isRunning() ? "Running ✓" : "Stopped ✗"));
        statusLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        // Connection info
        GridPane info = new GridPane();
        info.setHgap(10);
        info.setVgap(5);

        int row = 0;
        info.add(new Label("Transport:"), 0, row);
        info.add(new Label(config.getTransport().toString()), 1, row++);

        info.add(new Label("Port:"), 0, row);
        info.add(new Label(String.valueOf(config.getPort())), 1, row++);

        info.add(new Label("MCP Endpoint:"), 0, row);
        info.add(new Label("http://127.0.0.1:" + config.getPort() + "/mcp"), 1, row++);

        info.add(new Label("Health Check:"), 0, row);
        info.add(new Label("http://127.0.0.1:" + config.getPort() + "/health"), 1, row++);

        info.add(new Label("Script Execution:"), 0, row);
        info.add(new Label(config.isEnableScriptExecution() ? "Enabled" : "Disabled"), 1, row++);

        info.add(new Label("View Capture:"), 0, row);
        info.add(new Label(config.isEnableCaptureView() ? "Enabled" : "Disabled"), 1, row++);

        // Tools count
        if (server != null) {
            info.add(new Label("Available Tools:"), 0, row);
            info.add(new Label(String.valueOf(server.getToolCount())), 1, row);
        }

        content.getChildren().addAll(statusLabel, new Separator(), info);

        // Usage instructions
        Label instructions = new Label("\nTo connect from Claude Desktop, add to claude_desktop_config.json:\n" +
            "{\n" +
            "  \"mcpServers\": {\n" +
            "    \"qupath\": {\n" +
            "      \"url\": \"http://127.0.0.1:" + config.getPort() + "/mcp\"\n" +
            "    }\n" +
            "  }\n" +
            "}\n\n" +
            "Uses Streamable HTTP transport (MCP 2025-03-26 spec).");
        instructions.setStyle("-fx-font-family: monospace; -fx-font-size: 10px;");
        instructions.setWrapText(true);
        content.getChildren().add(instructions);

        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }

    private void showConfigDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("MCP Server Configuration");
        alert.setHeaderText("Configuration Options");

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        Label configInfo = new Label(
            "MCP Server is configured via environment variables:\n\n" +
            "• QUPATH_MCP_PORT - Server port (default: 9527)\n" +
            "• QUPATH_MCP_TRANSPORT - Transport mode: HTTP, STDIO, BOTH (default: HTTP)\n" +
            "• QUPATH_MCP_AUTO_START - Auto-start server: true/false (default: true)\n" +
            "• QUPATH_MCP_ENABLE_SCRIPTS - Enable script execution: true/false (default: true)\n" +
            "• QUPATH_MCP_ENABLE_CAPTURE_VIEW - Enable view capture: true/false (default: false)\n\n" +
            "View capture can also be toggled at runtime from the MCP Server menu.\n" +
            "Set the rest before starting QuPath.\n" +
            "Example: export QUPATH_MCP_PORT=8080"
        );
        configInfo.setWrapText(true);

        content.getChildren().add(configInfo);
        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("MCP Server");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("MCP Server Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @Override
    public void installHeadless() {
        logger.info("Installing MCP extension for headless mode");
        this.config = McpConfiguration.load();
        server = new McpServer(config, null);

        if (config.isAutoStart()) {
            server.start();
        }
    }

    @Override
    public String getName() {
        return "MCP Server Extension";
    }

    @Override
    public String getDescription() {
        return "Provides an MCP (Model Context Protocol) server for LLM integration. " +
               "Enables external tools to query image data, manage ROIs, run scripts, and invoke plugins.";
    }

    @Override
    public Version getQuPathVersion() {
        return Version.parse("0.7.0");
    }
}
