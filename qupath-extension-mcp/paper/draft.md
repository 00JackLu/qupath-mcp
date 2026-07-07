# QuPath-MCP: a state-aware interface for reproducible AI-agent workflows in digital pathology

**Authors:** *[to be completed]*
**Affiliations:** *[to be completed]*
**Corresponding author:** *[to be completed]*

---

## Abstract

**Motivation:** Large language model (LLM) agents can now operate scientific software through the
Model Context Protocol (MCP), but the common pattern — exposing a single "run a script" tool — turns
the agent into an unverifiable black box: it acts without inspecting state, its writes are unchecked,
and its analyses cannot be reproduced without re-invoking the model. Digital pathology, where a single
whole-slide analysis may involve many parameterised steps, needs agent access that is *grounded*,
*controlled*, and *reproducible*.

**Results:** We present QuPath-MCP, a state-aware, model-agnostic interface that enables AI agents to
inspect, execute, verify, and reproduce digital-pathology workflows in QuPath. It exposes 34 typed
tools organised into project/image state, viewer perception, annotation, detection and measurement,
script execution, and provenance/replay categories; every tool declares a machine-readable side-effect
hint (read-only, write, or destructive), and destructive capabilities are individually gated. A
built-in provenance layer records each executed step with its parameters and generated Groovy, and a
deterministic replay tool re-runs a saved workflow inside QuPath with no model in the loop. We
demonstrate the interface on multiplex immunofluorescence, including cell detection (5266 cells) and
byte-identical replay from a cleared state, and provide a 27-task benchmark for agent evaluation.

**Availability and implementation:** QuPath-MCP is a Java extension for QuPath 0.8, distributed under
the GNU GPL v3 (inherited from QuPath) at *[repository URL]* with an archived release at
*[Zenodo DOI]*. Documentation, three reproducible example workflows, and the benchmark are included.

---

## 1 Introduction

QuPath (Bankhead *et al.*, 2017) is a widely used open-source platform for whole-slide image analysis,
scriptable in Groovy and extensible in Java. Independently, LLM agents have begun to drive desktop and
scientific applications through the Model Context Protocol (MCP), a standard for exposing tools to a
model over a uniform transport. Connecting the two is attractive: a pathologist could ask an agent to
"detect cells in the tumour region and export the measurements", and have it done.

The naive way to do this is to expose one tool that executes arbitrary Groovy. This works for a demo
but fails as a scientific instrument for three reasons. First, the agent acts **without grounding** —
it cannot cheaply see which image is open, what is selected, or what has already been computed, so it
guesses. Second, its actions are **unverified** — a script that silently returns `false` (as several
QuPath plugin calls do on failure) is reported as success. Third, and most importantly for science,
the result is **not reproducible**: the analysis lives only in the model's transcript, and re-running
it requires re-invoking the model, which is non-deterministic and costly.

QuPath-MCP addresses these problems by treating the interface itself — not the model — as the locus of
correctness. Its design goals are: (i) **state-aware grounding**, so an agent inspects before it acts;
(ii) a **structured tool interface** with explicit side-effect semantics; (iii) **controlled
execution** with per-capability kill-switches; and (iv) **first-class provenance and deterministic,
model-free replay**. The result is a state-aware, model-agnostic interface that lets agents inspect,
execute, verify, and reproduce digital-pathology workflows — *not* a thin `run_script` wrapper.

## 2 System design

### 2.1 State-aware grounding

The interface leads with read-only state tools: `get_project_info`, `get_image_info` /
`get_image_metadata`, `get_viewer_state`, `get_selection_info`, `get_annotation_summary`, and
`get_detection_summary` report the project, the current image and its calibration, the on-screen
region and channels, the current selection, and object counts/areas by classification. A vision-capable
agent can additionally request `capture_view`, a size-bounded rendered snapshot of exactly what QuPath
is displaying (opt-in; see §2.3). Because these tools are cheap and side-effect-free, the natural agent
strategy — and the one the benchmark rewards — is to establish state before acting.

### 2.2 Structured tool interface

QuPath-MCP exposes 34 typed tools (Table 1) rather than a single scripting endpoint. Beyond state,
these cover annotation inspection/creation/classification, cell detection and measurement, measurement
export, and provenance. Every tool carries a machine-readable hint that a client can inspect *before*
calling: **read-only** (inspects state), **write** (changes state non-destructively, e.g. selection or
reclassification), or **destructive** (deletes data, runs code, or writes files). This lets a client
enforce policy — for example, auto-approving read-only calls while confirming destructive ones —
without parsing free-form scripts. General Groovy execution remains available through `run_script`, but
it is one gated, clearly-labelled fallback among 34 tools, and it returns rich diagnostics
(`errorType`, cause chain, root cause, stack trace) and surfaces silently-swallowed plugin failures as
warnings rather than false successes.

### 2.3 Controlled execution

Capabilities are individually gated by configuration: `enableScripts` (Groovy execution and replay),
`enablePlugins` (cell detection and arbitrary plugins), `enableFileAccess` (measurement/workflow
export and project save), and `enableCaptureView` (streaming on-screen pixels to the agent, **off by
default**). The server binds to localhost only and ships no remote exposure. These switches let a
deployment range from full autonomy to a read-only, inspection-only agent.

