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

package qupath.lib.extension.mcp.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.extension.mcp.McpConfiguration;
import qupath.lib.extension.mcp.protocol.McpProtocolHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stdio transport for MCP protocol.
 *
 * <p>Implements MCP over standard input/output for local LLM integration
 * (e.g., with Claude Desktop). Uses newline-delimited JSON-RPC messages.
 *
 * <p>Input: JSON-RPC requests from stdin, one per line
 * <p>Output: JSON-RPC responses to stdout, one per line
 *
 * <p>Logs and debug output go to stderr to avoid interfering with the protocol.
 */
public class StdioTransport {

    private static final Logger logger = LoggerFactory.getLogger(StdioTransport.class);

    private final McpProtocolHandler protocolHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread readerThread;

    public StdioTransport(McpProtocolHandler protocolHandler) {
        this.protocolHandler = protocolHandler;
    }

    /**
     * Start reading from stdin and processing requests.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            readerThread = new Thread(this::runLoop, "QuPath-MCP-Stdio");
            readerThread.setDaemon(true);
            readerThread.start();
            logger.info("MCP stdio transport started");
        }
    }

    /**
     * Stop the stdio transport.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (readerThread != null) {
                readerThread.interrupt();
            }
            logger.info("MCP stdio transport stopped");
        }
    }

    /**
     * Check if the transport is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    private void runLoop() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);

        try {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                // Skip empty lines
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                logger.debug("Received request: {}", line.length() > 200 ? line.substring(0, 200) + "..." : line);

                try {
                    // Process the request
                    String response = protocolHandler.handleRequest(line);

                    // Send response (if any - notifications don't have responses)
                    if (response != null) {
                        writer.println(response);
                        writer.flush();
                        logger.debug("Sent response: {} bytes", response.length());
                    }
                } catch (Exception e) {
                    logger.error("Error processing request: {}", e.getMessage(), e);
                    // Continue processing next requests despite errors
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                logger.error("IO error in stdio transport: {}", e.getMessage(), e);
            }
        } finally {
            running.set(false);
            logger.info("MCP stdio transport ended");
        }
    }
}
