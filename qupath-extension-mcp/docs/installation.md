# Installation

QuPath-MCP is a QuPath extension: the MCP server runs **inside** QuPath's JVM and calls the QuPath API
directly (no external bridge process).

## Requirements

- **QuPath 0.8.x** (built against `0.8.0-SNAPSHOT` host APIs). The embedded HTTP server uses only
  `java.base` (`java.net.ServerSocket`), so it runs in any QuPath runtime — no extra JDK modules needed.
- An MCP-capable client (Claude Code / Claude Desktop / Codex / Cursor …) — see [quick_start.md](quick_start.md).

## Option A — build & run from source (this fork)

The extension is bundled in this QuPath fork, so a normal build includes it:

```bash
QUPATH_MCP_TRANSPORT=HTTP QUPATH_MCP_PORT=9527 QUPATH_MCP_AUTO_START=true ./gradlew run
```

The server auto-starts on launch; an **Extensions ▸ MCP Server** menu lets you Start/Stop, view Status,
toggle **Enable View Capture**, and see the Configuration.

## Option B — install the JAR into an existing QuPath

```bash
./gradlew :qupath-extension-mcp:jar
# -> qupath-extension-mcp/build/libs/qupath-extension-mcp-<version>.jar
```

Drag the JAR onto the QuPath window (or copy it into the extensions directory shown under
**Extensions ▸ Installed extensions**), then **fully quit and restart** QuPath. Keep only one copy of the
JAR in the extensions directory. Requires a QuPath 0.8.x build that provides the host APIs the extension
uses.

> If you replace the JAR while QuPath is running, the old classes stay loaded until you fully restart the
> app — a restart is required for changes to take effect.

## Verify

```bash
curl http://127.0.0.1:9527/health          # -> {"status":"ok"}
./test-mcp-server.sh                        # end-to-end smoke test (curl only)
./gradlew :qupath-extension-mcp:test        # unit tests (no running QuPath needed)
```
