# Figures

Vector figures for the Application Note. SVG renders inline on GitHub and is directly usable in the
manuscript (convert to PDF/EPS at submission if the venue requires).

| File | Figure | Content |
|------|--------|---------|
| [`figure1_system_overview.svg`](figure1_system_overview.svg) | **Figure 1** | System overview: prompt → model-agnostic MCP agent → QuPath-MCP server (tool categories with R/W/D hints) → QuPath (project/viewer/objects/measurements) → workflow log + deterministic replay. |
| [`figure2_example_workflow.svg`](figure2_example_workflow.svg) | **Figure 2** | Example workflow: inspect state → select tumor region → DAPI cell detection (0→5266, verified) → measurement export (5266×134 CSV) → record/save/replay without an LLM. |

The Figure 2 stages correspond one-to-one to the demonstration workflows and their real screenshots +
JSON-RPC traces under [`../../examples/`](../../examples/).
