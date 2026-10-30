# Workflow 1 — State inspection & annotation summary

## Prompt

> Summarize the current QuPath image, the annotation hierarchy, any selected objects, and the
> measurement columns that are available. Do not change anything.

## Intent

The agent must **read** the current QuPath state before acting — the core QuPath-MCP pattern. Every
tool call is read-only; nothing in the project is modified.

## Tools used (all read-only)

`qupath_get_image_info` → `qupath_get_viewer_state` → `qupath_get_selection_info` →
`qupath_get_annotation_summary` → `qupath_get_detection_summary`

See [`trace.jsonl`](trace.jsonl) for the exact requests and salient responses, and
[`viewer_state.jpg`](viewer_state.jpg) for the captured view. Success criteria in
[`expected.md`](expected.md).
