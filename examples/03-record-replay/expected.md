# Expected result — Workflow 3

## Recording & save

- `qupath_stop_workflow_recording` reports **3 steps** recorded (set image type, select-all, cell
  detection).
- `qupath_export_workflow_json` returns the structured provenance in [`workflow.json`](workflow.json)
  (version, image, per-step params + script).
- `qupath_save_workflow` writes [`workflow.groovy`](workflow.groovy) into
  `<project>/qupath_workflow/luca_dapi_detection_<date>.groovy`.

## Replay (no LLM)

Starting from a **cleared** state, `qupath_replay_workflow` runs the saved `.groovy` directly in
QuPath's context and reports:

```json
{ "status": "success",
  "annotationsBefore": 0, "annotationsAfter": 1,
  "detectionsBefore": 0, "detectionsAfter": 5266 }
```

The same file replays headless, outside any client:

```bash
qupath script workflow.groovy -p project.qpproj
```

## Success criteria

- [x] The saved workflow contains the executable steps in order.
- [x] Replay runs **without any model** in the loop.
- [x] Replay reproduces the original outputs exactly: **1 annotation + 5266 detections**.
- [x] The provenance JSON records QuPath version, input image, and every step's parameters — so the
      run is auditable after the fact.

## Caveat

Only QuPath-recorded steps are captured automatically. Custom logic run through `qupath_run_script`
(e.g. a per-cell phenotyping loop) is **not** in the recorded history; for those the agent assembles
the full script and passes it to `qupath_save_workflow` as `content`. See
[`../../docs/workflow_replay.md`](../../docs/workflow_replay.md).
