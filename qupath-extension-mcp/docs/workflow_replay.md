# Workflow Provenance & Replay

Reproducibility is QuPath-MCP's central design goal: an analysis driven by an agent should be
re-runnable **without** the agent, deterministically.

## How recording works

QuPath records scriptable commands (e.g. `runPlugin`, classifier application, image-type changes) into a
per-image **history workflow**. QuPath-MCP exposes and extends this:

- `qupath_start_workflow_recording` — marks a start point (optionally clearing prior history).
- `qupath_stop_workflow_recording` — reports how many steps were recorded and the runnable script.
- `qupath_get_workflow` — the recorded steps plus a runnable Groovy `script`.
- `qupath_export_workflow_json` — a structured provenance record:

```json
{
  "qupathVersion": "0.8.0-SNAPSHOT",
  "image": "LuCa-7color_[13860,52919]_1x1component_data.tif - resolution #1",
  "imageType": "FLUORESCENCE",
  "stepCount": 2,
  "steps": [
    { "name": "Set image type", "script": "setImageType('FLUORESCENCE')" },
    { "name": "Cell detection", "parameters": { "detectionImage": "DAPI", ... },
      "script": "runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{...}')" }
  ],
  "script": "setImageType('FLUORESCENCE')\nrunPlugin('...', '{...}')\n"
}
```

## Saving

`qupath_save_workflow` writes the workflow (recorded, or an agent-curated script passed as `content`) to
`<project>/qupath_workflow/<name>_<timestamp>.groovy` (or `~/qupath_workflow/` when no project is open).

## Replay — deterministic and LLM-free

`qupath_replay_workflow` runs a saved `.groovy`/`.json` workflow directly in QuPath's context. No model is
involved in the replay. It reports object counts before and after so the replay can be compared with the
original run:

```json
{ "status": "success", "annotationsBefore": 0, "annotationsAfter": 1,
  "detectionsBefore": 0, "detectionsAfter": 5266 }
```

Or headless, outside any client:

```bash
qupath script my_workflow.groovy -p project.qpproj
```

## Important caveat

Only **QuPath-recorded** steps are captured automatically (`runPlugin`, classifiers, image type, …).
Custom logic run through `qupath_run_script` — e.g. a per-cell phenotyping loop — is **not** in the
recorded history. For those, the agent assembles the full script and persists it via `save_workflow`'s
`content` parameter, so the saved artifact reproduces the complete analysis.
