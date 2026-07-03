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
import qupath.lib.images.ImageData;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import java.util.List;

/**
 * Tools for managing projects and images.
 */
public class ProjectTools {

    private static final Logger logger = LoggerFactory.getLogger(ProjectTools.class);

    /**
     * Get information about the current project.
     */
    public static class GetProjectInfo implements McpTool {

        private static final String NAME = "qupath_get_project_info";

        private final McpToolRegistry registry;

        public GetProjectInfo(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");
            inputSchema.add("properties", new JsonObject());
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Get information about the current QuPath project. " +
                "Returns project name, path, number of images, and other metadata.",
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

            Project<?> project = qupath.getProject();
            if (project == null) {
                return ToolResult.error("No project is currently open. " +
                    "Open a project in QuPath first, or use qupath_open_image to open an individual image.");
            }

            JsonObject result = new JsonObject();
            result.addProperty("name", project.getName());
            result.addProperty("path", project.getPath() != null ? project.getPath().toString() : null);
            result.addProperty("imageCount", project.getImageList().size());

            // Get creation time if available
            if (project.getCreationTimestamp() > 0) {
                result.addProperty("created", project.getCreationTimestamp());
            }
            if (project.getModificationTimestamp() > 0) {
                result.addProperty("modified", project.getModificationTimestamp());
            }

            // List image entries
            JsonArray images = new JsonArray();
            for (ProjectImageEntry<?> entry : project.getImageList()) {
                JsonObject img = new JsonObject();
                img.addProperty("id", entry.getID());
                img.addProperty("name", entry.getImageName());
                if (entry.getDescription() != null && !entry.getDescription().isEmpty()) {
                    img.addProperty("description", entry.getDescription());
                }
                images.add(img);
            }
            result.add("images", images);

            return ToolResult.success(result);
        }
    }

    /**
     * List images in the current project.
     */
    public static class ListImages implements McpTool {

        private static final String NAME = "qupath_list_images";

        private final McpToolRegistry registry;

        public ListImages(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");

            JsonObject properties = new JsonObject();

            JsonObject filter = new JsonObject();
            filter.addProperty("type", "string");
            filter.addProperty("description", "Optional name filter pattern (case-insensitive substring match)");
            properties.add("filter", filter);

            JsonObject limit = new JsonObject();
            limit.addProperty("type", "integer");
            limit.addProperty("description", "Maximum number of images to return (default: 50, max: 500)");
            limit.addProperty("default", 50);
            properties.add("limit", limit);

            JsonObject offset = new JsonObject();
            offset.addProperty("type", "integer");
            offset.addProperty("description", "Number of images to skip for pagination");
            offset.addProperty("default", 0);
            properties.add("offset", offset);

            inputSchema.add("properties", properties);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "List images in the current project. " +
                "Returns image names, IDs, and basic metadata.",
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

            Project<?> project = qupath.getProject();
            if (project == null) {
                return ToolResult.error("No project is currently open");
            }

            String filter = arguments.has("filter") && !arguments.get("filter").isJsonNull()
                    ? arguments.get("filter").getAsString().toLowerCase() : null;
            int limit = arguments.has("limit") ? Math.min(arguments.get("limit").getAsInt(), 500) : 50;
            int offset = arguments.has("offset") ? arguments.get("offset").getAsInt() : 0;

            List<? extends ProjectImageEntry<?>> images = project.getImageList();

            // Apply filter
            if (filter != null && !filter.isEmpty()) {
                images = images.stream()
                        .filter(img -> img.getImageName().toLowerCase().contains(filter))
                        .toList();
            }

            int total = images.size();

            // Apply pagination
            List<? extends ProjectImageEntry<?>> pagedImages = images.stream()
                    .skip(offset)
                    .limit(limit)
                    .toList();

            JsonArray imagesArray = new JsonArray();
            for (ProjectImageEntry<?> entry : pagedImages) {
                JsonObject img = new JsonObject();
                img.addProperty("id", entry.getID());
                img.addProperty("name", entry.getImageName());
                img.addProperty("hasData", entry.hasImageData());

                if (entry.getDescription() != null && !entry.getDescription().isEmpty()) {
                    img.addProperty("description", entry.getDescription());
                }

                imagesArray.add(img);
            }

            JsonObject result = new JsonObject();
            result.addProperty("total", total);
            result.addProperty("count", pagedImages.size());
            result.addProperty("offset", offset);
            result.addProperty("hasMore", offset + pagedImages.size() < total);
            result.add("images", imagesArray);

            return ToolResult.success(result);
        }
    }

    /**
     * Open an image in QuPath.
     */
    public static class OpenImage implements McpTool {

        private static final Logger logger = LoggerFactory.getLogger(OpenImage.class);
        private static final String NAME = "qupath_open_image";

        private final McpToolRegistry registry;

        public OpenImage(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");

            JsonObject properties = new JsonObject();

            JsonObject path = new JsonObject();
            path.addProperty("type", "string");
            path.addProperty("description", "Image file path or URL");
            properties.add("path", path);

            JsonObject entryId = new JsonObject();
            entryId.addProperty("type", "string");
            entryId.addProperty("description", "Project entry ID (alternative to path)");
            properties.add("entryId", entryId);

            inputSchema.add("properties", properties);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Open an image in QuPath. " +
                "Either specify a file path/URL, or a project entry ID to open an image from the current project.",
                inputSchema,
                null,
                ToolDefinition.Annotations.write()
            );
        }