### 2.4 Provenance and deterministic replay

QuPath records scriptable commands (image type, cell detection, classifiers) into a per-image history.
QuPath-MCP exposes and extends this into a reproducibility pipeline: `start`/`stop_workflow_recording`
bracket a session, `export_workflow_json` emits a structured provenance record (QuPath version, input
image, and each step's parameters and generated Groovy), and `save_workflow` writes a dated, runnable
`.groovy` into a `qupath_workflow/` folder in the project. Crucially, `replay_workflow` re-executes a
saved workflow directly in QuPath's context **with no model involved**, reporting object counts before
and after so the replay can be checked against the original run. The same artifact runs headless across
a project (`qupath script workflow.groovy -p project.qpproj`). Custom logic executed through
`run_script` is not auto-recorded; for those cases the agent assembles the full script and persists it
as the workflow `content`, so the saved artifact still reproduces the complete analysis.

## 3 Implementation

QuPath-MCP is a Java extension bundled with QuPath 0.8 (targeting JDK 25). It embeds an HTTP MCP server
implementing the Streamable HTTP transport (MCP spec 2025-03-26) inside QuPath's JVM. The transport is
built only on `java.base` (`java.net.ServerSocket`), so it runs on the trimmed runtime shipped with a
stock QuPath install with no extra JDK modules. The server is model- and client-agnostic: any
MCP-capable agent — Claude Code, Claude Desktop, Codex, Cursor — connects over HTTP directly or via the
`mcp-remote` stdio bridge. Installation is a normal QuPath extension drop-in (or building the bundled
fork). The repository includes per-tool documentation, a prompt cookbook, three reproducible example
workflows with screenshots and JSON-RPC traces, and the benchmark.

## 4 Use cases and validation

We validated QuPath-MCP on QuPath's public LuCa-7color multiplex immunofluorescence sample (an
8-channel component image: seven markers plus autofluorescence). Three end-to-end workflows (Fig. 2;
full artifacts in the repository) exercise the inspect → execute → verify → reproduce pattern:

1. **State inspection** — an agent summarises the image, viewer, selection, and annotation/detection
   hierarchy using only read-only tools, performing no writes.
2. **Cell detection and export** — the agent runs DAPI watershed cell detection inside the selected
   tumour annotation, confirms the detection count increased from 0 to 5266 (independently logged by
   QuPath), and exports a 5266 × 134-column measurement table to CSV.
3. **Recording and replay** — the agent records the analysis, saves it, and replays it from a cleared
   state; the model-free replay reproduces 1 annotation and 5266 detections exactly.

To evaluate agents systematically we provide a 27-task benchmark spanning state inspection, annotation
operations, cell detection, measurement export, workflow replay, and batch/project tasks. Each task
card specifies the input state, the user prompt, the expected behaviour, the tools an ideal agent would
use, and explicit success/failure criteria; several cards are deliberately adversarial (confirm before
deleting, verify before claiming success, inspect before acting) to test whether structured tools with
side-effect hints change agent behaviour relative to a bare scripting interface. Per-task logging
(success, tool-call count, execution error, invalid operation, output-file existence, replay success)
aggregates into Table 2.

In an initial run (Claude Opus 4.8 driving the live tools; Table 2), all 26 executable cards succeeded
(one batch/project card was blocked by a test-harness limitation in opening a multi-image project
programmatically, not a tool defect). Cell detection reproducibly yielded 5266 cells at threshold 1.0
and 4769 at 2.0; export produced a 5266 × 134 measurement table; and replay from a cleared state
reproduced one annotation and 5266 detections with no model in the loop. The benchmark also surfaced a
minor defect — `list_objects` including the root image object when listing annotations — illustrating
its diagnostic value.

## 5 Discussion

The distinguishing contribution of QuPath-MCP is not that an agent *can* drive QuPath — a scripting
tool already allows that — but that the interface makes agent-driven analysis **grounded, controllable,
and reproducible**. Provenance and deterministic replay convert a one-off, model-dependent transcript
into a durable, auditable artifact that runs without the model and scales headlessly across a project.
Side-effect hints and per-capability gating give deployments a spectrum from full autonomy to
inspection-only. Limitations include the localhost/no-authentication trust model (single-user, trusted
agent by design), reliance on QuPath's history for automatic provenance (custom scripts must be
captured explicitly), and a v0.1 tool set focused on the detection–measurement–export path. Future work
includes classifier and pixel-classification tools, multi-image batch orchestration as first-class
tools, and dry-run/confirmation affordances for destructive operations.

## Availability and requirements

- **Project name:** QuPath-MCP
- **Project home page:** *[repository URL]*
- **Archived version:** *[Zenodo DOI]*
- **Operating systems:** platform-independent (Windows, macOS, Linux)
- **Programming language:** Java (QuPath 0.8; JDK 25)
- **Other requirements:** QuPath 0.8.x; an MCP-capable client
- **License:** GNU GPL v3 (inherited from QuPath)

## Funding

*[to be completed]*

## References

- Bankhead, P. *et al.* (2017) QuPath: Open source software for digital pathology image analysis.
  *Scientific Reports*, 7, 16878.
- Model Context Protocol specification (2025-03-26). *[citation to be completed]*
- *[additional references to be completed]*
