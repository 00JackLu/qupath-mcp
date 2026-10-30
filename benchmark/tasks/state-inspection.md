# Benchmark — State inspection (`ST-01`…`ST-05`)

All read-only. A destructive write here counts as an **invalid operation** (automatic failure).

---

## ST-01 — Identify the open image

- **Input:** LuCa-7color open.
- **User prompt:** *"What image is currently open? Give name, dimensions, channels, and pixel size."*
- **Expected behavior:** one call to `qupath_get_image_info`; report name, 1868×1400, 7 channels,
  ~0.498 µm/px.
- **Required tools:** `qupath_get_image_info`.
- **Success:** all four attributes correct.
- **Failure:** wrong/invented values; any write; using `run_script` to reimplement what the tool gives.

## ST-02 — Describe the viewer

- **Input:** LuCa-7color open, whole image in view.
- **User prompt:** *"Describe the current viewer: what region is shown, at what zoom, which channels
  and overlays are on."*
- **Expected behavior:** `qupath_get_viewer_state`; report region, downsample/magnification, shown
  channels, overlay flags.
- **Required tools:** `qupath_get_viewer_state`.
- **Success:** region + zoom + overlay state reported and internally consistent.
- **Failure:** hallucinated region; any write.

## ST-03 — Annotation summary

- **Input:** one `Tumor` annotation present.
- **User prompt:** *"How many annotations are there and what classes, with total area per class?"*
- **Expected behavior:** `qupath_get_annotation_summary`; report count, class(es), and per-class area
  (µm² since calibrated).
- **Required tools:** `qupath_get_annotation_summary`.
- **Success:** correct count + class + area, with units.
- **Failure:** counts objects by listing and mis-tallying; reports pixels as if µm²; any write.

## ST-04 — Selection state

- **Input:** one annotation selected.
- **User prompt:** *"Is anything selected? Describe the selected objects."*
- **Expected behavior:** `qupath_get_selection_info`; report selected count, type, class, ROI.
- **Required tools:** `qupath_get_selection_info`.
- **Success:** selection correctly described (or correctly "nothing selected").
- **Failure:** wrong selection; any write.

## ST-05 — Full state briefing (composite)

- **Input:** LuCa-7color open, 1 `Tumor` annotation, 5266 detections.
- **User prompt:** *"Give me a full state briefing before I start: image, viewer, selection,
  annotations, detections."*
- **Expected behavior:** chains the five read-only state tools and synthesizes one briefing.
- **Required tools:** `qupath_get_image_info`, `qupath_get_viewer_state`, `qupath_get_selection_info`,
  `qupath_get_annotation_summary`, `qupath_get_detection_summary`.
- **Success:** all five aspects covered and mutually consistent; ≤ ~6 tool calls; no writes.
- **Failure:** omits a requested aspect; any write; uses `run_script` instead of the structured tools.
