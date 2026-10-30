# Installation

QuPath-MCP is a QuPath extension: the MCP server runs **inside** QuPath's JVM and calls the QuPath API
directly (no external bridge process).

## Requirements

- **QuPath 0.7.0** (the official release). The embedded HTTP server uses only `java.base`
  (`java.net.ServerSocket`), so it runs on QuPath's bundled runtime — no extra JDK modules needed.
- An MCP-capable client (Claude Code / Claude Desktop / Codex / Cursor …) — see [quick_start.md](quick_start.md).

## Option A — install the released JAR (recommended)

1. Download `qupath-extension-mcp-<version>.jar` from the
   [latest release](https://github.com/00JackLu/qupath-mcp/releases/latest).
2. Drag the JAR onto the QuPath window (or copy it into the extensions directory shown under
   **Extensions ▸ Manage extensions**), then restart QuPath.
3. The server auto-starts; an **Extensions ▸ MCP Server** menu lets you Start/Stop, view Status, toggle
   **Enable View Capture**, and see the Configuration.

Keep only one copy of the JAR in the extensions directory.

> If you replace the JAR while QuPath is running, the old classes stay loaded until you fully restart the
> app — a restart is required for changes to take effect.

## Option B — build the JAR from source

```bash
git clone https://github.com/00JackLu/qupath-mcp.git
cd qupath-mcp
./gradlew build
# -> build/libs/qupath-extension-mcp-<version>.jar
```

Then install it as in Option A. The build needs JDK 25; the Gradle wrapper downloads it if missing.

## Verify

```bash
curl http://127.0.0.1:9527/health          # -> {"status":"ok"}
./test-mcp-server.sh                        # end-to-end smoke test (curl only)
./gradlew test                              # unit tests (no running QuPath needed)
```
