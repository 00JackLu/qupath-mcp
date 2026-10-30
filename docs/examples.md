# Example Workflows

Three end-to-end workflows demonstrating inspect → execute → verify → reproduce. All traces below were
run against the `LuCa-7color` multiplex fluorescence sample. Full artifacts live under
[`../examples/`](../examples/).

## Workflow 1 — State inspection & annotation summary (no writes)

**Prompt:** *"Summarize the current QuPath image, annotation hierarchy, selected objects, and available
measurement columns."*

**Tool calls (all read-only):** `qupath_get_image_info` → `qupath_get_viewer_state` →
`qupath_get_selection_info` → `qupath_get_annotation_summary` → `qupath_get_detection_summary`.

**Result (excerpt):**
```json
// get_annotation_summary
{ "totalAnnotations": 1, "calibrated": true,
  "byClass": [ { "classification": "Tumor", "count": 1,
                 "totalAreaPixels": 2615200.0, "totalAreaMicrons2": 648565.68 } ] }
// get_detection_summary
{ "totalDetections": 5266, "cells": 5266, "byClass": [ { "classification": "Unclassified", "count": 5266 } ] }
```

**Success criteria:** correct image, correct annotation count/classes, no write operation. ✅

## Workflow 2 — Cell detection & measurement export

**Prompt:** *"Run cell detection inside the selected tumor annotation on DAPI and export the measurement
table as CSV."*

**Tool calls:** `qupath_get_selection_info` → `qupath_run_cell_detection` →
`qupath_get_detection_summary` → `qupath_export_measurements`.

**Result:**
```json
// run_cell_detection
{ "success": true, "detectionsBefore": 0, "detectionsAfter": 5266, "added": 5266,
  "parameters": { "detectionImage": "DAPI", "threshold": 1.0, ... } }
// export_measurements (objectType: cell)
{ "saved": ".../measurements_cell_<ts>.csv", "rows": 5266, "columns": [ /* 134 columns */ ] }
```

**Success criteria:** detection count increases; CSV exists, parses, has the expected columns; nothing
changed outside the selected annotation. ✅

## Workflow 3 — Recording & deterministic replay

**Prompt:** *"Record this workflow — set image type, create/select a full-image annotation, run DAPI cell
detection — then save it and replay it."*

**Tool calls:** `qupath_start_workflow_recording` → (analysis steps) → `qupath_stop_workflow_recording`
→ `qupath_save_workflow` → `qupath_replay_workflow`.

**Result (replay from a cleared state, no LLM involved):**
```json
{ "status": "success",
  "annotationsBefore": 0, "annotationsAfter": 1,
  "detectionsBefore": 0, "detectionsAfter": 5266 }
```

**Success criteria:** the saved workflow contains the executable steps; replay runs without an LLM and
reproduces the outputs (1 annotation + 5266 detections). ✅

---

Each example directory contains the exact prompt, the JSON-RPC tool-call trace, output files, and
before/after screenshots (see [`../examples/`](../examples/)).
