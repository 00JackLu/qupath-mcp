/*-
 * #%L
 * QuPath MCP Extension
 * %%
 * Copyright (C) 2024 QuPath developers
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.extension.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.extension.mcp.protocol.models.ToolDefinition;
import qupath.lib.extension.mcp.protocol.models.ToolResult;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.scripting.QP;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Tools for running detection/analysis and exporting quantitative results.
 */
public class AnalysisTools {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisTools.class);

    private static final String WATERSHED = "qupath.imagej.detect.cells.WatershedCellDetection";

    /**
     * Typed wrapper over QuPath's watershed cell detection.
     */
    public static class RunCellDetection implements McpTool {

        private static final String NAME = "qupath_run_cell_detection";

        private final McpToolRegistry registry;

        public RunCellDetection(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");
            JsonObject properties = new JsonObject();

            addString(properties, "detectionImage", "Channel to detect nuclei on. For fluorescence this is " +
                "required (e.g. \"DAPI\"); for brightfield use \"Hematoxylin OD\" or \"Optical density sum\".");
            addNumber(properties, "threshold", "Intensity threshold for the detection channel (default 1.0).");
            addNumber(properties, "requestedPixelSizeMicrons", "Processing resolution in microns (default 0.5).");
            addNumber(properties, "backgroundRadiusMicrons", "Background radius in microns (default 8.0).");
            addNumber(properties, "medianRadiusMicrons", "Median filter radius in microns (default 0.0).");
            addNumber(properties, "sigmaMicrons", "Gaussian sigma in microns (default 1.5).");
            addNumber(properties, "minAreaMicrons", "Minimum nucleus area, microns^2 (default 10.0).");
            addNumber(properties, "maxAreaMicrons", "Maximum nucleus area, microns^2 (default 400.0).");
            addNumber(properties, "cellExpansionMicrons", "Cell expansion from nucleus, microns (default 5.0).");
            addBool(properties, "includeNuclei", "Include the nucleus ROI in each cell (default true).");
            addBool(properties, "makeMeasurements", "Compute per-cell intensity measurements (default true).");

            inputSchema.add("properties", properties);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Run QuPath's watershed cell detection on the selected annotation(s) (or all annotations if " +
                "none are selected). A convenience wrapper over the WatershedCellDetection plugin with typed, " +
                "documented parameters. Returns the detection count before and after so the effect is explicit. " +
                "Requires plugin execution to be enabled.",
                inputSchema,
                null,
                ToolDefinition.Annotations.destructive()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            if (!registry.getConfig().isEnablePluginExecution())
                return ToolResult.error("Plugin execution is disabled in MCP configuration "
                    + "(set QUPATH_MCP_ENABLE_PLUGINS=true).");
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null)
                return ToolResult.error("No QuPath instance available (headless mode)");
            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null)
                return ToolResult.error("No image is currently open");

            PathObjectHierarchy hierarchy = imageData.getHierarchy();

            // Ensure there is a parent to detect within: use the current selection, else all annotations.
            var selection = hierarchy.getSelectionModel();
            if (selection.getSelectedObjects().isEmpty()) {
                var annotations = hierarchy.getAnnotationObjects();
                if (annotations.isEmpty())
                    return ToolResult.error("No annotation to detect within. Create/select an annotation first "
                        + "(e.g. a tumor region or a full-image annotation).");
                selection.setSelectedObjects(new ArrayList<>(annotations), null);
            }

            // Build parameter JSON with defaults.
            JsonObject params = new JsonObject();
            copyOr(params, arguments, "detectionImage", null);
            copyOr(params, arguments, "requestedPixelSizeMicrons", 0.5);
            copyOr(params, arguments, "backgroundRadiusMicrons", 8.0);
            params.addProperty("backgroundByReconstruction", true);
            copyOr(params, arguments, "medianRadiusMicrons", 0.0);
            copyOr(params, arguments, "sigmaMicrons", 1.5);
            copyOr(params, arguments, "minAreaMicrons", 10.0);
            copyOr(params, arguments, "maxAreaMicrons", 400.0);
            copyOr(params, arguments, "threshold", 1.0);
            params.addProperty("watershedPostProcess", true);
            copyOr(params, arguments, "cellExpansionMicrons", 5.0);
            copyOr(params, arguments, "includeNuclei", true);
            params.addProperty("smoothBoundaries", true);
            copyOr(params, arguments, "makeMeasurements", true);

            int before = hierarchy.getDetectionObjects().size();
            boolean ok;
            try {
                ok = QPEx.runPlugin(WATERSHED, imageData, params.toString());
            } catch (Exception e) {
                logger.error("Cell detection failed", e);
                return ToolResult.error("Cell detection failed: " + e.getMessage(), e);
            }
            int after = hierarchy.getDetectionObjects().size();

            JsonObject result = new JsonObject();
            result.addProperty("success", ok);
            result.addProperty("detectionsBefore", before);
            result.addProperty("detectionsAfter", after);
            result.addProperty("added", after - before);
            result.add("parameters", params);
            if (!ok || after == before)
                result.addProperty("warning", "Detection reported no change - check the detectionImage channel "
                    + "name and threshold (fluorescence images need a channel name like \"DAPI\").");
            return ToolResult.success(result);
        }

        private static void addString(JsonObject props, String name, String desc) {
            JsonObject o = new JsonObject();
            o.addProperty("type", "string");
            o.addProperty("description", desc);
            props.add(name, o);
        }

        private static void addNumber(JsonObject props, String name, String desc) {
            JsonObject o = new JsonObject();
            o.addProperty("type", "number");
            o.addProperty("description", desc);
            props.add(name, o);
        }

        private static void addBool(JsonObject props, String name, String desc) {
            JsonObject o = new JsonObject();
            o.addProperty("type", "boolean");
            o.addProperty("description", desc);
            props.add(name, o);
        }

        private static void copyOr(JsonObject target, JsonObject args, String key, Object def) {
            if (args.has(key) && !args.get(key).isJsonNull())
                target.add(key, args.get(key));
            else if (def instanceof Number n)
                target.addProperty(key, n);
            else if (def instanceof Boolean b)
                target.addProperty(key, b);
            // null default => omit (e.g. detectionImage not provided)
        }
    }

    /**
     * Export the measurement table for detections/cells/annotations to a CSV file.
     */
    public static class ExportMeasurements implements McpTool {

        private static final String NAME = "qupath_export_measurements";

        private final McpToolRegistry registry;

        public ExportMeasurements(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");
            JsonObject properties = new JsonObject();

            JsonObject objectType = new JsonObject();
            objectType.addProperty("type", "string");
            objectType.addProperty("description", "Which objects to export: 'detection' (default), 'cell', or " +
                "'annotation'.");
            JsonArray en = new JsonArray();
            en.add("detection");
            en.add("cell");
            en.add("annotation");
            objectType.add("enum", en);
            objectType.addProperty("default", "detection");
            properties.add("objectType", objectType);

            JsonObject path = new JsonObject();
            path.addProperty("type", "string");
            path.addProperty("description", "Output CSV path. A bare directory or relative path resolves under a " +
                "'qupath_measurements' folder in the current project (or the user home). If omitted a dated file " +
                "is generated there. A path ending in '.csv' is used as the exact file name.");
            properties.add("path", path);

            JsonObject overwrite = new JsonObject();
            overwrite.addProperty("type", "boolean");
            overwrite.addProperty("description", "Overwrite if the file exists (default false).");
            overwrite.addProperty("default", false);
            properties.add("overwrite", overwrite);

            inputSchema.add("properties", properties);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Export the QuPath measurement table for detections, cells, or annotations to a CSV file on the " +
                "local computer. Returns the saved path, the column names, and the number of rows. Requires file " +
                "access to be enabled.",
                inputSchema,
                null,
                ToolDefinition.Annotations.destructive()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            if (!registry.getConfig().isEnableFileAccess())
                return ToolResult.error("File access is disabled in MCP configuration "
                    + "(set QUPATH_MCP_ENABLE_FILE_ACCESS=true).");
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null)
                return ToolResult.error("No QuPath instance available (headless mode)");
            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null)
                return ToolResult.error("No image is currently open");

            String objectType = arguments.has("objectType") && !arguments.get("objectType").isJsonNull()
                    ? arguments.get("objectType").getAsString() : "detection";
            boolean overwrite = arguments.has("overwrite") && arguments.get("overwrite").getAsBoolean();

            PathObjectHierarchy hierarchy = imageData.getHierarchy();
            Collection<PathObject> objects;
            switch (objectType) {
                case "annotation" -> objects = new ArrayList<>(hierarchy.getAnnotationObjects());
                case "cell" -> {
                    objects = new ArrayList<>();
                    for (PathObject d : hierarchy.getDetectionObjects())
                        if (d.isCell())
                            objects.add(d);
                }
                default -> objects = new ArrayList<>(hierarchy.getDetectionObjects());
            }
            if (objects.isEmpty())
                return ToolResult.error("No '" + objectType + "' objects to export.");

            try {
                ObservableMeasurementTableData table = new ObservableMeasurementTableData();
                table.setImageData(imageData, objects);
                List<String> columns = table.getAllNames();

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(csv(columns.get(i)));
                }
                sb.append('\n');
                for (PathObject obj : objects) {
                    for (int i = 0; i < columns.size(); i++) {
                        if (i > 0) sb.append(',');
                        sb.append(csv(table.getStringValue(obj, columns.get(i))));
                    }
                    sb.append('\n');
                }

                Path target = resolveCsvPath(qupath, arguments, objectType);
                if (Files.exists(target) && !overwrite)
                    return ToolResult.error("File already exists: " + target.toAbsolutePath()
                        + " (pass overwrite:true).");
                if (target.getParent() != null)
                    Files.createDirectories(target.getParent());
                Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);

                JsonObject result = new JsonObject();
                result.addProperty("saved", target.toAbsolutePath().toString());
                result.addProperty("objectType", objectType);
                result.addProperty("rows", objects.size());
                JsonArray cols = new JsonArray();
                columns.forEach(cols::add);
                result.add("columns", cols);
                return ToolResult.success(result);

            } catch (IOException e) {
                logger.error("Failed to export measurements", e);
                return ToolResult.error("Failed to export measurements: " + e.getMessage(), e);
            }
        }

        private static Path resolveCsvPath(QuPathGUI qupath, JsonObject arguments, String objectType) {
            Path base = Paths.get(System.getProperty("user.home"));
            if (qupath.getProject() != null && qupath.getProject().getPath() != null) {
                Path dir = qupath.getProject().getPath().getParent();
                if (dir != null)
                    base = dir;
            }
            String raw = arguments.has("path") && !arguments.get("path").isJsonNull()
                    ? arguments.get("path").getAsString() : null;
            if (raw != null && raw.toLowerCase().endsWith(".csv")) {
                Path p = Paths.get(raw);
                return p.isAbsolute() ? p : base.resolve(p);
            }
            Path dir;
            if (raw != null && !raw.isBlank()) {
                Path p = Paths.get(raw);
                dir = p.isAbsolute() ? p : base.resolve(p);
            } else {
                dir = base.resolve("qupath_measurements");
            }
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            return dir.resolve("measurements_" + objectType + "_" + ts + ".csv");
        }

        private static String csv(String v) {
            if (v == null)
                return "";
            if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0)
                return "\"" + v.replace("\"", "\"\"") + "\"";
            return v;
        }
    }

    /**
     * Add shape measurements (area, perimeter, circularity, ...) to annotations or detections.
     */
    public static class MeasureAnnotations implements McpTool {

        private static final String NAME = "qupath_measure_annotations";

        private final McpToolRegistry registry;

        public MeasureAnnotations(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");
            JsonObject properties = new JsonObject();

            JsonObject objectType = new JsonObject();
            objectType.addProperty("type", "string");
            objectType.addProperty("description", "Which objects to measure: 'annotation' (default), 'cell', or " +
                "'detection'.");
            JsonArray en = new JsonArray();
            en.add("annotation");
            en.add("cell");
            en.add("detection");
            objectType.add("enum", en);
            objectType.addProperty("default", "annotation");
            properties.add("objectType", objectType);

            JsonObject selectedOnly = new JsonObject();
            selectedOnly.addProperty("type", "boolean");
            selectedOnly.addProperty("description", "Measure only the currently selected objects (default false).");
            selectedOnly.addProperty("default", false);
            properties.add("selectedOnly", selectedOnly);

            inputSchema.add("properties", properties);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Add shape measurements (area, perimeter, circularity, caliper, etc.) to annotations, cells, or " +
                "detections. Intensity measurements are produced by cell detection or qupath_run_plugin; this " +
                "tool covers geometric features. Returns how many objects were measured and the measurement " +
                "columns now present.",
                inputSchema,
                null,
                ToolDefinition.Annotations.write()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null)
                return ToolResult.error("No QuPath instance available (headless mode)");
            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null)
                return ToolResult.error("No image is currently open");

            String objectType = arguments.has("objectType") && !arguments.get("objectType").isJsonNull()
                    ? arguments.get("objectType").getAsString() : "annotation";
            boolean selectedOnly = arguments.has("selectedOnly") && arguments.get("selectedOnly").getAsBoolean();

            PathObjectHierarchy hierarchy = imageData.getHierarchy();
            List<PathObject> objects = new ArrayList<>();
            if (selectedOnly) {
                objects.addAll(hierarchy.getSelectionModel().getSelectedObjects());
            } else {
                switch (objectType) {
                    case "cell" -> {
                        for (PathObject d : hierarchy.getDetectionObjects())
                            if (d.isCell())
                                objects.add(d);
                    }
                    case "detection" -> objects.addAll(hierarchy.getDetectionObjects());
                    default -> objects.addAll(hierarchy.getAnnotationObjects());
                }
            }
            if (objects.isEmpty())
                return ToolResult.error("No '" + objectType + "' objects to measure"
                    + (selectedOnly ? " in the current selection." : "."));

            try {
                QP.addShapeMeasurements(imageData, objects);
                hierarchy.fireHierarchyChangedEvent(this);

                JsonObject result = new JsonObject();
                result.addProperty("measured", objects.size());
                result.addProperty("objectType", selectedOnly ? "selected" : objectType);
                JsonArray cols = new JsonArray();
                objects.get(0).getMeasurementList().getMeasurementNames().forEach(cols::add);
                result.add("measurementColumns", cols);
                return ToolResult.success(result);
            } catch (Exception e) {
                logger.error("Failed to measure objects", e);
                return ToolResult.error("Failed to measure objects: " + e.getMessage(), e);
            }
        }
    }
}
