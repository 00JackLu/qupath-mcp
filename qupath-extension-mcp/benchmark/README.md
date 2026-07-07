# QuPath-MCP Benchmark (v0.1)

A small, reproducible benchmark that measures whether an AI agent, given only the QuPath-MCP tools,
can complete realistic digital-pathology tasks — and, critically, whether the result is
**verifiable and reproducible**, not just "the script ran".

## Design

- **27 task cards** across 6 categories (see below). Each card states the input state, the
  natural-language user prompt, the expected behavior, the tools an ideal agent would use, and
  explicit success / failure criteria.
- Tasks are **model-agnostic and client-agnostic**: any MCP-capable agent (Claude Code, Claude
  Desktop, Codex, Cursor, …) can be pointed at the running server and handed the prompt.
- The benchmark rewards the QuPath-MCP thesis: an agent should *inspect state before acting*,
  *verify the effect of a write*, and *produce a replayable artifact* — so cards score behavior, not
  just final output.

| Category | Cards | IDs |
|----------|------:|-----|
| State inspection | 5 | `ST-01`…`ST-05` |
| Annotation operations | 6 | `AN-01`…`AN-06` |
| Cell detection | 5 | `CD-01`…`CD-05` |
| Measurement export | 5 | `ME-01`…`ME-05` |
| Workflow replay | 4 | `WR-01`…`WR-04` |
| Batch / project workflow | 2 | `BP-01`…`BP-02` |
| **Total** | **27** | |

Full cards: [`tasks/`](tasks/). Machine-readable index: [`tasks.csv`](tasks.csv).

## Standard setup

Unless a card says otherwise:

- QuPath running with the MCP server (`docs/quick_start.md`), all feature flags **enabled**.
- The **LuCa-7color** multiplex fluorescence sample open, image type *Fluorescence*.
- A single full-image or tumor `Tumor` annotation present where a card needs a parent region.

## Running the benchmark (this session)

For each task card: hand the agent the card's **User prompt** only (not the expected behavior),
let it call tools, then score it against the card's criteria and append one row to a results CSV
using the schema in [`results_template.csv`](results_template.csv).

Per-task logged fields:

```
task_id, task_category, model, mcp_client, success_or_failure,
number_of_tool_calls, execution_error, invalid_operation,
output_file_exists, replay_success, notes
```

- `success_or_failure` — `success` only if **all** the card's success criteria are met.
- `execution_error` — a tool returned an error / the script threw.
- `invalid_operation` — the agent did something the card marks as a failure (e.g. a destructive write
  where inspection was asked, or acting without inspecting required state).
- `output_file_exists` — for export/save cards, the file is on disk and parses.
- `replay_success` — for replay cards, the LLM-free replay reproduced the expected counts.

## Scoring & reporting

Aggregate into **Table 2** (per category: tasks, success rate, replay success, main failure mode) and
emit both a results CSV and a markdown summary table. See [`../paper/plan.md`](../paper/plan.md)
Phase 6.

## Note on difficulty

Cards are ordered roughly easy → hard within a category. Some cards are deliberately **adversarial**
(ambiguous prompt, a destructive request that should be confirmed, a task requiring state inspection
first) to test whether the structured tools + side-effect hints actually change agent behavior versus
a bare `run_script` interface (the Phase 5 baseline comparison).
