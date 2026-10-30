# Safety & Controlled Execution

QuPath-MCP gives an AI agent real control over QuPath and, through Groovy, the host machine. It is
designed for **local, single-user, trusted-agent** use. This page describes the controls.

## Side-effect hints

Every tool declares a hint that a client can inspect before calling:

- **read-only** — inspects state; never modifies data (e.g. `get_viewer_state`, `get_annotation_summary`).
- **write** — changes QuPath state but is not destructive (e.g. `select_annotation`,
  `update_annotation_class`, `measure_annotations`).
- **destructive** — can modify/delete data, run arbitrary code, or write files (e.g. `run_script`,
  `run_plugin`, `run_cell_detection`, `delete_objects`, `replay_workflow`, `save_workflow`).

## Feature kill-switches

Set before launching QuPath (env var, `-D` property, or a properties file). Defaults in **bold**.

| Env var | Default | Gates |
|---------|---------|-------|
| `QUPATH_MCP_ENABLE_SCRIPTS` | **true** | `run_script`, `replay_workflow` |
| `QUPATH_MCP_ENABLE_PLUGINS` | **true** | `run_plugin`, `run_cell_detection` |
| `QUPATH_MCP_ENABLE_FILE_ACCESS` | **true** | `export_measurements`, `save_workflow`, `export_workflow_json` (file write), workflow read for replay |
| `QUPATH_MCP_ENABLE_CAPTURE_VIEW` | **false** | `qupath_capture_view` (sends on-screen pixels to the agent) |

Lock-down example (`~/.qupath/mcp.properties`):

```properties
enableScripts=false
enablePlugins=false
enableFileAccess=false
```

View capture is **off by default** and also togglable at runtime via
**Extensions ▸ MCP Server ▸ Enable View Capture**.

## Network exposure

The server binds to `127.0.0.1` (localhost) only. There is **no authentication** — anything that can
reach the port can call every enabled tool. Do **not** forward or tunnel the port. Stop the server
(**Extensions ▸ MCP Server ▸ Stop**) when not in use.

## Auditability

Because provenance records every executed step with its parameters
([workflow_replay.md](workflow_replay.md)), state changes made through the server are inspectable after
the fact and reproducible independently of any model.
