# Table 2 — Benchmark results (template)

Populated from [`../../benchmark/`](../../benchmark/) live runs (per-task rows in the results CSV,
schema in `benchmark/results_template.csv`). One row per category; `n` is the number of task cards.

> **Status: awaiting live runs.** The 27 task cards are frozen (Phase 4). Numbers below are filled in
> once the cards are executed against a running 34-tool QuPath via an MCP agent. Run configuration to
> record: **model**, **MCP client**, QuPath version, date.

**Run configuration:** model = `<…>`, client = `<…>`, QuPath = `0.8.0-SNAPSHOT`, date = `<…>`.

| Category | n | Success rate | Replay success | Mean tool calls | Main failure mode |
|----------|--:|:------------:|:--------------:|:---------------:|-------------------|
| State inspection | 5 | — | n/a | — | — |
| Annotation operations | 6 | — | n/a | — | — |
| Cell detection | 5 | — | n/a | — | — |
| Measurement export | 5 | — | n/a | — | — |
| Workflow replay | 4 | — | — | — | — |
| Batch / project workflow | 2 | — | n/a | — | — |
| **Overall** | **27** | — | — | — | — |

*Success rate* = fraction of cards meeting **all** success criteria. *Replay success* = fraction of
replay-category cards whose LLM-free replay reproduced the expected object counts. *Main failure mode*
= most common reason a card failed (e.g. acted-without-inspecting, claimed-success-without-verifying,
wrong-target).
