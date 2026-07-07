# QuPath-MCP v0.1 Tool Interface — Gap Analysis

Maps the [plan](../paper/plan.md)'s required v0.1 tools to what already exists in the extension
(20 tools) and defines what to build. Column meanings:

- **Have** — exists today, adequate as-is.
- **Rename/alias** — functionality exists under a different name; expose an alias or document the mapping.
- **Extend** — exists but needs additional output fields.
- **Build** — new tool required.

R/W = read-only vs write (write = potentially destructive; must be annotated and gated).

---

## 1. Project & image state

| Plan tool | Existing | Status | R/W | Notes |
|-----------|----------|--------|-----|-------|
| `get_project_info` | `qupath_get_project_info` | **Have** | R | project path, image list |
| `get_current_image_info` | `qupath_get_image_info` (+ `get_image_metadata`) | **Rename/alias** | R | name, dims, pixel size, server metadata |
| `get_viewer_state` | — | **Build** | R | current viewer region (x,y,w,h), downsample/zoom, displayed channels, overlay opts |
| `get_selection_info` | partial via `list_objects` | **Build** | R | selected object(s): id, type, class, ROI bounds, area, parent |

## 2. Annotation

| Plan tool | Existing | Status | R/W | Notes |
|-----------|----------|--------|-----|-------|
| `list_annotations` | `qupath_list_objects` (type=annotation) | **Rename/alias** | R | |
| `get_annotation_summary` | — | **Build** | R | counts by class, total area per class, # detections inside |
| `select_annotation` | — | **Build** | W* | selection change (non-destructive but stateful); by id/index/class |
| `export_annotations` | `qupath_export_geojson` | **Rename/alias** | R | |
| `create_rectangle_annotation` | `qupath_create_annotation` (type=rectangle) | **Have** | W | |
| `update_annotation_class` | — | **Build** | W | set classification of an object by id |
| `delete_annotation` | `qupath_delete_objects` | **Have** | W | |

\* `select_annotation` only changes selection state, not data — annotate as a light write.

## 3. Detection & measurement

| Plan tool | Existing | Status | R/W | Notes |
|-----------|----------|--------|-----|-------|
| `run_cell_detection` | `qupath_run_plugin` (Watershed) | **Build** (wrapper) | W | typed params (detectionImage, threshold, expansion…); returns counts before/after |
| `get_detection_summary` | — | **Build** | R | # detections, by class, parent annotation |
| `measure_annotations` | — | **Build** | W | add intensity/shape measurements to annotations/detections |
| `export_measurements` | partial (`run_script` only) | **Build** | R/W | export measurement table to CSV; return path + columns + row count |

## 4. Script execution

| Plan tool | Existing | Status | R/W | Notes |
|-----------|----------|--------|-----|-------|
| `validate_groovy_script` | — | **Build** | R | compile-check only (no execution); return ok/errors + line numbers |
| `run_groovy_script` | `qupath_run_script` | **Rename/alias** | W | already returns rich diagnostics; keep as advanced fallback |

## 5. Provenance & replay

| Plan tool | Existing | Status | R/W | Notes |
|-----------|----------|--------|-----|-------|
| `start_workflow_recording` | QuPath records history implicitly | **Build** | W | mark a start point / clear+timestamp; capture prompt + tool calls |
| `stop_workflow_recording` | — | **Build** | W | close the recording, attach metadata |
| `export_workflow_json` | partial (`get_workflow` → script) | **Build/Extend** | R | structured JSON: steps, params, generated Groovy, QuPath version, input project/image, outputs, status, errors |
| `replay_workflow` | partial (`save_workflow` + headless) | **Build** | W | run a saved workflow JSON/script in-process, no LLM; return replay log + status |

---

## Build list for v0.1 (priority order)

**Read-only first (cheap, high-value, safe to demo):**
1. `get_viewer_state` — viewer region, downsample, channels, overlay flags.
2. `get_selection_info` — selected-object summary.
3. `get_annotation_summary` — counts/areas by class.
4. `get_detection_summary` — detection counts by class/parent.
5. `validate_groovy_script` — compile-check via the Groovy engine without executing.

**Write / analysis:**
6. `run_cell_detection` — typed wrapper over WatershedCellDetection; before/after counts.
7. `measure_annotations` — add measurements.
8. `export_measurements` — measurement table → CSV (path, columns, rows).
9. `select_annotation`, `update_annotation_class` — selection + reclassification.

**Provenance/replay (the paper's differentiator):**
10. `export_workflow_json` — structured, metadata-rich provenance (supersedes/wraps `get_workflow`).
11. `replay_workflow` — deterministic in-process replay of a saved workflow, LLM-free.
12. `start_workflow_recording` / `stop_workflow_recording` — explicit session boundaries + prompt capture.

## Naming convention

Keep the existing `qupath_*` prefix. Where the plan uses a friendlier name (e.g.
`get_current_image_info`), either (a) add it as an alias tool, or (b) document the mapping in
`docs/tools.md`. Decision for v0.1: **document mappings** for names that already have a clear existing
tool; **build** genuinely new capabilities. Avoid duplicate tools that do the same thing.

## Read/write policy (for the paper's "controlled execution" claim)

- Every tool declares `readOnly` or `destructive` in its `ToolDefinition.Annotations`.
- Write tools are gated by config (`enableScripts`, `enablePlugins`, `enableFileAccess`,
  `enableCaptureView`) where applicable, plus a future `confirm`/dry-run affordance for destructive ops.
- Provenance records every write with parameters, so any state change is auditable and replayable.
