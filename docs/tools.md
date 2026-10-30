# QuPath-MCP Tool Reference

QuPath-MCP exposes **34 tools** (all prefixed `qupath_`). Every tool declares a machine-readable hint —
**read-only**, **write** (changes state but not destructive), or **destructive** — so a client can reason
about side effects before calling. Full JSON input schemas are self-documenting via the MCP `tools/list`
method; this page summarizes purpose, category, side-effect class, and gating.

## Categories at a glance

### Project & image state (read-only)

| Tool | Purpose |
|------|---------|
| `qupath_get_project_info` | Active project path and image list |
| `qupath_list_images` | Images in the current project |
| `qupath_get_image_info` | Current image: name, dimensions, pixel size, channels |
| `qupath_get_image_metadata` | Full server metadata for the current image |
| `qupath_get_viewer_state` | Displayed region, downsample/magnification, z/t, shown channels, overlay flags |

### Project actions

| Tool | Side effect | Gated by | Purpose |
|------|-------------|----------|---------|
| `qupath_open_image` | write | — | Open a project image in the viewer by name or index |
| `qupath_save_data` | destructive¹ | `enableFileAccess` | Persist the current image data (objects) into the project |

### Viewer perception

| Tool | Side effect | Purpose |
|------|-------------|---------|
| `qupath_read_region` | read-only | Pixel data / statistics for a region |
| `qupath_capture_view` | read-only | The rendered viewer as an image block (opt-in; see [safety](safety.md)) |

### Objects & annotations

| Tool | Side effect | Purpose |
|------|-------------|---------|
| `qupath_list_objects` | read-only | List annotations/detections (type + class filters, paging) |
| `qupath_get_object_details` | read-only | Full detail for one object by id |
| `qupath_get_selection_info` | read-only | The current selection: ids, types, classes, ROIs |
| `qupath_get_annotation_summary` | read-only | Annotation counts + area by classification |
| `qupath_get_detection_summary` | read-only | Detection/cell counts by classification |
| `qupath_select_annotation` | write | Select annotation(s) by id/class/index/all |
| `qupath_create_annotation` | destructive | Create a rectangle/ellipse/line/polygon/points annotation |
| `qupath_update_annotation_class` | write | Set/clear classification by id or on selection |
| `qupath_delete_objects` | destructive | Delete objects |
| `qupath_import_geojson` | destructive | Import objects from GeoJSON |
| `qupath_export_geojson` | read-only | Export objects to GeoJSON |

### Detection & measurement

| Tool | Side effect | Gated by | Purpose |
|------|-------------|----------|---------|
| `qupath_run_cell_detection` | destructive | `enablePlugins` | Watershed cell detection on selected annotation(s); reports count before/after |
| `qupath_measure_annotations` | write | — | Add shape measurements (area, perimeter, circularity, …) |
| `qupath_export_measurements` | destructive¹ | `enableFileAccess` | Export the measurement table to CSV |

### Scripting

| Tool | Side effect | Gated by | Purpose |
|------|-------------|----------|---------|
| `qupath_validate_groovy_script` | read-only | — | Compile-check a script without running it |
| `qupath_run_script` | destructive | `enableScripts` | Execute Groovy (advanced fallback; rich error/log diagnostics) |
| `qupath_list_scripts` | read-only | — | List scripts in the project scripts directory |

### Plugins & provenance

| Tool | Side effect | Gated by | Purpose |
|------|-------------|----------|---------|
| `qupath_list_plugins` | read-only | — | List available plugins |
| `qupath_run_plugin` | destructive | `enablePlugins` | Run an arbitrary plugin by class name |
| `qupath_get_workflow` | read-only | — | Recorded history as steps + a runnable Groovy script |
| `qupath_export_workflow_json` | read-only | (`enableFileAccess` if writing) | Structured provenance: version, image, per-step params/script |
| `qupath_save_workflow` | destructive¹ | `enableFileAccess` | Save the workflow as a dated `.groovy` in a `qupath_workflow/` folder |
| `qupath_replay_workflow` | destructive | `enableScripts` | Deterministic, LLM-free replay of a saved workflow |
| `qupath_start_workflow_recording` | write | — | Mark a recording start (optional clear) |
| `qupath_stop_workflow_recording` | read-only | — | Report steps recorded + runnable script |

¹ *Writes a file to disk; not destructive to QuPath data.*

## Mapping to the plan's tool names

The paper plan uses friendlier names; QuPath-MCP keeps the stable `qupath_*` names and maps them:

| Plan name | QuPath-MCP tool |
|-----------|-----------------|
| `get_current_image_info` | `qupath_get_image_info` (+ `qupath_get_image_metadata`) |
| `list_annotations` | `qupath_list_objects` (`type: "annotation"`) |
| `export_annotations` | `qupath_export_geojson` |
| `create_rectangle_annotation` | `qupath_create_annotation` (`type: "rectangle"`) |
| `delete_annotation` | `qupath_delete_objects` |
| `run_groovy_script` | `qupath_run_script` |
| everything else | same name with the `qupath_` prefix |

## Representative tool details

### `qupath_run_cell_detection` *(destructive, needs `enablePlugins`)*
Runs `WatershedCellDetection` on the selected annotation(s) — or all annotations if none are selected.

- **Input:** `detectionImage` (channel, e.g. `"DAPI"`), `threshold`, `requestedPixelSizeMicrons`,
  `backgroundRadiusMicrons`, `medianRadiusMicrons`, `sigmaMicrons`, `minAreaMicrons`, `maxAreaMicrons`,
  `cellExpansionMicrons`, `includeNuclei`, `makeMeasurements` — all optional with documented defaults.
- **Output:** `{ success, detectionsBefore, detectionsAfter, added, parameters, warning? }`
- **Failure:** no image; no annotation to detect within; plugins disabled.
- **Example:** `{"name":"qupath_run_cell_detection","arguments":{"detectionImage":"DAPI","threshold":1.0}}`
  → `{"success":true,"detectionsBefore":0,"detectionsAfter":5266,"added":5266,...}`

### `qupath_export_measurements` *(writes a file, needs `enableFileAccess`)*
- **Input:** `objectType` (`detection`|`cell`|`annotation`), `path?`, `overwrite?`.
- **Output:** `{ saved, objectType, rows, columns[] }`
- **Example:** `{"objectType":"cell"}` → `{"saved":".../measurements_cell_<ts>.csv","rows":5266,"columns":[...134...]}`

### `qupath_export_workflow_json` *(read-only; writes if `path` given)*
- **Output:** `{ qupathVersion, image, imageType, stepCount, steps:[{name, parameters?, script?}], script }`

### `qupath_replay_workflow` *(destructive, needs `enableScripts`)*
Runs a saved workflow with **no LLM**. Provide `path` (`.groovy` or `.json`) or inline `script`.
- **Output:** `{ status, output, annotationsBefore/After, detectionsBefore/After, error? }`

### `qupath_run_script` *(destructive, needs `enableScripts`)*
On failure returns `error`, `errorType`, `causeChain`, `rootCause`, `stackTrace`. Operations that swallow
their own exceptions (notably `runPlugin`) surface the underlying error in a `logs` field with a
`warning`, so `success:true` alone is not proof an operation worked — check `logs`.

See [safety.md](safety.md) for the configuration flags that gate write/destructive tools, and
[workflow_replay.md](workflow_replay.md) for the provenance model.
