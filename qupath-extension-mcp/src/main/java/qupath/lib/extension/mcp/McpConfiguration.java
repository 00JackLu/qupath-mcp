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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuration for the MCP server.
 *
 * <p>Configuration can be set via:
 * <ol>
 *   <li>System properties (qupath.mcp.*)</li>
 *   <li>Environment variables (QUPATH_MCP_*)</li>
 *   <li>Config file (qupath-mcp.properties in user home or QuPath directory)</li>
 *   <li>Defaults</li>
 * </ol>
 */
public class McpConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(McpConfiguration.class);

    /**
     * Transport protocol for MCP communication.
     */
    public enum Transport {
        HTTP,
        STDIO,
        BOTH
    }

    private Transport transport = Transport.HTTP;
    private String host = "127.0.0.1";
    private int port = 9527;
    private int maxThreads = 4;
    private int requestTimeout = 60000; // 1 minute
    private boolean autoStart = true;
    private boolean enableScriptExecution = true;
    private boolean enableFileAccess = true;
    private boolean enablePluginExecution = true;

    /**
     * Load configuration from system properties, environment variables, and config files.
     */
    public static McpConfiguration load() {
        McpConfiguration config = new McpConfiguration();

        // Try to load from config file first
        config.loadFromFile();

        // Override with environment variables
        config.loadFromEnvironment();

        // Override with system properties (highest priority)
        config.loadFromSystemProperties();

        logger.info("MCP configuration: transport={}, host={}, port={}, autoStart={}",
                config.getTransport(), config.getHost(), config.getPort(), config.isAutoStart());

        return config;
    }

    private void loadFromFile() {
        // Try user home first, then QuPath directory
        Path[] configPaths = {
            Paths.get(System.getProperty("user.home"), ".qupath", "mcp.properties"),
            Paths.get(System.getProperty("user.home"), "qupath-mcp.properties"),
            Paths.get("qupath-mcp.properties")
        };

        for (Path path : configPaths) {
            if (Files.exists(path)) {
                try (InputStream is = new FileInputStream(path.toFile())) {
                    Properties props = new Properties();
                    props.load(is);
                    applyProperties(props);
                    logger.debug("Loaded MCP configuration from {}", path);
                    return;
                } catch (IOException e) {
                    logger.warn("Failed to load MCP configuration from {}: {}", path, e.getMessage());
                }
            }
        }
    }

    private void loadFromEnvironment() {
        // Environment variables use QUPATH_MCP_ prefix
        getEnv("QUPATH_MCP_TRANSPORT").ifPresent(this::setTransportFromString);
        getEnv("QUPATH_MCP_HOST").ifPresent(this::setHost);
        getEnv("QUPATH_MCP_PORT").ifPresent(s -> setPort(Integer.parseInt(s)));
        getEnv("QUPATH_MCP_MAX_THREADS").ifPresent(s -> setMaxThreads(Integer.parseInt(s)));
        getEnv("QUPATH_MCP_REQUEST_TIMEOUT").ifPresent(s -> setRequestTimeout(Integer.parseInt(s)));
        getEnv("QUPATH_MCP_AUTO_START").ifPresent(s -> setAutoStart(Boolean.parseBoolean(s)));
        getEnv("QUPATH_MCP_ENABLE_SCRIPTS").ifPresent(s -> setEnableScriptExecution(Boolean.parseBoolean(s)));
        getEnv("QUPATH_MCP_ENABLE_FILE_ACCESS").ifPresent(s -> setEnableFileAccess(Boolean.parseBoolean(s)));
        getEnv("QUPATH_MCP_ENABLE_PLUGINS").ifPresent(s -> setEnablePluginExecution(Boolean.parseBoolean(s)));
    }

    private void loadFromSystemProperties() {
        // System properties use qupath.mcp. prefix
        getProp("qupath.mcp.transport").ifPresent(this::setTransportFromString);
        getProp("qupath.mcp.host").ifPresent(this::setHost);
        getProp("qupath.mcp.port").ifPresent(s -> setPort(Integer.parseInt(s)));
        getProp("qupath.mcp.maxThreads").ifPresent(s -> setMaxThreads(Integer.parseInt(s)));
        getProp("qupath.mcp.requestTimeout").ifPresent(s -> setRequestTimeout(Integer.parseInt(s)));
        getProp("qupath.mcp.autoStart").ifPresent(s -> setAutoStart(Boolean.parseBoolean(s)));
        getProp("qupath.mcp.enableScripts").ifPresent(s -> setEnableScriptExecution(Boolean.parseBoolean(s)));
        getProp("qupath.mcp.enableFileAccess").ifPresent(s -> setEnableFileAccess(Boolean.parseBoolean(s)));
        getProp("qupath.mcp.enablePlugins").ifPresent(s -> setEnablePluginExecution(Boolean.parseBoolean(s)));
    }

    private void applyProperties(Properties props) {
        getProperty(props, "transport").ifPresent(this::setTransportFromString);
        getProperty(props, "host").ifPresent(this::setHost);
        getProperty(props, "port").ifPresent(s -> setPort(Integer.parseInt(s)));
        getProperty(props, "maxThreads").ifPresent(s -> setMaxThreads(Integer.parseInt(s)));
        getProperty(props, "requestTimeout").ifPresent(s -> setRequestTimeout(Integer.parseInt(s)));
        getProperty(props, "autoStart").ifPresent(s -> setAutoStart(Boolean.parseBoolean(s)));
        getProperty(props, "enableScripts").ifPresent(s -> setEnableScriptExecution(Boolean.parseBoolean(s)));
        getProperty(props, "enableFileAccess").ifPresent(s -> setEnableFileAccess(Boolean.parseBoolean(s)));
        getProperty(props, "enablePlugins").ifPresent(s -> setEnablePluginExecution(Boolean.parseBoolean(s)));
    }

    private java.util.Optional<String> getProp(String key) {
        String value = System.getProperty(key);
        return java.util.Optional.ofNullable(value);
    }

    private java.util.Optional<String> getEnv(String key) {
        String value = System.getenv(key);
        return java.util.Optional.ofNullable(value);
    }

    private java.util.Optional<String> getProperty(Properties props, String key) {
        String value = props.getProperty(key);
        return java.util.Optional.ofNullable(value);
    }

    private void setTransportFromString(String value) {
        this.transport = Transport.valueOf(value.toUpperCase());
    }

    // Getters and setters

    public Transport getTransport() {
        return transport;
    }

    public void setTransport(Transport transport) {
        this.transport = transport;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public boolean isEnableScriptExecution() {
        return enableScriptExecution;
    }

    public void setEnableScriptExecution(boolean enableScriptExecution) {
        this.enableScriptExecution = enableScriptExecution;
    }

    public boolean isEnableFileAccess() {
        return enableFileAccess;
    }

    public void setEnableFileAccess(boolean enableFileAccess) {
        this.enableFileAccess = enableFileAccess;
    }

    public boolean isEnablePluginExecution() {
        return enablePluginExecution;
    }

    public void setEnablePluginExecution(boolean enablePluginExecution) {
        this.enablePluginExecution = enablePluginExecution;
    }
}
