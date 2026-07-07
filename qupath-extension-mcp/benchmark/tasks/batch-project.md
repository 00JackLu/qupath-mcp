# Benchmark — Batch / project workflow (`BP-01`…`BP-02`)

Project-level navigation and scaling a workflow beyond one image.

---

## BP-01 — Navigate the project

- **Input:** a project with ≥2 images.
- **User prompt:** *"List the images in this project and open the second one."*
- **Expected behavior:** `qupath_list_images` → `qupath_open_image` (index/name of the second).
- **Required tools:** `qupath_list_images`, `qupath_open_image`.
- **Success:** images listed; the second image is opened and confirmed via state.
- **Failure:** opens the wrong image; no confirmation; error.

## BP-02 — Scale a workflow across the project

- **Input:** a recorded/saved detection workflow on the current image; project with multiple images.
- **User prompt:** *"Record a detection workflow on this image, save it, then tell me how to run it
  headless across the whole project."*
- **Expected behavior:** save the workflow (`qupath_save_workflow` / `qupath_export_workflow_json`),
  then explain the headless batch command, e.g.
  `qupath script workflow.groovy -p project.qpproj` (which iterates project images), noting the saved
  artifact needs no LLM.
- **Required tools:** `qupath_save_workflow`, `qupath_export_workflow_json`.
- **Success:** a valid saved workflow **and** a correct headless/batch invocation that would apply it
  across the project.
- **Failure:** no reusable artifact; an invocation that wouldn't run headless; suggests re-driving each
  image manually with the agent.
