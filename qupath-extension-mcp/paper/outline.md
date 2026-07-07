# Application Note — outline

**Target venue:** *Bioinformatics* Applications Note (≈2 pages) — also suitable for ISMB/ISBI tool
tracks.

**Title:** *QuPath-MCP: a state-aware interface for reproducible AI-agent workflows in digital
pathology.*

**One-sentence positioning (preserve verbatim):** QuPath-MCP is a state-aware, model-agnostic
interface that enables AI agents to inspect, execute, verify, and reproduce digital-pathology
workflows in QuPath.

## Structure

1. **Abstract** — Motivation / Results / Availability (≤150 words).
2. **Introduction** (~1 col)
   - Digital pathology + QuPath; rise of LLM agents.
   - The gap: bolting an agent onto `run_script` gives an unverifiable, unreproducible black box.
   - Contribution: a structured, state-aware, provenance-first MCP interface.
3. **System design** (the core)
   - (a) State-aware grounding — read-only state tools first.
   - (b) Structured tool interface — typed tools with machine-readable side-effect hints (Table 1).
   - (c) Controlled execution — feature kill-switches, localhost-only, opt-in perception.
   - (d) Provenance & replay — record → export JSON → deterministic LLM-free replay.
4. **Implementation** — QuPath 0.8 Java extension; Streamable HTTP (java.base only); model/client
   agnostic; install; docs; data; license; repo + archived release.
5. **Use cases & validation** — three workflows (Fig 2) + benchmark (Table 2).
6. **Discussion / conclusion** — reproducibility as the differentiator; limitations; outlook.
7. **Figures/Tables:** Fig 1 (system), Fig 2 (workflow), Table 1 (tools), Table 2 (benchmark).
8. **Availability & requirements; Funding; References.**

## Claims the draft must support with committed evidence

- "not a run_script wrapper" → Table 1 (34 tools, R/W/D), plan positioning.
- "inspect before acting" → examples/01, benchmark adversarial cards.
- "verify writes" → examples/02 (0→5266 confirmed in logs), CD-03.
- "reproduce without the model" → examples/03 replay, workflow_replay.md, WR-04.
- "controlled execution" → safety.md, config flags.
