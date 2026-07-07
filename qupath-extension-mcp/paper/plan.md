# QuPath-MCP Paper Workflow: From Experiments to Bioinformatics Application Note

> **Status tracker.** This document is the master plan. Progress is tracked in the session task
> list (Phases 0–8) and summarized in the [Progress](#progress) table below. Last updated
> 2026-07-07.

## Goal

Prepare **QuPath-MCP** as a publishable **Bioinformatics Application Note**.

**Positioning (use this framing everywhere — code comments, docs, examples, paper):**

> QuPath-MCP is a **state-aware, model-agnostic interface** that enables AI agents to **inspect,
> execute, verify, and reproduce** digital pathology workflows in QuPath.

Do **not** position this as "an MCP server for QuPath." Emphasize: state-aware access, structured
AI-agent tools, controlled execution, workflow provenance, deterministic replay, reproducibility.

### Key message to preserve

> QuPath-MCP turns QuPath from a GUI/script-driven pathology platform into a **state-aware,
> agent-accessible, and reproducible workflow execution environment** for digital pathology.

---

## Context & architecture reality (adaptation note)

The generic plan assumed a **Python/Node MCP server** bridging to QuPath (`pyproject.toml`,
`qupath_mcp/`, `qupath_bridge/`). **This project's reality is different and better:** the MCP server
is a **Java QuPath extension** (`qupath-extension-mcp`) that runs *inside* QuPath's own JVM and calls
the QuPath API directly — no out-of-process bridge, no IPC, no version-skew between a bridge and the
app. We therefore adapt the plan:

- **No separate bridge layer.** "qupath_bridge" is not needed — tools call the QuPath API in-process.
- **Repo layout** follows the Java/Gradle module (`qupath-extension-mcp/`), not a Python package.
  Research-release materials (`docs/`, `examples/`, `benchmark/`, `paper/`) live under that module so
  it is self-contained and could be split into a standalone `qupath-mcp` repo later (Phase 2 decision).
- **Transport** is Streamable HTTP on `127.0.0.1`, implemented on `java.net.ServerSocket` (works in any
  QuPath runtime; no `jdk.httpserver` dependency).
- **Provenance** builds on QuPath's own recorded `Workflow` (history) plus the extension's
  `get_workflow` / `save_workflow` tools.

This is a *strength* for the paper: in-process, state-aware access is exactly the "grounding" argument.

---

## Current status snapshot (as of 2026-07-07)

**Implemented (20 tools), transport, and safety already exist:**

| Group | Existing tools |
|-------|----------------|
| Image | `get_image_info`, `get_image_metadata`, `read_region`, `capture_view` |
| Objects | `list_objects`, `get_object_details`, `create_annotation`, `delete_objects`, `import_geojson`, `export_geojson` |
| Scripts | `run_script` *(Groovy)*, `list_scripts` |
| Project | `get_project_info`, `list_images`, `open_image`, `save_data` |
| Plugins/Workflow | `list_plugins`, `run_plugin`, `get_workflow`, `save_workflow` |

Already in place that the paper needs:
- **Controlled execution**: per-feature kill switches (`enableScripts`, `enablePlugins`,
  `enableFileAccess`, `enableCaptureView`), localhost-only bind, destructive-tool annotations.
- **Diagnostics/verification**: `run_script` returns `errorType` / `causeChain` / `rootCause` /
  `stackTrace`, and surfaces silently-swallowed failures (e.g. `runPlugin`) via `logs` + `warning`.
- **Provenance foundation**: `get_workflow` emits a runnable Groovy script from QuPath's history;
  `save_workflow` persists it (dated file in a project `qupath_workflow/` folder).
- **Vision**: `capture_view` returns a size-bounded image so an agent can *see* the viewer.

**The gap to close for v0.1** (detailed in [`../docs/tool_interface_v0.1.md`](../docs/tool_interface_v0.1.md)):
dedicated state tools (`get_viewer_state`, `get_selection_info`), a selection/annotation-summary
surface, first-class detection/measurement tools (`get_detection_summary`, `measure_annotations`,
`export_measurements`), `validate_groovy_script`, and explicit provenance/replay
(`start/stop_workflow_recording`, `export_workflow_json`, `replay_workflow`).

---

## Progress

| Phase | Description | State |
|-------|-------------|-------|
| 0 | Plan doc + tool-interface gap analysis | complete |
| 1 | Freeze the v0.1 tool interface | complete |
| 2 | Repository cleanup + docs | complete |
| 3 | Three demonstration workflows | complete |
| 4 | Small benchmark (20–30 task cards) | cards complete; live runs pending |
| 5 | Baselines (optional) | pending |
| 6 | Figures + tables | Fig 1/2 + Table 1 done; Table 2 pending live runs |
| 7 | Application Note draft | pending |
| 8 | Submission-readiness checklist | pending |

---

## Phase 1: Freeze the v0.1 tool interface

**Objective:** a minimal but complete tool set proving QuPath-MCP is *not* a thin `run_script` wrapper.

### 1. Project and image state tools
`get_project_info`, `get_current_image_info`, `get_viewer_state`, `get_selection_info` — output must
include project path, current image name, image dimensions, pixel size, server metadata, current
viewer region, selected-object summary.

### 2. Annotation tools
Required: `list_annotations`, `get_annotation_summary`, `select_annotation`, `export_annotations`.
Optional: `create_rectangle_annotation`, `update_annotation_class`, `delete_annotation`.
Destructive tools must be marked as write operations and require explicit confirmation.

### 3. Detection and measurement tools
`run_cell_detection`, `get_detection_summary`, `measure_annotations`, `export_measurements` — output
must include detection counts before/after, measurement columns, exported file path, execution status.

### 4. Script execution tools
`validate_groovy_script`, `run_groovy_script`. `run_groovy_script` is an **advanced fallback**, not the
headline tool.

### 5. Provenance and replay tools
`start_workflow_recording`, `stop_workflow_recording`, `export_workflow_json`, `replay_workflow`. The
workflow log must include user prompt, tool calls + parameters, generated Groovy, QuPath version, input
project/image, output files, execution status, and errors.

---

## Phase 2: Repository cleanup

Make it look like a real research-software release. Target structure (adapted to the Java module):

```text
qupath-extension-mcp/
  README.md
  docs/{installation,quick_start,tools,safety,workflow_replay,examples}.md
  docs/tools/<per-tool>.md
  examples/{annotation_summary,cell_detection_export,workflow_replay}/
  benchmark/{task_cards,results,scripts}/
  paper/{plan.md,outline.md,draft.md,figures/,tables/}
  src/main/java/qupath/lib/extension/mcp/{transport,protocol,tools,provenance,...}
```

**README must contain:** what/why, installation, quick start, supported QuPath versions, supported MCP
clients, tool list, example workflows, safety model, workflow recording/replay, citation placeholder.

**Per-tool docs:** name, purpose, input schema, output schema, read-only/write, failure cases, example
call, example response.

---

## Phase 3: Three demonstration workflows

Each includes input project, prompt, tool-call trace, output files, screenshots, expected result,
replay result.

1. **State inspection + annotation summary** — agent understands current state before acting; no writes.
2. **Cell detection + measurement export** — real analysis; detection count increases, CSV exported and
   parseable, no modification outside the selected annotation.
3. **Workflow recording + replay** — record → export JSON → replay without an LLM → equivalent outputs.

(Full prompts/expected calls/success criteria are carried verbatim from the source plan.)

---

## Phase 4: Small benchmark (20–30 tasks)

| Category | Tasks |
|---|---:|
| State inspection | 5 |
| Annotation operations | 5–8 |
| Cell detection | 5 |
| Measurement export | 5 |
| Workflow replay | 3–5 |
| Batch/project workflow | 2–3 |

Task-card markdown format: Input, User prompt, Expected behavior, Required tools, Success criteria,
Failure criteria. Per-task logging: `task_id, task_category, model, mcp_client,
success_or_failure, number_of_tool_calls, execution_error, invalid_operation, output_file_exists,
replay_success, notes`. Emit results as CSV + markdown table.

---

## Phase 5: Baselines (optional)

- **A. Direct LLM Groovy scripting** — agent writes a script, run directly. Measure execution success,
  correctness, manual correction, replayability.
- **B. MCP run-script-only** — expose only `run_groovy_script`; remove structured state/annotation tools.
  Measure success, error rate, correction turns, replayability.

Comparison table: Direct LLM Groovy (replayable: No) vs run-script-only (Partial) vs structured tools
(Yes). Optional for the Application Note; useful for ISMB/ISBI.

---

## Phase 6: Figures and tables

- **Figure 1 — system overview:** prompt → MCP agent → QuPath-MCP server → state/annotation/analysis/
  measurement/provenance tools → QuPath project/viewer/annotations/detections/measurements → workflow
  log + replay.
- **Figure 2 — example workflow:** prompt → state inspection → selected tumor annotation → cell
  detection → measurement export → recording + replay, with QuPath screenshots (before/after/table/JSON).
- **Table 1 — tool categories** (project state / viewer state / annotation / detection / measurement /
  script execution / provenance).
- **Table 2 — benchmark results** (per category: tasks, success rate, replay success, main failure mode).

---

## Phase 7: Bioinformatics Application Note

**Preferred title:** *QuPath-MCP: a state-aware interface for reproducible AI-agent workflows in digital
pathology.*

**Skeleton:** Abstract (Motivation / Results / Availability), Introduction, System Design (state-aware
grounding, structured tool interface, controlled execution, provenance & replay), Implementation
(versions, language, transport, OS/clients, install, docs, data, license, repo + archived release),
Use Cases and Validation (three workflows + Table 2), Discussion/Conclusion. Draft lives in
`paper/draft.md`, outline in `paper/outline.md`.

---

## Phase 8: Submission readiness checklist

```text
[ ] GitHub repo public/accessible   [ ] License included         [ ] README quick start
[ ] Docs for all tools              [ ] Example data available   [ ] >=3 reproducible workflows
[ ] Benchmark task cards included   [ ] Benchmark results CSV     [ ] Figure 1 complete
[ ] Figure 2 complete               [ ] Tool table complete       [ ] Release archived (Zenodo)
[ ] Availability & Implementation   [ ] New-user install works    [ ] Clearly > a run_script wrapper
```

---

## Final deliverables

1. Cleaned QuPath-MCP repository/module
2. README and documentation
3. v0.1 tool list
4. Three reproducible examples
5. Benchmark task cards
6. Benchmark result CSV
7. Figure 1 system diagram draft
8. Figure 2 workflow diagram draft
9. Table 1 tool category table
10. Table 2 benchmark result table
11. Application Note outline
12. First paper draft in `paper/draft.md`
