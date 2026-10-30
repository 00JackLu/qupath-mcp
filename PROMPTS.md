# QuPath MCP — Prompt Cookbook

Natural-language prompts for driving QuPath through the MCP server (18 tools). Point any MCP
client (Claude Code/Desktop, Codex, Cursor, …) at `http://127.0.0.1:9527/mcp` with an image open,
then say things like the examples below. Each prompt lists the tools it exercises.

> Tips that avoid dead ends (learned the hard way):
> - **Cell detection channel:** this QuPath build wants `detectionImage: "<channel name>"` (e.g.
>   `"DAPI"`), **not** the old `detectionImageFluorescence` index — the latter is rejected and the
>   error is swallowed, so a wrong key silently yields 0 cells.
> - **`read_region` with `format:"image"`** returns the rendered **RGB composite** you see in the
>   viewer (channel colors + brightness/contrast), so multi-channel fluorescence looks right.
> - **`run_script`** already has QuPath's default imports — call `getCurrentImageData()`,
>   `getAnnotationObjects()`, `runPlugin(...)` etc. directly, no `import` lines needed.
> - Project tools (`get_project_info`, `list_images`, `save_data`) need a **project** open; a
>   standalone image alone will return a clear "no project open" error.

---

## 1. Orient yourself on the image
- *"What image is open? Give me its size, pixel size, channels and image type."* → `get_image_info`
- *"List the channel names and tell me which one is the nuclear stain."* → `get_image_info`
- *"Summarize the intensity of every channel over the whole image (min/max/mean)."*
  → `read_region` (`format:"stats"`, whole image at a downsample)
- *"Show me a 512×512 crop centered on tissue at (600,400)."* → `read_region` (`format:"image"`)

## 2. Annotations & regions
- *"Draw a 400×300 rectangle annotation at (100,100) and class it 'Tumor'."* → `create_annotation`
- *"Add a polygon annotation through these points and call it 'Stroma'."* → `create_annotation`
- *"List all objects and show me the annotation ROIs."* → `list_objects`
- *"Give me the full details and measurements of annotation <id>."* → `get_object_details`
- *"Delete all annotations"* / *"delete objects <id1>,<id2>."* → `delete_objects`

## 3. Cell / nucleus detection (the flagship task)
- *"Run nucleus detection on the DAPI channel over the whole image, then tell me how many cells."*
  → `run_script` (`createFullImageAnnotation(true)` + `runPlugin('…WatershedCellDetection', …)`)
- *"Detect cells in the selected annotation only, DAPI threshold 1.0, 5 µm cell expansion."*
  → `run_script`
- *"For the detected cells, report mean nucleus area and mean DAPI intensity."* → `run_script`

## 4. Phenotyping / quantification
- *"For each marker (PDL1, CD8, FoxP3, CD68, PD1, CK), how many cells are positive using a
  mean+2·SD cutoff? Give counts and percentages."* → `run_script`
- *"Classify cells as tumor (CK+), CD8 T cell, or macrophage (CD68+) and give the breakdown."*
  → `run_script`
- *"What fraction of cells are CK+ epithelial?"* → `run_script`

## 5. Import / export
- *"Export all annotations as GeoJSON."* → `export_geojson` (returns a `FeatureCollection` inline)
- *"Import this GeoJSON FeatureCollection as annotations."* → `import_geojson`
- *"Round-trip check: export annotations, clear them, re-import, confirm the count matches."*
  → `export_geojson` → `delete_objects` → `import_geojson` → `list_objects`

## 6. Projects & saving
- *"What images are in the current project?"* → `list_images` / `get_project_info`
- *"Open the image at <path> (or project entry <id>)."* → `open_image`
- *"Save the current data back to the project."* → `save_data` (project required)

## 7. Plugins & provenance
- *"What plugins are available?"* → `list_plugins`
- *"Run <plugin> with these parameters."* → `run_plugin`
- *"Show me the workflow / command history for this image."* → `get_workflow`

## 8. Scripting escape hatch
Anything not covered by a dedicated tool: *"Run this Groovy: …"* → `run_script`
(full `QPEx` API, default imports on). Good for custom measurements, batch logic, thresholders,
classifiers, and calling any QuPath plugin by class name.

---

## End-to-end example (what a full request looks like)

> *"On the open image: detect nuclei on DAPI over the whole slide, quantify all channels per cell,
> then give me total cell count, mean nucleus area, and the CK+ tumor fraction. Leave the cells in
> the viewer."*

Under the hood the agent will: `get_image_info` (find DAPI + intensity range) → `run_script`
(`createFullImageAnnotation` + `WatershedCellDetection` with `detectionImage:"DAPI"`) → `run_script`
(aggregate measurements) → report.
