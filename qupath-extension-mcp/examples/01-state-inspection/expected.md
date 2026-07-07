# Expected result — Workflow 1

The agent produces a state summary equivalent to:

- **Image:** LuCa-7color multiplex fluorescence, 1868 × 1400 px, 7 channels, ~0.498 µm/px,
  image type FLUORESCENCE.
- **Viewer:** whole image in view at downsample 1.0; annotation and detection overlays on.
- **Selection:** 1 annotation selected — a `Tumor` rectangle (≈648,566 µm²).
- **Annotations:** 1 total, all `Tumor`.
- **Detections:** 5266 cells, currently all `Unclassified`.

## Success criteria

- [x] Correct image identified (name, dimensions, channel count, pixel size).
- [x] Correct annotation count and class.
- [x] Selection correctly reported.
- [x] Detection count reported (5266).
- [x] **No write operation performed** — project state unchanged.
