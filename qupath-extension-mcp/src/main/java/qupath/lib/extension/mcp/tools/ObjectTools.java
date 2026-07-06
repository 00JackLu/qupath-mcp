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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.extension.mcp.protocol.models.ToolDefinition;
import qupath.lib.extension.mcp.protocol.models.ToolResult;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.io.GsonTools;
import qupath.lib.io.PathIO;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tools for managing objects and ROIs.
 */
public class ObjectTools {

    private static final Logger logger = LoggerFactory.getLogger(ObjectTools.class);
    private static final Gson GSON = GsonTools.getInstance();

    /**
     * List objects in the current image hierarchy.
     */
    public static class ListObjects implements McpTool {

        private static final String NAME = "qupath_list_objects";

        private final McpToolRegistry registry;

        public ListObjects(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");

            JsonObject properties = new JsonObject();

            JsonObject type = new JsonObject();
            type.addProperty("type", "string");
            type.addProperty("description", "Filter by object type");
            type.addProperty("enum", GSON.toJson(new String[]{"ALL", "ANNOTATIONS", "DETECTIONS", "CELLS", "TILES", "TMA"}));
            type.addProperty("default", "ALL");
            properties.add("type", type);

            JsonObject filter = new JsonObject();
            filter.addProperty("type", "string");
            filter.addProperty("description", "Filter by classification name (optional)");
            properties.add("filter", filter);

            JsonObject limit = new JsonObject();
            limit.addProperty("type", "integer");
            limit.addProperty("description", "Maximum number of objects to return (default: 100, max: 1000)");
            limit.addProperty("default", 100);
            limit.addProperty("maximum", 1000);
            properties.add("limit", limit);

            JsonObject offset = new JsonObject();
            offset.addProperty("type", "integer");
            offset.addProperty("description", "Number of objects to skip for pagination");
            offset.addProperty("default", 0);
            properties.add("offset", offset);

            inputSchema.add("properties", properties);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "List objects in the current image hierarchy. " +
                "Returns objects filtered by type (annotations, detections, cells, tiles, TMA cores). " +
                "Each object includes ID, type, classification, and basic measurements.",
                inputSchema,
                null,
                ToolDefinition.Annotations.readOnly()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null) {
                return ToolResult.error("No QuPath instance available (headless mode)");
            }

            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null) {
                return ToolResult.error("No image is currently open");
            }

            PathObjectHierarchy hierarchy = imageData.getHierarchy();

            String typeFilter = arguments.has("type") ? arguments.get("type").getAsString() : "ALL";
            String classFilter = arguments.has("filter") && !arguments.get("filter").isJsonNull()
                    ? arguments.get("filter").getAsString() : null;
            int limit = arguments.has("limit") ? Math.min(arguments.get("limit").getAsInt(), 1000) : 100;
            int offset = arguments.has("offset") ? arguments.get("offset").getAsInt() : 0;

            // Get objects by type
            Collection<PathObject> objects;
            switch (typeFilter) {
                case "ANNOTATIONS":
                    objects = hierarchy.getObjects(null, PathAnnotationObject.class);
                    break;
                case "DETECTIONS":
                    objects = hierarchy.getObjects(null, PathDetectionObject.class);
                    break;
                case "CELLS":
                    objects = hierarchy.getObjects(null, PathCellObject.class);
                    break;
                case "TILES":
                    objects = hierarchy.getObjects(null, PathTileObject.class);
                    break;
                case "TMA":
                    objects = hierarchy.getObjects(null, TMACoreObject.class);
                    break;
                default:
                    objects = hierarchy.getObjects(null, PathObject.class);
            }

            // Filter by classification if specified
            if (classFilter != null && !classFilter.isEmpty()) {
                objects = objects.stream()
                        .filter(obj -> obj.getPathClass() != null &&
                                      obj.getPathClass().getName().contains(classFilter))
                        .toList();
            }

            int total = objects.size();

            // Apply pagination
            List<PathObject> pagedObjects = objects.stream()
                    .skip(offset)
                    .limit(limit)
                    .toList();

