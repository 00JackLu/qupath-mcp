# Benchmark — Workflow replay (`WR-01`…`WR-04`)

The differentiator category: provenance and LLM-free reproducibility.

---

## WR-01 — Record a workflow

- **Input:** LuCa-7color, one `Tumor` annotation selected.
- **User prompt:** *"Start recording a workflow, run DAPI cell detection, then stop and show me the
  recorded steps."*
- **Expected behavior:** `qupath_start_workflow_recording` → `qupath_run_cell_detection` →
  `qupath_stop_workflow_recording`; report the recorded steps.
- **Required tools:** `qupath_start_workflow_recording`, `qupath_run_cell_detection`,
  `qupath_stop_workflow_recording`.
- **Success:** recording brackets the detection; stop reports ≥1 step including the detection.
- **Failure:** no steps recorded; detection outside the recording; error.

## WR-02 — Export provenance JSON

- **Input:** a recorded workflow present.
- **User prompt:** *"Export the current workflow as structured JSON with all step parameters."*
- **Expected behavior:** `qupath_export_workflow_json`; return version, image, per-step params +
  script.
- **Required tools:** `qupath_export_workflow_json`.
- **Success:** JSON includes QuPath version, image, and each step's parameters.
- **Failure:** missing metadata; empty steps.

## WR-03 — Save a reproducible workflow

- **Input:** a recorded workflow present.
- **User prompt:** *"Save this analysis as a reproducible workflow file I can rerun later."*
- **Expected behavior:** `qupath_save_workflow`; write a dated `.groovy` under `qupath_workflow/`.
- **Required tools:** `qupath_save_workflow`.
- **Success:** file written; path returned; file contains the runnable steps.
- **Failure:** no file; empty/non-runnable script.

## WR-04 — Deterministic replay (the key card)

- **Input:** a saved workflow file; state cleared (0 annotations, 0 detections).
- **User prompt:** *"Replay the workflow I saved and confirm it reproduces the same object counts,
  with no model involved."*
- **Expected behavior:** `qupath_replay_workflow`; report before/after counts; note the replay ran in
  QuPath's context without an LLM.
- **Required tools:** `qupath_replay_workflow`.
- **Success:** replay runs LLM-free and reproduces the original counts (1 annotation + ~5266
  detections); `replay_success = true`.
- **Failure:** counts differ; replay silently errors; the agent re-derives the analysis with the model
  instead of replaying the saved artifact.
