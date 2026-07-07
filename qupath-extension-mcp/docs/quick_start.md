# Quick Start

## 1. Launch QuPath with the server

```bash
QUPATH_MCP_TRANSPORT=HTTP QUPATH_MCP_PORT=9527 QUPATH_MCP_AUTO_START=true ./gradlew run
```

Open an image (e.g. the `LuCa-7color` multiplex sample). The MCP endpoint is
`http://127.0.0.1:9527/mcp`.

## 2. Connect a client

**Claude Code** (native HTTP) — add to `.mcp.json`:

```json
{ "mcpServers": { "qupath": { "type": "http", "url": "http://127.0.0.1:9527/mcp" } } }
```

**Claude Desktop / Codex** (stdio bridge):

```json
{ "mcpServers": { "qupath": { "command": "npx", "args": ["-y", "mcp-remote", "http://127.0.0.1:9527/mcp"] } } }
```

## 3. Ask the agent to work state-first

QuPath-MCP is designed so an agent *inspects state before acting*. Good opening prompts:

- *"Summarize the current image, annotation hierarchy, selection, and available measurement columns."*
  → `get_image_info`, `get_viewer_state`, `get_selection_info`, `get_annotation_summary`
- *"Run cell detection in the selected tumor annotation on DAPI and export the measurements as CSV."*
  → `get_selection_info` → `run_cell_detection` → `get_detection_summary` → `export_measurements`
- *"Record this workflow and save it so I can replay it later."*
  → `start_workflow_recording` → … → `stop_workflow_recording` → `save_workflow` / `export_workflow_json`
- *"Replay the workflow I saved earlier."* → `replay_workflow` (no LLM needed for the replay itself)

## 4. Manual sanity check (no agent)

```bash
curl -s -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"qupath_get_viewer_state","arguments":{}}}' \
  http://127.0.0.1:9527/mcp
```

See [tools.md](tools.md) for the full tool list and [examples.md](examples.md) for complete workflows.