            // Build response
            JsonObject result = new JsonObject();
            result.addProperty("total", total);
            result.addProperty("count", pagedObjects.size());
            result.addProperty("offset", offset);
            result.addProperty("hasMore", offset + pagedObjects.size() < total);

            JsonArray objectsArray = new JsonArray();
            for (PathObject obj : pagedObjects) {
                objectsArray.add(summarizeObject(obj));
            }
            result.add("objects", objectsArray);

            return ToolResult.success(result);
        }

        private JsonObject summarizeObject(PathObject obj) {
            JsonObject json = new JsonObject();
            json.addProperty("id", obj.getID().toString());
            json.addProperty("type", getObjectTypeName(obj));

            if (obj.getPathClass() != null) {
                json.addProperty("class", obj.getPathClass().getName());
            }

            ROI roi = obj.getROI();
            if (roi != null) {
                JsonObject roiJson = new JsonObject();
                roiJson.addProperty("type", roi.getRoiType().toString());
                roiJson.addProperty("x", roi.getBoundsX());
                roiJson.addProperty("y", roi.getBoundsY());
                roiJson.addProperty("width", roi.getBoundsWidth());
                roiJson.addProperty("height", roi.getBoundsHeight());
                json.add("roi", roiJson);
            }

            // Include measurement count
            if (obj.getMeasurementList() != null && obj.getMeasurementList().size() > 0) {
                json.addProperty("measurementCount", obj.getMeasurementList().size());
            }

            return json;
        }
    }

    /**
     * Get detailed information about a specific object.
     */
    public static class GetObjectDetails implements McpTool {

        private static final String NAME = "qupath_get_object_details";

        private final McpToolRegistry registry;

        public GetObjectDetails(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");

            JsonObject properties = new JsonObject();

            JsonObject objectId = new JsonObject();
            objectId.addProperty("type", "string");
            objectId.addProperty("description", "Object UUID");
            properties.add("objectId", objectId);

            JsonObject includeMeasurements = new JsonObject();
            includeMeasurements.addProperty("type", "boolean");
            includeMeasurements.addProperty("description", "Include all measurements (default: true)");
            includeMeasurements.addProperty("default", true);
            properties.add("includeMeasurements", includeMeasurements);

            JsonObject includeROI = new JsonObject();
            includeROI.addProperty("type", "boolean");
            includeROI.addProperty("description", "Include full ROI geometry (default: true)");
            includeROI.addProperty("default", true);
            properties.add("includeROI", includeROI);

            inputSchema.add("properties", properties);

            JsonArray required = new JsonArray();
            required.add("objectId");
            inputSchema.add("required", required);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Get detailed information about a specific object including ROI geometry, " +
                "measurements, classification, and parent/child relationships.",
                inputSchema,
                null,
                ToolDefinition.Annotations.readOnly()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null) {
                return ToolResult.error("No QuPath instance available (headless mode)");
            }

            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null) {
                return ToolResult.error("No image is currently open");
            }

            String objectId = arguments.get("objectId").getAsString();
            boolean includeMeasurements = !arguments.has("includeMeasurements") ||
                                          arguments.get("includeMeasurements").getAsBoolean();
            boolean includeROI = !arguments.has("includeROI") ||
                                 arguments.get("includeROI").getAsBoolean();

            // Find the object by ID
            PathObjectHierarchy hierarchy = imageData.getHierarchy();
            PathObject target = findObjectById(hierarchy, objectId);

            if (target == null) {
                return ToolResult.error("Object not found: " + objectId);
            }

            // Build detailed response using GsonTools for proper serialization.
            // Serialize via a String rather than toJsonTree(): QuPath's PathObject GeoJSON
            // adapter writes raw JSON with JsonWriter.jsonValue(), which Gson's JsonTreeWriter
            // does not support (it throws UnsupportedOperationException).
            JsonObject result = GSON.fromJson(GSON.toJson(target), JsonObject.class);

            // Remove potentially large ROI geometry if not requested
            if (!includeROI && result.has("roi")) {
                result.remove("roi");
            }

            // Ensure measurements are included if requested
            if (includeMeasurements && target.getMeasurementList() != null) {
                JsonObject measurements = new JsonObject();
                for (var entry : target.getMeasurements().entrySet()) {
                    measurements.addProperty(entry.getKey(), entry.getValue().doubleValue());
                }
                result.add("measurements", measurements);
            }

            return ToolResult.success(result);
        }
    }

    /**
     * Create a new annotation.
     */
    public static class CreateAnnotation implements McpTool {

        private static final String NAME = "qupath_create_annotation";

        private final McpToolRegistry registry;

        public CreateAnnotation(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");

            JsonObject properties = new JsonObject();

            JsonObject type = new JsonObject();
            type.addProperty("type", "string");
            type.addProperty("description", "ROI type");
            type.add("enum", GSON.toJsonTree(new String[]{"rectangle", "ellipse", "line", "polygon", "points"}));
            properties.add("type", type);

            JsonObject coordinates = new JsonObject();
            coordinates.addProperty("type", "array");
            coordinates.addProperty("description", "ROI coordinates. For rectangle/ellipse: [x, y, width, height]. " +
                "For line: [x1, y1, x2, y2]. For polygon/points: [[x1, y1], [x2, y2], ...]");
            JsonObject items = new JsonObject();
            items.addProperty("type", "number");
            coordinates.add("items", items);
            properties.add("coordinates", coordinates);

            JsonObject classification = new JsonObject();
            classification.addProperty("type", "string");
            classification.addProperty("description", "Classification name (optional)");
            properties.add("classification", classification);

            JsonObject z = new JsonObject();
            z.addProperty("type", "integer");
            z.addProperty("description", "Z-slice index (default: 0)");
            z.addProperty("default", 0);
            properties.add("z", z);

            JsonObject t = new JsonObject();
            t.addProperty("type", "integer");
            t.addProperty("description", "Timepoint index (default: 0)");
            t.addProperty("default", 0);
            properties.add("t", t);

            inputSchema.add("properties", properties);

            JsonArray required = new JsonArray();
            required.add("type");
            required.add("coordinates");
            inputSchema.add("required", required);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Create a new annotation object with the specified ROI. " +
                "Returns the ID of the created annotation.",
                inputSchema,
                null,
                ToolDefinition.Annotations.destructive()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null) {
                return ToolResult.error("No QuPath instance available (headless mode)");
            }

            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null) {
                return ToolResult.error("No image is currently open");
            }

            // Validate required 'type'
            if (!arguments.has("type") || arguments.get("type").isJsonNull()) {
                return ToolResult.error("Missing required parameter 'type' " +
                    "(one of: rectangle, ellipse, line, polygon, points)");
            }
            String roiType = arguments.get("type").getAsString();
            String lower = roiType.toLowerCase();
            if (!List.of("rectangle", "ellipse", "line", "polygon", "points").contains(lower)) {
                return ToolResult.error("Invalid 'type': '" + roiType + "'. " +
                    "Must be one of: rectangle, ellipse, line, polygon, points");
            }

            // Validate required 'coordinates'
            if (!arguments.has("coordinates") || !arguments.get("coordinates").isJsonArray()) {
                return ToolResult.error("Missing required parameter 'coordinates' (an array). " +
                    "For rectangle/ellipse use [x, y, width, height]; for line use [x1, y1, x2, y2]; " +
                    "for polygon/points use [[x1, y1], [x2, y2], ...]");
            }
            JsonArray coords = arguments.getAsJsonArray("coordinates");

            // Shape-specific coordinate checks
            if (lower.equals("rectangle") || lower.equals("ellipse")) {
                if (coords.size() < 4) {
                    return ToolResult.error("'" + roiType + "' needs 4 coordinates " +
                        "[x, y, width, height], got " + coords.size());
                }
            } else if (lower.equals("line")) {
                if (coords.size() < 4) {
                    return ToolResult.error("'line' needs 4 coordinates [x1, y1, x2, y2], got " + coords.size());
                }
            } else { // polygon or points
                int min = lower.equals("polygon") ? 3 : 1;
                if (coords.size() < min) {
                    return ToolResult.error("'" + roiType + "' needs at least " + min +
                        " [x, y] point pair(s), got " + coords.size());
                }
                for (int i = 0; i < coords.size(); i++) {
                    if (!coords.get(i).isJsonArray() || coords.get(i).getAsJsonArray().size() < 2) {
                        return ToolResult.error("'" + roiType + "' coordinates must be [x, y] pairs, " +
                            "e.g. [[x1, y1], [x2, y2], ...]; element " + i + " is not a valid pair");
                    }
                }
            }

            String classification = arguments.has("classification") &&
                                   !arguments.get("classification").isJsonNull()
                                   ? arguments.get("classification").getAsString() : null;
            int z = arguments.has("z") ? arguments.get("z").getAsInt() : 0;
            int t = arguments.has("t") ? arguments.get("t").getAsInt() : 0;

            try {
                ImagePlane plane = ImagePlane.getPlane(z, t);
                ROI roi = createROI(roiType, coords, plane);

                if (roi == null) {
                    return ToolResult.error("Unknown ROI type: " + roiType);
                }

                // Create annotation
                PathObject annotation = PathObjects.createAnnotationObject(roi);

                // Set classification if specified
                if (classification != null && !classification.isEmpty()) {
                    annotation.setPathClass(PathClass.fromString(classification));
                }

                // Add to hierarchy
                imageData.getHierarchy().addObject(annotation);

                JsonObject result = new JsonObject();
                result.addProperty("id", annotation.getID().toString());
                result.addProperty("type", "annotation");
                result.addProperty("message", "Annotation created successfully");

                return ToolResult.success(result);

            } catch (Exception e) {
                logger.error("Failed to create annotation", e);
                return ToolResult.error("Failed to create annotation: " + e.getMessage(), e);
            }
        }

        private ROI createROI(String type, JsonArray coords, qupath.lib.regions.ImagePlane plane) {
            switch (type.toLowerCase()) {
                case "rectangle":
                    return ROIs.createRectangleROI(
                        coords.get(0).getAsDouble(),
                        coords.get(1).getAsDouble(),
                        coords.get(2).getAsDouble(),
                        coords.get(3).getAsDouble(),
                        plane
                    );
                case "ellipse":
                    return ROIs.createEllipseROI(
                        coords.get(0).getAsDouble(),
                        coords.get(1).getAsDouble(),
                        coords.get(2).getAsDouble(),
                        coords.get(3).getAsDouble(),
                        plane
                    );
                case "line":
                    return ROIs.createLineROI(
                        coords.get(0).getAsDouble(),
                        coords.get(1).getAsDouble(),
                        coords.get(2).getAsDouble(),
                        coords.get(3).getAsDouble(),
                        plane
                    );
                case "polygon":
                case "points":
                    List<qupath.lib.geom.Point2> points = new ArrayList<>();
                    for (int i = 0; i < coords.size(); i++) {
                        JsonArray pt = coords.get(i).getAsJsonArray();
                        points.add(new qupath.lib.geom.Point2(
                            pt.get(0).getAsDouble(),
                            pt.get(1).getAsDouble()
                        ));
                    }
                    if (type.equalsIgnoreCase("polygon")) {
                        return ROIs.createPolygonROI(points, plane);
                    } else {
                        return ROIs.createPointsROI(points, plane);
                    }
                default:
                    return null;
            }
        }
    }

    /**
     * Delete objects from the hierarchy.
     */
    public static class DeleteObjects implements McpTool {

        private static final String NAME = "qupath_delete_objects";

        private final McpToolRegistry registry;

        public DeleteObjects(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");

            JsonObject properties = new JsonObject();

            JsonObject objectIds = new JsonObject();
            objectIds.addProperty("type", "array");
            objectIds.addProperty("description", "Array of object UUIDs to delete");
            JsonObject items = new JsonObject();
            items.addProperty("type", "string");
            objectIds.add("items", items);
            properties.add("objectIds", objectIds);

            JsonObject filter = new JsonObject();
            filter.addProperty("type", "string");
            filter.addProperty("description", "Delete all objects of this type (ALL, ANNOTATIONS, DETECTIONS, CELLS, TILES)");
            filter.add("enum", GSON.toJsonTree(new String[]{"ALL", "ANNOTATIONS", "DETECTIONS", "CELLS", "TILES"}));
            properties.add("filter", filter);

            inputSchema.add("properties", properties);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Delete objects from the hierarchy. " +
                "Either specify objectIds to delete specific objects, " +
                "or use filter to delete all objects of a type. " +
                "WARNING: This action cannot be undone.",
                inputSchema,
                null,
                ToolDefinition.Annotations.destructive()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null) {
                return ToolResult.error("No QuPath instance available (headless mode)");
            }

            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null) {
                return ToolResult.error("No image is currently open");
            }

            PathObjectHierarchy hierarchy = imageData.getHierarchy();
            int deletedCount = 0;

            try {
                // Delete by specific IDs
                if (arguments.has("objectIds") && !arguments.get("objectIds").isJsonNull()) {
                    JsonArray ids = arguments.getAsJsonArray("objectIds");
                    for (JsonElement idElem : ids) {
                        String id = idElem.getAsString();
                        PathObject obj = findObjectById(hierarchy, id);
                        if (obj != null) {
                            hierarchy.removeObject(obj, true);
                            deletedCount++;
                        }
                    }
                }
                // Delete by filter
                else if (arguments.has("filter") && !arguments.get("filter").isJsonNull()) {
                    String filter = arguments.get("filter").getAsString();
                    Collection<PathObject> toDelete;

                    switch (filter) {
                        case "ANNOTATIONS":
                            toDelete = hierarchy.getObjects(null, PathAnnotationObject.class);
                            break;
                        case "DETECTIONS":
                            toDelete = hierarchy.getObjects(null, PathDetectionObject.class);
                            break;
                        case "CELLS":
                            toDelete = hierarchy.getObjects(null, PathCellObject.class);
                            break;
                        case "TILES":
                            toDelete = hierarchy.getObjects(null, PathTileObject.class);
                            break;
                        case "ALL":
                            toDelete = new ArrayList<>(hierarchy.getObjects(null, PathObject.class));
                            // Remove root object
                            toDelete.remove(hierarchy.getRootObject());
                            break;
                        default:
                            return ToolResult.error("Unknown filter type: " + filter);
                    }

                    hierarchy.removeObjects(toDelete, true);
                    deletedCount = toDelete.size();
                } else {
                    return ToolResult.error("Either objectIds or filter must be specified");
                }

                JsonObject result = new JsonObject();
                result.addProperty("deletedCount", deletedCount);
                result.addProperty("message", "Deleted " + deletedCount + " object(s)");

                return ToolResult.success(result);

            } catch (Exception e) {
                logger.error("Failed to delete objects", e);
                return ToolResult.error("Failed to delete objects: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Import objects from GeoJSON.
     */
    public static class ImportGeoJson implements McpTool {

        private static final String NAME = "qupath_import_geojson";

        private final McpToolRegistry registry;

        public ImportGeoJson(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");

            JsonObject properties = new JsonObject();

            JsonObject geojson = new JsonObject();
            geojson.addProperty("type", "object");
            geojson.addProperty("description", "GeoJSON FeatureCollection or Feature to import");
            properties.add("geojson", geojson);

            inputSchema.add("properties", properties);

            JsonArray required = new JsonArray();
            required.add("geojson");
            inputSchema.add("required", required);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Import objects from GeoJSON format. " +
                "Accepts FeatureCollection or individual Features. " +
                "Returns the number of objects imported.",
                inputSchema,
                null,
                ToolDefinition.Annotations.destructive()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null) {
                return ToolResult.error("No QuPath instance available (headless mode)");
            }

            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null) {
                return ToolResult.error("No image is currently open");
            }

            try {
                JsonObject geojson = arguments.getAsJsonObject("geojson");
                String geojsonStr = GSON.toJson(geojson);

                // Use GsonTools to parse GeoJSON
                Collection<PathObject> objects = GsonTools.parseObjectsFromGeoJSON(geojsonStr);

                // Add to hierarchy
                imageData.getHierarchy().addObjects(objects);

                JsonObject result = new JsonObject();
                result.addProperty("importedCount", objects.size());
                result.addProperty("message", "Imported " + objects.size() + " object(s) from GeoJSON");

                return ToolResult.success(result);

            } catch (Exception e) {
                logger.error("Failed to import GeoJSON", e);
                return ToolResult.error("Failed to import GeoJSON: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Export objects as GeoJSON.
     */
    public static class ExportGeoJson implements McpTool {

        private static final String NAME = "qupath_export_geojson";

        private final McpToolRegistry registry;

        public ExportGeoJson(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");

            JsonObject properties = new JsonObject();

            JsonObject type = new JsonObject();
            type.addProperty("type", "string");
            type.addProperty("description", "Type of objects to export");
            type.add("enum", GSON.toJsonTree(new String[]{"ALL", "ANNOTATIONS", "DETECTIONS", "CELLS"}));
            type.addProperty("default", "ANNOTATIONS");
            properties.add("type", type);

            JsonObject includeMeasurements = new JsonObject();
            includeMeasurements.addProperty("type", "boolean");
            includeMeasurements.addProperty("description", "Include measurements in properties (default: true)");
            includeMeasurements.addProperty("default", true);
            properties.add("includeMeasurements", includeMeasurements);

            inputSchema.add("properties", properties);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Export objects as GeoJSON format. " +
                "Returns a GeoJSON FeatureCollection with all selected objects.",
                inputSchema,
                null,
                ToolDefinition.Annotations.readOnly()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null) {
                return ToolResult.error("No QuPath instance available (headless mode)");
            }

            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null) {
                return ToolResult.error("No image is currently open");
            }

            String type = arguments.has("type") ? arguments.get("type").getAsString() : "ANNOTATIONS";
            boolean includeMeasurements = !arguments.has("includeMeasurements") ||
                                          arguments.get("includeMeasurements").getAsBoolean();

            try {
                PathObjectHierarchy hierarchy = imageData.getHierarchy();

                Collection<PathObject> objects;
                switch (type) {
                    case "ANNOTATIONS":
                        objects = hierarchy.getObjects(null, PathAnnotationObject.class);
                        break;
                    case "DETECTIONS":
                        objects = hierarchy.getObjects(null, PathDetectionObject.class);
                        break;
                    case "CELLS":
                        objects = hierarchy.getObjects(null, PathCellObject.class);
                        break;
                    default:
                        objects = hierarchy.getObjects(null, PathObject.class);
                        objects = new ArrayList<>(objects);
                        ((List<PathObject>) objects).remove(hierarchy.getRootObject());
                }

                // Export to GeoJSON using GsonTools. A collection of PathObjects serializes to a
                // JSON array of Features, so wrap it in a GeoJSON FeatureCollection (parsing it as a
                // JsonObject directly would fail with "Expected JsonObject but was JsonArray").
                JsonArray features = GSON.fromJson(GSON.toJson(objects), JsonArray.class);

                JsonObject featureCollection = new JsonObject();
                featureCollection.addProperty("type", "FeatureCollection");
                featureCollection.add("features", features);

                JsonObject result = new JsonObject();
                result.addProperty("count", objects.size());
                result.add("geojson", featureCollection);

                return ToolResult.success(result);

            } catch (Exception e) {
                logger.error("Failed to export GeoJSON", e);
                return ToolResult.error("Failed to export GeoJSON: " + e.getMessage(), e);
            }
        }
    }

    // Helper methods

    private static String getObjectTypeName(PathObject obj) {
        if (obj.isAnnotation()) return "annotation";
        if (obj.isCell()) return "cell";
        if (obj.isDetection()) return "detection";
        if (obj.isTile()) return "tile";
        if (obj.isTMACore()) return "tma_core";
        return "unknown";
    }

    private static PathObject findObjectById(PathObjectHierarchy hierarchy, String id) {
        try {
            UUID uuid = UUID.fromString(id);
            for (PathObject obj : hierarchy.getObjects(null, PathObject.class)) {
                if (obj.getID().equals(uuid)) {
                    return obj;
                }
            }
        } catch (IllegalArgumentException e) {
            // Invalid UUID format
        }
        return null;
    }
}
