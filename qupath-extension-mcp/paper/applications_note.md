<!--
  Bioinformatics (Oxford University Press) — Applications Note
  Formatting target: ~2 printed pages, structured abstract, numbered sections,
  Figures 1-2 (paper/figures/), Tables 1-2 (paper/tables/).
  This is the submission manuscript; paper/draft.md is the longer working draft.
  Placeholders in [brackets] require author input before submission.
-->

**Subject area:** Data and text mining / Bioimage informatics

# QuPath-MCP: a state-aware interface for reproducible AI-agent workflows in digital pathology

**[Author One]**<sup>1,\*</sup>, **[Author Two]**<sup>2</sup> and **[Author Three]**<sup>1</sup>

<sup>1</sup>[Department, Institution, City, Country]
<sup>2</sup>[Department, Institution, City, Country]

<sup>\*</sup>To whom correspondence should be addressed.

Associate Editor: [Name]

---

## Abstract

**Summary:** Large language model (LLM) agents can now drive scientific software through the Model
Context Protocol (MCP), but the prevailing pattern — exposing a single "run-a-script" tool — turns the
agent into an unverifiable black box that acts without inspecting state and leaves analyses that cannot
be reproduced without re-invoking the model. We present QuPath-MCP, a state-aware, model-agnostic
interface that lets AI agents inspect, execute, verify, and reproduce digital-pathology workflows in
QuPath. It exposes 34 typed tools spanning image/project state, viewer perception, annotation,
detection and measurement, script execution, and workflow provenance; every tool declares a
machine-readable side-effect hint (read-only, write, or destructive) and destructive capabilities are
individually gated. A provenance layer records each executed step and a replay tool re-runs a saved
workflow deterministically with no model in the loop. On a multiplex immunofluorescence sample, an
agent completed 26/26 executable benchmark tasks, and a workflow replayed from a cleared state
reproduced 1 annotation and 5266 detections without any LLM.

**Availability and implementation:** QuPath-MCP is a Java extension for QuPath 0.8, freely available
under the GNU GPL v3 at https://github.com/00JackLu/qupath-mcp and archived at https://doi.org/10.5281/zenodo.23788878.
Documentation, three reproducible example workflows, and a 27-task benchmark are included. It is
platform-independent (Windows, macOS, Linux).

**Contact:** [corresponding.author@institution.edu]

**Supplementary information:** Supplementary data (tool reference, example traces, benchmark task
cards and results) are available in the software repository.

---

## 1 Introduction

QuPath (Bankhead *et al.*, 2017) is a widely used open-source platform for whole-slide image analysis,
scriptable in Groovy and extensible in Java. Independently, LLM agents have begun to operate desktop
and scientific applications through the Model Context Protocol (MCP), an emerging standard for exposing
tools to a model over a uniform transport. Bridging the two is attractive: a user could simply ask an
agent to "detect cells in the tumour region and export the measurements."

The straightforward way to enable this is to expose a single tool that executes arbitrary Groovy. This
suffices for a demonstration but fails as a scientific instrument for three reasons. First, the agent
acts *without grounding*: it cannot cheaply see which image is open, what is selected, or what has
already been computed, so it guesses. Second, its actions are *unverified*: a QuPath command that
silently returns `false` on failure (as several plugin calls do) is reported as success. Third, and
most consequential for science, the result is *not reproducible*: the analysis exists only in the
model's transcript, and re-running it requires re-invoking a non-deterministic, costly model.

We address these problems by making the interface — not the model — the locus of correctness.
QuPath-MCP is designed for (i) state-aware grounding, so an agent inspects before it acts; (ii) a
structured tool interface with explicit side-effect semantics; (iii) controlled execution with
per-capability kill-switches; and (iv) first-class provenance and deterministic, model-free replay. The
result is a state-aware, model-agnostic interface for inspecting, executing, verifying, and
reproducing digital-pathology workflows — not a thin script-runner (Fig. 1).

## 2 Implementation

QuPath-MCP is a Java extension bundled with QuPath 0.8 (targeting JDK 25) that embeds an HTTP MCP
server (Streamable HTTP transport) inside QuPath's JVM, so tools call the QuPath API in-process with no
out-of-process bridge or version skew. The transport is built solely on the Java standard library
(`java.net.ServerSocket`) and therefore runs on the trimmed runtime shipped with a stock QuPath install
without additional JDK modules. The server binds to localhost and is model- and client-agnostic: any
MCP-capable client — Claude Code, Claude Desktop, Codex, Cursor — connects over HTTP directly or via a
lightweight stdio bridge.

