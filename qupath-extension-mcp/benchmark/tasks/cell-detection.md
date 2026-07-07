# Benchmark — Cell detection (`CD-01`…`CD-05`)

Requires `enablePlugins`. Detection cards reward verifying the before/after count rather than assuming
success.

---

## CD-01 — Basic DAPI detection

- **Input:** LuCa-7color, one `Tumor` annotation selected, 0 detections.
- **User prompt:** *"Run DAPI cell detection inside the selected tumor annotation."*
- **Expected behavior:** `qupath_run_cell_detection` (detectionImage DAPI); report detections
  added.
- **Required tools:** `qupath_run_cell_detection`.
- **Success:** detection runs; count goes 0 → ~5266; report reflects the real added count.
- **Failure:** claims success with no count check; runs on the wrong image/channel; unhandled error.

## CD-02 — Detection summary

- **Input:** detections present.
- **User prompt:** *"How many cells were detected, broken down by class?"*
- **Expected behavior:** `qupath_get_detection_summary`.
- **Required tools:** `qupath_get_detection_summary`.
- **Success:** correct total and per-class breakdown.
- **Failure:** wrong count; any write.

## CD-03 — Detect and verify (composite)

- **Input:** one `Tumor` annotation selected, 0 detections.
- **User prompt:** *"Run cell detection, then confirm the count actually increased from before."*
- **Expected behavior:** read count before → `qupath_run_cell_detection` → read count after → state
  the delta.
- **Required tools:** `qupath_get_detection_summary`, `qupath_run_cell_detection`.
- **Success:** explicit before (0) and after (~5266) counts with a stated increase.
- **Failure:** no before/after comparison; asserts success without evidence.

## CD-04 — Parameter sweep

- **Input:** one `Tumor` annotation selected.
- **User prompt:** *"Detect cells with a higher intensity threshold (2.0) and tell me how the count
  changed."*
- **Expected behavior:** `qupath_run_cell_detection` (threshold 2.0), compare to the default-threshold
  count; expect **fewer** cells.
- **Required tools:** `qupath_run_cell_detection`.
- **Success:** threshold 2.0 used; count is lower than the threshold-1.0 run and the direction is
  explained.
- **Failure:** ignores the threshold; reports an increase; no comparison.

## CD-05 — Annotation shape measurements

- **Input:** one `Tumor` annotation.
- **User prompt:** *"Add shape measurements (area, perimeter, circularity) to the tumor annotation."*
- **Expected behavior:** `qupath_measure_annotations`; the annotation gains area/perimeter/circularity
  measurements.
- **Required tools:** `qupath_measure_annotations`.
- **Success:** the named measurements are present on the annotation afterwards.
- **Failure:** no measurements added; wrong object; error.
