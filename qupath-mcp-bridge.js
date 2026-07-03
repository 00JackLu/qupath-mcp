#!/usr/bin/env node
/**
 * QuPath MCP Bridge - stdio to HTTP transport
 *
 * This script bridges between stdio (used by MCP clients)
 * and HTTP (used by QuPath MCP server).
 *
 * Usage: node qupath-mcp-bridge.js
 * The QuPath MCP server should be running at http://127.0.0.1:9527
 */

const http = require('http');

const MCP_URL = process.env.QUPATH_MCP_URL || 'http://127.0.0.1:9527/mcp';

// Log to stderr (doesn't interfere with stdio)
console.error('QuPath MCP Bridge started');
console.error('Connecting to:', MCP_URL);

// Read line by line from stdin
const readline = require('readline');
const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: false
});

rl.on('line', async (line) => {
    if (!line.trim()) return;

    try {
        const response = await sendToHttp(line.trim());
        if (response) {
            process.stdout.write(response + '\n');
            process.stdout.write('\n'); // Extra newline for MCP protocol
        }
    } catch (error) {
        console.error('Error:', error.message);
        const errorResponse = {
            jsonrpc: '2.0',
            id: null,
            error: {
                code: -32603,
                message: error.message
            }
        };
        process.stdout.write(JSON.stringify(errorResponse) + '\n');
    }
});

rl.on('close', () => {
    console.error('QuPath MCP Bridge stopped');
    process.exit(0);
});

function sendToHttp(request) {
    return new Promise((resolve, reject) => {
        const url = new URL(MCP_URL);
        const options = {
            hostname: url.hostname,
            port: url.port || 80,
            path: url.pathname,
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'Content-Length': Buffer.byteLength(request)
            }
        };

        const req = http.request(options, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                console.error('Response status:', res.statusCode);
                if (res.statusCode === 202) {
                    // Notification, no response
                    resolve(null);
                } else if (res.statusCode >= 200 && res.statusCode < 300) {
                    resolve(data);
                } else {
                    reject(new Error(`HTTP ${res.statusCode}: ${data}`));
                }
            });
        });

        req.on('error', (err) => {
            console.error('Request error:', err.message);
            reject(err);
        });

        req.write(request);
        req.end();
    });
}