        @Override
        public ToolResult execute(JsonObject arguments) {
            QuPathGUI qupath = registry.getQuPath();
            if (qupath == null) {
                return ToolResult.error("No QuPath instance available (headless mode)");
            }

            try {
                String path = arguments.has("path") && !arguments.get("path").isJsonNull()
                        ? arguments.get("path").getAsString() : null;
                String entryId = arguments.has("entryId") && !arguments.get("entryId").isJsonNull()
                        ? arguments.get("entryId").getAsString() : null;

                // Open by project entry ID
                if (entryId != null && !entryId.isEmpty()) {
                    Project<?> project = qupath.getProject();
                    if (project == null) {
                        return ToolResult.error("No project is open. Cannot open image by entry ID.");
                    }

                    ProjectImageEntry<?> entry = null;
                    for (ProjectImageEntry<?> e : project.getImageList()) {
                        if (e.getID().equals(entryId)) {
                            entry = e;
                            break;
                        }
                    }

                    if (entry == null) {
                        return ToolResult.error("Project entry not found: " + entryId);
                    }

                    // Use QuPathGUI's openImageEntry method
                    @SuppressWarnings("unchecked")
                    boolean success = qupath.openImageEntry((ProjectImageEntry)entry);
                    if (!success) {
                        return ToolResult.error("Failed to open image entry: " + entryId);
                    }

                    ImageData<?> imageData = qupath.getImageData();
                    JsonObject result = new JsonObject();
                    result.addProperty("success", true);
                    result.addProperty("path", imageData.getServer().getPath());
                    result.addProperty("width", imageData.getServer().getWidth());
                    result.addProperty("height", imageData.getServer().getHeight());
                    result.addProperty("message", "Image opened successfully");

                    return ToolResult.success(result);

                }
                // Open by path
                else if (path != null && !path.isEmpty()) {
                    // Try to open as project image first if project exists
                    Project<?> project = qupath.getProject();
                    if (project != null) {
                        for (ProjectImageEntry<?> entry : project.getImageList()) {
                            if (entry.getImageName().equals(path) || entry.getID().equals(path)) {
                                @SuppressWarnings("unchecked")
                                boolean success = qupath.openImageEntry((ProjectImageEntry)entry);
                                if (success) {
                                    ImageData<?> imageData = qupath.getImageData();
                                    JsonObject result = new JsonObject();
                                    result.addProperty("success", true);
                                    result.addProperty("path", imageData.getServer().getPath());
                                    result.addProperty("width", imageData.getServer().getWidth());
                                    result.addProperty("height", imageData.getServer().getHeight());
                                    result.addProperty("message", "Image opened successfully");
                                    return ToolResult.success(result);
                                }
                            }
                        }
                    }

                    // If not found in project, open directly using openImage
                    boolean success = qupath.openImage(qupath.getViewer(), path);
                    if (!success) {
                        return ToolResult.error("Failed to open image: " + path);
                    }

                    ImageData<?> imageData = qupath.getImageData();
                    JsonObject result = new JsonObject();
                    result.addProperty("success", true);
                    result.addProperty("path", imageData.getServer().getPath());
                    result.addProperty("width", imageData.getServer().getWidth());
                    result.addProperty("height", imageData.getServer().getHeight());
                    result.addProperty("message", "Image opened successfully");

                    return ToolResult.success(result);
                } else {
                    return ToolResult.error("Either 'path' or 'entryId' must be specified");
                }

            } catch (Exception e) {
                logger.error("Failed to open image", e);
                return ToolResult.error("Failed to open image: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Save the current image data.
     */
    public static class SaveData implements McpTool {

        private static final Logger logger = LoggerFactory.getLogger(SaveData.class);
        private static final String NAME = "qupath_save_data";

        private final McpToolRegistry registry;

        public SaveData(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");
            inputSchema.add("properties", new JsonObject());
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Save the current image data (annotations, detections, etc.) to the project. " +
                "Requires an image to be open from a project.",
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
                Project<?> project = qupath.getProject();
                if (project == null) {
                    return ToolResult.error("No project is open. Data can only be saved within a project.");
                }

                // Find the project entry for the current image
                String serverPath = imageData.getServer().getPath();
                ProjectImageEntry<?> foundEntry = null;
                for (ProjectImageEntry<?> entry : project.getImageList()) {
                    // Check if this entry matches the current image by ID
                    if (entry.getID() != null && serverPath.contains(entry.getID())) {
                        foundEntry = entry;
                        break;
                    }
                }

                if (foundEntry == null) {
                    return ToolResult.error("Current image is not part of the project. Cannot save.");
                }

                // Save the image data - need to handle generics carefully
                saveImageDataSafely(foundEntry, imageData);

                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("message", "Image data saved successfully");
                result.addProperty("entryId", foundEntry.getID());

                return ToolResult.success(result);

            } catch (Exception e) {
                logger.error("Failed to save image data", e);
                return ToolResult.error("Failed to save image data: " + e.getMessage(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void saveImageDataSafely(ProjectImageEntry<T> entry, ImageData<?> imageData) throws java.io.IOException {
        entry.saveImageData((ImageData<T>) imageData);
    }
}
