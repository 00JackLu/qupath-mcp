# Workflow 2 — Cell detection & measurement export

## Prompt

> Run cell detection inside the selected tumor annotation using the DAPI channel, then export the
> per-cell measurement table as a CSV so I can open it in a spreadsheet.

## Intent

A real analysis step with a **verified** outcome. The agent inspects the selection, runs Watershed
cell detection with typed parameters, confirms the detection count actually increased, and exports
the measurement table — checking the file was written and has the expected shape. Nothing outside the
selected annotation is touched.

## Tools used

`qupath_get_selection_info` (read-only) → `qupath_run_cell_detection` (destructive, needs
`enablePlugins`) → `qupath_get_detection_summary` (read-only) → `qupath_export_measurements`
(writes a file, needs `enableFileAccess`)

Artifacts: [`trace.jsonl`](trace.jsonl), [`measurements_sample.csv`](measurements_sample.csv),
[`detections_after.png`](detections_after.png). Success criteria in [`expected.md`](expected.md).
