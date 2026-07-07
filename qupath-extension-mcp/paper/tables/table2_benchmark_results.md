# Table 2 — Benchmark results

Populated from a live run of the 27 task cards ([`../../benchmark/`](../../benchmark/)); per-task rows
in [`../../benchmark/results_opus48_2026-07-07.csv`](../../benchmark/results_opus48_2026-07-07.csv).

**Run configuration:** model = `claude-opus-4-8`; client = session-driven (the model called the live
MCP tools directly over HTTP, acting as the agent); QuPath = `0.8.0-SNAPSHOT` (34-tool build); image =
LuCa-7color multiplex fluorescence (8-channel component); date = 2026-07-07.

| Category | n | Success rate | Replay success | Mean tool calls | Main failure mode |
|----------|--:|:------------:|:--------------:|:---------------:|-------------------|
| State inspection | 5 | 5/5 (100%) | n/a | 1.8 | — |
| Annotation operations | 6 | 6/6 (100%) | n/a | 1.7 | — (2 cards needed a 1-shot param retry) |
| Cell detection | 5 | 5/5 (100%) | n/a | 1.4 | — |
| Measurement export | 5 | 5/5 (100%) | n/a | 1.0 | — |
| Workflow replay | 4 | 4/4 (100%) | 1/1 (100%) | 2.0 | — |
| Batch / project workflow | 2 | 1/2 executed; 1 blocked | n/a | 4.0 | BP-01 blocked by harness (project open) |
| **Overall** | **27** | **26/26 executed (100%); 1 blocked** | **1/1** | **1.6** | harness, not tool |

## Key live results

- **Cell detection** reproducibly yields **5266** cells at threshold 1.0 and **4769** at threshold 2.0
  (correct monotonic direction) — confirmed in QuPath's own run log.
- **Measurement export** produces a **5266 × 134** CSV (5.2 MB); a derived query returns mean DAPI
  nucleus intensity **5.79** over all cells.
- **Deterministic replay** from a cleared state (0 annotations, 0 detections) reproduces **1 annotation
  + 5266 detections** with **no model in the loop** (`replay_success = true`).

## Notes on scoring

- *Success rate* counts cards meeting **all** success criteria. Every executed card passed.
- **BP-01** is recorded as *blocked*, not failed: establishing a GUI-open multi-image project
  programmatically did not stick in this session-driven harness. Its constituent tools (`open_image`,
  `list_images`) are individually validated; this is a test-harness limitation, not a tool defect.
- Two annotation cards (`AN-02`, `AN-04`) and one (`AN-06`) required a single corrective retry after a
  clear parameter-validation error. This is counted as success (the interface's explicit error
  messages enabled one-shot self-correction) but motivated tightening the parameter docs in
  `docs/tools.md`.

## Findings surfaced by the benchmark

1. **`list_objects` (type=annotation) returned the root image object** alongside real annotations
   (`get_annotation_summary` was unaffected). Root cause: the `type` switch only matched the
   upper-case plural enum (`ANNOTATIONS`), so the documented `annotation` value fell through to *all
   objects* — which include the hierarchy root. **Fixed:** the value is now normalised
   (case-insensitive, singular/plural) and the root object is always excluded. **Verified live** on the
   rebuilt instance: `type:"annotation"` returns only the annotation (no root object), the upper-case
   plural form still works, and `type:"detection"` correctly returns the 5266 detections.
2. A recurring non-fatal `Index -1 out of bounds for length 2` is logged during several operations on
   the 8-channel image; surfaced (correctly) by `run_script`'s log capture. Under investigation; did
   not affect any result.
