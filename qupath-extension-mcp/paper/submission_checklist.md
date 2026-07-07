# Submission-readiness checklist (Phase 8)

Status of each deliverable for the Application Note. Legend: ✅ done · 🟡 done, pending an external
action (push / author input / live run) · ⬜ not started.

| # | Item | Status | Where / note |
|---|------|:------:|--------------|
| 1 | GitHub repo public/accessible | 🟡 | Fork `origin` (00JackLu/qupath-mcp); commits held locally, **not pushed** per instruction. Push + make public to close. |
| 2 | License included | ✅ | GNU GPL v3 at repo root `LICENSE` (extension inherits it). |
| 3 | README quick start | ✅ | `README.md` §1–3; `docs/quick_start.md`. |
| 4 | Docs for all tools | ✅ | `docs/tools.md` (all 34 tools, R/W/D hints, plan-name mapping). |
| 5 | Example data available | ✅ | QuPath public **LuCa-7color** sample; referenced in `examples/README.md`. |
| 6 | ≥3 reproducible workflows | ✅ | `examples/01–03` (prompt, trace, outputs, screenshot, criteria). |
| 7 | Benchmark task cards included | ✅ | `benchmark/` — 27 cards + `tasks.csv` + results schema. |
| 8 | Benchmark results CSV | 🟡 | Schema ready (`benchmark/results_template.csv`); needs live runs on a rebuilt 34-tool QuPath. |
| 9 | Figure 1 complete | ✅ | `paper/figures/figure1_system_overview.svg`. |
| 10 | Figure 2 complete | ✅ | `paper/figures/figure2_example_workflow.svg`. |
| 11 | Tool table complete | ✅ | `paper/tables/table1_tool_categories.md` (Table 1). |
| 12 | Release archived (Zenodo) | ⬜ | After push + tag; mint DOI, paste into draft + this table. |
| 13 | Availability & Implementation section | 🟡 | `paper/draft.md` §Availability; URL/DOI placeholders pending #1/#12. |
| 14 | New-user install works | 🟡 | README drop-in / build-from-fork instructions; verify on a clean QuPath 0.8 install. |
| 15 | Clearly > a run_script wrapper | ✅ | Table 1 (34 tools; `run_script` is 1 gated fallback), provenance/replay, adversarial benchmark cards. |

## Remaining external actions (not blocking the writeup)

1. **Push** the held commits to `origin` and make the repo public *(needs explicit user authorization —
   currently held)*.
2. **Live benchmark runs** → fill `benchmark/results_*.csv` and Table 2.
3. **Zenodo** archive of a tagged release → DOI into `draft.md` and item #12.
4. **Author metadata** (names, affiliations, corresponding author, funding) into `draft.md`.
5. **Clean-install smoke test** of the extension JAR on a stock QuPath 0.8.

## What is fully done

The repository, documentation, v0.1 tool set (34 tools), three reproducible examples, the benchmark
task cards + scoring schema, Figures 1–2, Table 1, and the outline + first draft are complete and
committed. The remaining items are external/operational (push, live runs, DOI, author details), not
authoring gaps.
