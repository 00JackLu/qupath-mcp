# Baseline comparison (Phase 5, optional)

To show that the *structured, state-aware* interface — not merely "an LLM plus QuPath" — is what
delivers reproducibility, we compare three interface conditions on the **same** 27 benchmark task
cards, same model, same client. Optional for the Applications Note; recommended for an ISMB/ISBI tool
track.

## Conditions

- **A · Direct LLM Groovy scripting.** No MCP. The agent writes a Groovy script; a human pastes it into
  QuPath's script editor and runs it. Measures raw scripting competence.
- **B · MCP, run-script-only.** The MCP server exposes **only** `qupath_run_script` (structured state,
  annotation, detection, measurement, and provenance tools removed). The agent must do everything
  through scripts, but over the live transport.
- **C · Structured tools (full QuPath-MCP).** The full 34-tool interface, as shipped.

Condition B is produced by launching with only `run_script` enabled (or filtering the advertised tool
list); A needs no server.

## Metrics (per condition, aggregated over the 27 cards)

| Metric | Definition |
|--------|------------|
| Task success rate | fraction of cards meeting all success criteria |
| Execution error rate | fraction of runs with a thrown/return-false failure |
| Correction turns | mean human/agent turns needed to fix a broken attempt |
| State-inspection rate | fraction of tasks where the agent inspected state before acting |
| Verified-write rate | fraction of write tasks where the agent confirmed the effect |
| **Replayable** | can the produced artifact be re-run deterministically **without the model**? |

## Expected comparison table

| Condition | Success | Exec-error | Correction turns | Inspects state | Replayable |
|-----------|:-------:|:----------:|:----------------:|:--------------:|:----------:|
| A · Direct LLM Groovy | — | — | — | rarely | **No** |
| B · MCP run-script-only | — | — | — | sometimes | **Partial** |
| C · Structured tools | — | — | — | usually | **Yes** |

The qualitative **Replayable** column is the headline: only condition C yields a saved workflow that
`replay_workflow` (or `qupath script … -p project.qpproj`) reproduces with no model in the loop.
Conditions A/B may *complete* tasks but leave the analysis trapped in the model transcript.

## Procedure

1. Fix model + client + QuPath version; open the LuCa-7color sample.
2. For each condition, run all 27 cards (blind to expected behavior), logging the metrics above into a
   per-condition CSV (extend `results_template.csv` with a `condition` column).
3. Aggregate into the comparison table and into Table 2's discussion.

> **Status: design frozen; runs pending.** Requires live model runs against a rebuilt 34-tool QuPath
> and a run-script-only launch for condition B.
