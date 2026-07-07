# Benchmark — Measurement export (`ME-01`…`ME-05`)

Requires `enableFileAccess`. Export cards check that a file is actually written and parses.

---

## ME-01 — Export cell table

- **Input:** ~5266 detections present.
- **User prompt:** *"Export the per-cell measurement table as a CSV and tell me the path and row
  count."*
- **Expected behavior:** `qupath_export_measurements` (objectType cell); return path + row count.
- **Required tools:** `qupath_export_measurements`.
- **Success:** CSV written; reported row count matches detections (~5266); path returned.
- **Failure:** no file; wrong row count; fabricated path.

## ME-02 — Describe columns

- **Input:** detections present.
- **User prompt:** *"How many measurement columns does the detection table have? List a few."*
- **Expected behavior:** `qupath_export_measurements` (or its column metadata); report column count
  (~134) and examples.
- **Required tools:** `qupath_export_measurements`.
- **Success:** plausible column count and real example column names (centroid, nucleus area,
  per-channel intensities).
- **Failure:** invented columns; wrong magnitude.

## ME-03 — Export annotation table

- **Input:** ≥1 annotation with measurements.
- **User prompt:** *"Export annotation-level measurements to CSV."*
- **Expected behavior:** `qupath_export_measurements` (objectType annotation).
- **Required tools:** `qupath_export_measurements`.
- **Success:** annotation-level CSV written and returned.
- **Failure:** exports detections instead; no file.

## ME-04 — Export and verify (composite)

- **Input:** detections present.
- **User prompt:** *"Export the cell measurements, then verify the file exists and parses as CSV."*
- **Expected behavior:** export → confirm existence/parse (via `run_script` file read or a follow-up
  check).
- **Required tools:** `qupath_export_measurements` (+ verification).
- **Success:** file written **and** an explicit verification that it exists and parses.
- **Failure:** claims verification without checking; file missing.

## ME-05 — Derived statistic (composite)

- **Input:** ~5266 detections with DAPI measurements.
- **User prompt:** *"Give me the mean DAPI nucleus intensity across all detected cells."*
- **Expected behavior:** export or script over the measurement table to compute the mean of the
  `DAPI: Nucleus: Mean` column.
- **Required tools:** `qupath_export_measurements` and/or `qupath_run_script`.
- **Success:** a single numeric mean, computed over all cells from the real column.
- **Failure:** guesses a value; uses the wrong column; error.
