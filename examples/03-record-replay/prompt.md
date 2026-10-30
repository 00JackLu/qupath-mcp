# Workflow 3 — Recording & deterministic replay

## Prompt

> Record this workflow: set the image type to fluorescence, create and select a full-image
> annotation, then run DAPI cell detection. When it's done, save the workflow and replay it so I can
> confirm it reproduces the same result without you.

## Intent

The paper's central claim: an agent-driven analysis is **reproducible without the agent**. The agent
marks a recording boundary, performs the analysis, stops recording, persists the workflow to disk,
then replays it in QuPath's own context — **no LLM involved in the replay** — and the object counts
match the original run.

## Tools used

`qupath_start_workflow_recording` (write) → (analysis: set image type, create/select annotation,
`qupath_run_cell_detection`) → `qupath_stop_workflow_recording` (read-only) →
`qupath_export_workflow_json` → `qupath_save_workflow` (writes a file) →
`qupath_replay_workflow` (destructive, needs `enableScripts`)

Artifacts: [`trace.jsonl`](trace.jsonl), [`workflow.groovy`](workflow.groovy) (what
`save_workflow` writes), [`workflow.json`](workflow.json) (what `export_workflow_json` returns).
Success criteria in [`expected.md`](expected.md).
