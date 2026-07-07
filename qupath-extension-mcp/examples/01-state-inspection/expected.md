# Expected result — Workflow 1

The agent produces a state summary equivalent to:

- **Image:** LuCa-7color multiplex fluorescence, 1868 × 1400 px, **8 channels** (7 markers —
  PDL1, CD8, FoxP3, CD68, PD1, CK, DAPI — plus autofluorescence), ~0.498 µm/px, image type
  Fluorescence.
- **Viewer:** whole image in view at downsample 1.0; annotation and detection overlays on.
- **Selection:** 1 annotation selected — a `Tumor` rectangle (≈648,566 µm²).
- **Annotations:** 1 total, all `Tumor`.
- **Detections:** 5266 cells, currently all `Unclassified`.

## Success criteria

- [x] Correct image identified (name, dimensions, channel count = 8, pixel size).
- [x] Correct annotation count and class.
- [x] Selection correctly reported.
- [x] Detection count reported (5266).
- [x] **No write operation performed** — project state unchanged.
