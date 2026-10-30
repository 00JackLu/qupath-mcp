# QuPath MCP Extension

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.23788877.svg)](https://doi.org/10.5281/zenodo.23788877)

Turns [QuPath](https://qupath.github.io/) into a [Model Context Protocol](https://modelcontextprotocol.io)
server, so AI agents (Claude Code, Claude Desktop, Codex, Cursor, …) can drive QuPath: read image data,
manage annotations/detections, import/export GeoJSON, run Groovy scripts, and invoke plugins.

The extension embeds an HTTP MCP server (Streamable HTTP, MCP spec `2025-03-26`) inside QuPath. Point any
MCP client at `http://127.0.0.1:9527/mcp` and you get **34 tools**.

This module is bundled directly into this QuPath fork (registered in `settings.gradle.kts` and depended on
by `qupath-app`), so a normal build of the app already includes it — no separate install step needed.

---

## 1. Build & run from source

From the repository root:

```bash
# Build & launch QuPath with the MCP HTTP server auto-starting on port 9527
QUPATH_MCP_TRANSPORT=HTTP QUPATH_MCP_PORT=9527 QUPATH_MCP_AUTO_START=true ./gradlew run
```

The server auto-starts on launch. Inside QuPath you also get an **Extensions ▸ MCP Server** menu to
*Start / Stop*, view *Status*, and see the *Configuration*.

To build just the extension JAR (e.g. to drop into another QuPath install):

```bash
./gradlew :qupath-extension-mcp:build
# JAR: qupath-extension-mcp/build/libs/qupath-extension-mcp-<version>.jar
```

To install a prebuilt JAR into a stock QuPath: drag it onto the main QuPath window (or copy it into the
QuPath *extensions* directory shown under `Extensions ▸ Installed extensions`), then restart when prompted.
This requires a QuPath 0.8.x build that provides the host APIs this extension uses.

The embedded HTTP server is built on `java.net.ServerSocket` (part of `java.base`), so it needs no extra
JDK modules — it runs on the trimmed runtime that ships with a stock QuPath install. (An earlier version
used `com.sun.net.httpserver`, whose `jdk.httpserver` module is *not* bundled in QuPath's jlink runtime,
which caused `NoClassDefFoundError` on start — that's fixed.) If you replace the JAR in a running QuPath,
fully **quit and restart** the app so the new classes are loaded, and keep only one copy of the extension
JAR in the extensions directory.

## 2. Test that it works

### Smoke test (running server, end to end)

With QuPath running (step 1), run the bundled script from the repo root:

```bash
./test-mcp-server.sh
```

It exercises the live server using only `curl` and prints PASS/FAIL per check
(exit code 0 = all passed):

| # | Request | Expected |
|---|---------|----------|
| 0 | reachability | connects to `:9527`, else prints a launch hint |
| 1 | `GET /health` | `{"status":"ok"}` |
| 2 | `POST /mcp` `initialize` | `protocolVersion` + `serverInfo` |
| 3 | `POST /mcp` `ping` | a `result` |
| 4 | `POST /mcp` `tools/list` | non-empty list of `qupath_*` tools (34) |
| 5 | `POST /mcp` `tools/call` (`qupath_list_objects`) | a well-formed JSON-RPC response |

Override the target with env vars: `HOST=127.0.0.1 PORT=9527 ./test-mcp-server.sh`.

A minimal manual check:

```bash
curl http://127.0.0.1:9527/health
# -> {"status":"ok"}

curl -s -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' \
  http://127.0.0.1:9527/mcp
# -> {"jsonrpc":"2.0","id":1,"result":{"tools":[ ... 34 tools ... ]}}
```

### Unit tests (no running QuPath needed)

```bash
./gradlew :qupath-extension-mcp:test
```

Covers the JSON-RPC protocol handler (`TestMcpProtocolHandler`) and the HTTP transport
(`TestHttpTransport`).

---

## 3. Connect an agent

The server speaks **Streamable HTTP**. Clients that support HTTP MCP servers connect directly; stdio-only
clients use a tiny bridge (`mcp-remote`).

### Claude Code *(native HTTP — easiest)*

Add to a project's `.mcp.json` (or run `claude mcp add`):

```json
{
  "mcpServers": {
    "qupath": { "type": "http", "url": "http://127.0.0.1:9527/mcp" }
  }
}
```

Then `claude mcp list` should show `qupath: … ✔ Connected` (with QuPath running).

### Claude Desktop *(stdio bridge)*

`claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "qupath": { "command": "npx", "args": ["-y", "mcp-remote", "http://127.0.0.1:9527/mcp"] }
  }
}
```

### Codex CLI

`~/.codex/config.toml` — via the stdio bridge (works on all versions):

```toml
[mcp_servers.qupath]
command = "npx"
args = ["-y", "mcp-remote", "http://127.0.0.1:9527/mcp"]
```

Recent Codex builds can also connect to the HTTP endpoint directly:

```toml
[mcp_servers.qupath]
url = "http://127.0.0.1:9527/mcp"
experimental_use_rmcp_client = true
```

### Cursor / other HTTP-capable clients

Use the same `{ "type": "http", "url": "http://127.0.0.1:9527/mcp" }` form, or the `mcp-remote` bridge if
the client is stdio-only.

---

## 4. Tools (34)

| Group   | Tools |
|---------|-------|
| Image / viewer | `qupath_get_image_info`, `qupath_get_image_metadata`, `qupath_read_region`, `qupath_capture_view`, `qupath_get_viewer_state` |
| Objects | `qupath_list_objects`, `qupath_get_object_details`, `qupath_get_selection_info`, `qupath_get_annotation_summary`, `qupath_get_detection_summary`, `qupath_select_annotation`, `qupath_create_annotation`, `qupath_update_annotation_class`, `qupath_delete_objects`, `qupath_import_geojson`, `qupath_export_geojson` |
| Detection / measurement | `qupath_run_cell_detection`, `qupath_measure_annotations`, `qupath_export_measurements` |
| Scripts | `qupath_validate_groovy_script`, `qupath_run_script` *(Groovy)*, `qupath_list_scripts` |
| Project | `qupath_get_project_info`, `qupath_list_images`, `qupath_open_image`, `qupath_save_data` |
| Plugins / provenance | `qupath_list_plugins`, `qupath_run_plugin`, `qupath_get_workflow`, `qupath_save_workflow`, `qupath_export_workflow_json`, `qupath_replay_workflow`, `qupath_start_workflow_recording`, `qupath_stop_workflow_recording` |

Full reference (purpose, side-effect class, gating): [`docs/tools.md`](docs/tools.md). Other docs:
[installation](docs/installation.md) · [quick start](docs/quick_start.md) · [safety](docs/safety.md) ·
[workflow replay](docs/workflow_replay.md) · [examples](docs/examples.md) · [paper plan](paper/plan.md).

`qupath_run_script` executes Groovy in QuPath's context (full `QPEx` API). It's annotated *destructive* and
can be disabled — see below. On failure it returns rich diagnostics — `errorType`, `causeChain`,
`rootCause` and a full `stackTrace`. Note that some QuPath calls (notably `runPlugin`) catch their own
exceptions and return `false` instead of throwing; when that happens the script still reports
`success: true`, but the underlying error is surfaced in a `logs` field with a `warning`, so an agent
should check those to confirm an operation actually did what it expected.

`qupath_capture_view` returns what QuPath is currently displaying (the rendered slide plus
annotation/detection overlays at the current zoom/pan) as an MCP **image** content block, so a
vision-capable agent can *see* the view and reason over it — not just read pixel numbers. The payload is
size-bounded so it can't overflow the model's context: it defaults to JPEG at `maxDimension` 1024 px and
enforces a `maxBytes` budget (default 350 KB), progressively lowering quality then downscaling until it
fits. Pass `format: "png"` with `maxBytes: 0` for a lossless full-resolution capture instead.

`qupath_get_workflow` + `qupath_save_workflow` turn a successful analysis into something reproducible: the
former returns the recorded history as a runnable Groovy `script`, the latter writes it (or an
agent-curated script passed as `content`) to a file on the local machine — so a user can just ask the
agent to *"save this workflow"* and later re-run it, including headless via
`qupath script <file> -p project.qpproj`. By default the file is named `<name>_<timestamp>.groovy` and
saved into a **`qupath_workflow/` folder inside the current project** (or your home directory when no
project is open); pass a `path` to choose a different directory, or a path ending in `.groovy` for an exact
file name. Saving requires file access to be enabled. Note only QuPath-recorded steps (e.g. `runPlugin`,
classifiers) are captured automatically — custom logic run through `qupath_run_script` is not, so for those
the agent should assemble the script and pass it as `content`.

Because it streams on-screen pixels to the agent, `qupath_capture_view` is **disabled by default**. Turn it
on with **Extensions ▸ MCP Server ▸ Enable View Capture** (a checkbox, off by default) or
`QUPATH_MCP_ENABLE_CAPTURE_VIEW=true`. While disabled, calling it returns a clear "view capture is disabled"
error.

See [`PROMPTS.md`](PROMPTS.md) for a cookbook of natural-language prompts that drive these tools.

## 5. Configuration

Resolved in order (later overrides earlier): a properties file → environment variables → system properties
→ built-in defaults. Set env vars / `-D` properties before launching QuPath.

| Env var | System property | Default | Meaning |
|---------|-----------------|---------|---------|
| `QUPATH_MCP_TRANSPORT` | `qupath.mcp.transport` | `HTTP` | `HTTP`, `STDIO`, or `BOTH` |
| `QUPATH_MCP_HOST` | `qupath.mcp.host` | `127.0.0.1` | Bind address |
| `QUPATH_MCP_PORT` | `qupath.mcp.port` | `9527` | HTTP port |
| `QUPATH_MCP_AUTO_START` | `qupath.mcp.autoStart` | `true` | Start server on launch |
| `QUPATH_MCP_MAX_THREADS` | `qupath.mcp.maxThreads` | `4` | HTTP handler thread pool size |
| `QUPATH_MCP_REQUEST_TIMEOUT` | `qupath.mcp.requestTimeout` | `60000` | Request timeout (ms) |
| `QUPATH_MCP_ENABLE_SCRIPTS` | `qupath.mcp.enableScripts` | `true` | Allow `qupath_run_script` |
| `QUPATH_MCP_ENABLE_FILE_ACCESS` | `qupath.mcp.enableFileAccess` | `true` | Allow file read/write tools |
| `QUPATH_MCP_ENABLE_PLUGINS` | `qupath.mcp.enablePlugins` | `true` | Allow `qupath_run_plugin` |
| `QUPATH_MCP_ENABLE_CAPTURE_VIEW` | `qupath.mcp.enableCaptureView` | `false` | Allow `qupath_capture_view` (also toggled from the menu) |

Or a properties file (first match wins): `~/.qupath/mcp.properties`, `~/qupath-mcp.properties`, or
`./qupath-mcp.properties`. Use keys **without** the prefix, e.g.:

```properties
port=8080
transport=HTTP
enableScripts=false
```

## 6. Security note

The server binds to `127.0.0.1` (localhost) only and enables Groovy script execution by default — an agent
can run arbitrary code in QuPath. Set `QUPATH_MCP_ENABLE_SCRIPTS=false` (and `QUPATH_MCP_ENABLE_PLUGINS` /
`QUPATH_MCP_ENABLE_FILE_ACCESS` as needed) to lock that down, and don't expose the port beyond localhost.

## How to cite

If you use QuPath-MCP in published work, please cite the archived release and QuPath itself:

> Lu, Z. (2026). *QuPath-MCP: a state-aware interface for reproducible AI-agent workflows in digital
> pathology* (Version 0.1.0) [Computer software]. Zenodo. https://doi.org/10.5281/zenodo.23788878

> Bankhead, P. *et al.* (2017). QuPath: Open source software for digital pathology image analysis.
> *Scientific Reports* 7, 16878. https://doi.org/10.1038/s41598-017-17204-5

The DOI above pins v0.1.0; [10.5281/zenodo.23788877](https://doi.org/10.5281/zenodo.23788877) always resolves to the latest release. BibTeX
and other formats are available from GitHub's **Cite this repository** button (from `CITATION.cff`).