The interface provides 34 typed tools organised into six categories (Table 1). It leads with read-only
*state* tools (project, image, viewer, selection, and annotation/detection summaries) that let a
vision-capable agent additionally request a size-bounded rendered snapshot of the viewer. Beyond state,
tools cover annotation inspection/creation/classification, cell detection, measurement and export, and
provenance. Every tool declares a machine-readable side-effect hint — read-only, write (non-destructive
state change), or destructive (deletes data, runs code, or writes files) — that a client can inspect
before calling to enforce policy without parsing free-form scripts. Capabilities are individually gated
by configuration (`enableScripts`, `enablePlugins`, `enableFileAccess`, `enableCaptureView`), allowing
deployments to range from full autonomy to an inspection-only agent. General Groovy execution remains
available but is one gated, clearly-labelled fallback among 34 tools; it returns rich diagnostics and
surfaces silently-swallowed plugin failures as warnings rather than false successes.

Reproducibility is built in. QuPath records scriptable commands into a per-image history; QuPath-MCP
exposes and extends this into a pipeline that brackets a session (`start`/`stop_workflow_recording`),
emits a structured provenance record (QuPath version, input image, and each step's parameters and
generated Groovy), and writes a dated, runnable script. Critically, a replay tool re-executes a saved
workflow directly in QuPath with no model involved, reporting object counts before and after so the
replay can be checked against the original run. The same artefact runs headless across a project
(`qupath script workflow.groovy -p project.qpproj`).

## 3 Results

We validated QuPath-MCP on QuPath's public LuCa-7color multiplex immunofluorescence sample (an
8-channel component image). Three end-to-end example workflows exercise the inspect → execute → verify
→ reproduce pattern (Fig. 2): (i) an agent summarises image, viewer, selection, and object hierarchy
using only read-only tools; (ii) it runs DAPI watershed cell detection inside the selected tumour
annotation, confirms the count increased from 0 to 5266 (independently logged by QuPath), and exports a
5266 × 134 measurement table; and (iii) it records the analysis, saves it, and replays it from a
cleared state, reproducing 1 annotation and 5266 detections exactly.

To evaluate agents systematically we provide a 27-task benchmark spanning state inspection, annotation
operations, cell detection, measurement export, workflow replay, and batch/project tasks; each task
specifies input state, prompt, expected behaviour, and explicit success/failure criteria, and several
tasks are deliberately adversarial (confirm before deleting, verify before claiming success, inspect
before acting). In an initial run (Claude Opus 4.8 driving the live tools), all 26 executable tasks
succeeded, with a mean of 1.6 tool calls per task; one batch/project task was blocked by a test-harness
limitation rather than a tool defect (Table 2). Cell detection reproducibly yielded 5266 cells at
intensity threshold 1.0 and 4769 at 2.0 (correct monotonic direction); a derived query returned mean
DAPI nuclear intensity 5.79 across all cells; and replay from a cleared state reproduced the full object
state with no model in the loop. The benchmark also surfaced a minor defect — `list_objects` including
the hierarchy root when listing annotations — which we fixed and re-verified, illustrating the
diagnostic value of a structured, checkable interface.

## 4 Discussion

The distinguishing contribution of QuPath-MCP is not that an agent *can* drive QuPath — a scripting
tool already allows that — but that the interface makes agent-driven analysis grounded, controllable,
and reproducible. Provenance and deterministic replay convert a one-off, model-dependent transcript
into a durable, auditable artefact that runs without the model and scales headlessly across a project;
side-effect hints and per-capability gating give deployments a spectrum from full autonomy to
inspection-only. Limitations include a localhost, single-user trust model (no authentication by
design), reliance on QuPath's history for automatic provenance (custom scripts must be captured
explicitly), and a v0.1 tool set focused on the detection–measurement–export path. Future work includes
first-class classifier and pixel-classification tools, multi-image batch orchestration, and
dry-run/confirmation affordances for destructive operations.

## Acknowledgements

[Acknowledgements.]

## Funding

[Funding statement.]

*Conflict of Interest:* none declared.

## References

Bankhead, P. *et al.* (2017) QuPath: Open source software for digital pathology image analysis.
*Sci. Rep.*, **7**, 16878.

Anthropic (2025) Model Context Protocol specification (revision 2025-03-26). [URL].

[Additional references as required by the venue.]

---

### Figure legends

**Fig. 1.** System overview. A natural-language prompt is handled by a model-agnostic MCP agent that
calls the QuPath-MCP server embedded in QuPath's JVM. Tools are organised by category and annotated
with read-only/write/destructive side-effect hints; execution over the QuPath project, viewer, objects
and measurements is recorded to a workflow log that supports deterministic, model-free replay.
(`paper/figures/figure1_system_overview.svg`)

**Fig. 2.** Example workflow. Inspect state → select the tumour region → DAPI cell detection (0 → 5266,
verified) → measurement export (5266 × 134 CSV) → record, save and replay without an LLM, reproducing
1 annotation and 5266 detections. (`paper/figures/figure2_example_workflow.svg`)

### Tables

**Table 1.** QuPath-MCP tool categories and side-effect hints (34 tools).
See `paper/tables/table1_tool_categories.md`.

**Table 2.** Benchmark results by category (27 tasks; live run).
See `paper/tables/table2_benchmark_results.md`.
