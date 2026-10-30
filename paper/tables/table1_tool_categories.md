# Table 1 — QuPath-MCP tool categories (v0.1, 34 tools)

Each tool declares a machine-readable side-effect hint (**R** read-only, **W** write / non-destructive
state change, **D** destructive / runs code or writes files) that a client can inspect before calling.

| Category | Purpose | Tools | Side effect |
|----------|---------|-------|:-----------:|
| **Project & image state** | Ground the agent in the current project and image | `qupath_get_project_info`, `qupath_list_images`, `qupath_get_image_info`, `qupath_get_image_metadata` | R |
| **Viewer state & perception** | What is on screen right now | `qupath_get_viewer_state`, `qupath_read_region`, `qupath_capture_view`ᵃ | R |
| **Annotation** | Inspect / create / edit / classify regions | `qupath_list_objects`, `qupath_get_object_details`, `qupath_get_selection_info`, `qupath_get_annotation_summary`, `qupath_select_annotation` (W), `qupath_create_annotation` (D), `qupath_update_annotation_class` (W), `qupath_delete_objects` (D), `qupath_import_geojson` (D), `qupath_export_geojson` (R) | R/W/D |
| **Detection & measurement** | Run analysis and quantify | `qupath_run_cell_detection` (D)ᵇ, `qupath_get_detection_summary` (R), `qupath_measure_annotations` (W), `qupath_export_measurements` (D)ᶜ | R/W/D |
| **Script execution** | Advanced fallback (full QuPath API) | `qupath_validate_groovy_script` (R), `qupath_run_script` (D)ᵈ, `qupath_list_scripts` (R) | R/D |
| **Provenance & replay** | Record, persist, and reproduce a workflow | `qupath_get_workflow` (R), `qupath_export_workflow_json` (R), `qupath_start_workflow_recording` (W), `qupath_stop_workflow_recording` (R), `qupath_save_workflow` (D)ᶜ, `qupath_replay_workflow` (D)ᵈ | R/W/D |
| **Project actions** | Navigate and persist project data | `qupath_open_image` (W), `qupath_save_data` (D)ᶜ | W/D |
| **Plugins** | Escape hatch to any QuPath plugin | `qupath_list_plugins` (R), `qupath_run_plugin` (D)ᵇ | R/D |

ᵃ Opt-in, off by default (`enableCaptureView`). ᵇ Gated by `enablePlugins`.
ᶜ Writes a file; gated by `enableFileAccess`. ᵈ Executes code; gated by `enableScripts`.

**Design point for the paper:** read-only *state* tools outnumber and precede the write/destructive
tools. `run_script` is one tool among 34 — an advanced fallback, not the interface. This is what
distinguishes QuPath-MCP from a thin `run_script` wrapper.
