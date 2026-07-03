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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.extension.mcp.protocol.McpProtocolHandler.McpException;
import qupath.lib.extension.mcp.protocol.models.ToolDefinition;
import qupath.lib.extension.mcp.protocol.models.ToolResult;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.regions.RegionRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Tools for querying image data.
 */
public class ImageTools {

    private static final Logger logger = LoggerFactory.getLogger(ImageTools.class);
    private static final Gson GSON = new Gson();

    /**
     * Get information about the currently open image.
     */
    public static class GetImageInfo implements McpTool {

        private static final String NAME = "qupath_get_image_info";

        private final McpToolRegistry registry;

        public GetImageInfo(McpToolRegistry registry) {
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
                "Get information about the currently open image in QuPath. " +
                "Returns dimensions, channels, calibration, image type, and server type. " +
                "Use this to understand the basic properties of the current image.",
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
                return ToolResult.error("No image is currently open. Use qupath_open_image to open an image first.");
            }

            ImageServer<?> server = imageData.getServer();

            JsonObject result = new JsonObject();
            result.addProperty("path", server.getPath());
            result.addProperty("width", server.getWidth());
            result.addProperty("height", server.getHeight());
            result.addProperty("nChannels", server.nChannels());
            result.addProperty("nZSlices", server.nZSlices());
            result.addProperty("nTimepoints", server.nTimepoints());
            result.addProperty("isRGB", server.isRGB());

            // Pixel type
            result.addProperty("pixelType", server.getPixelType().toString());

            // Calibration
            PixelCalibration cal = server.getPixelCalibration();
            JsonObject calibration = new JsonObject();
            calibration.addProperty("pixelWidthMicrons", cal.getPixelWidthMicrons());
            calibration.addProperty("pixelHeightMicrons", cal.getPixelHeightMicrons());
            if (cal.hasZSpacingMicrons()) {
                calibration.addProperty("zSpacingMicrons", cal.getZSpacingMicrons());
            }
            result.add("calibration", calibration);

            // Image type
            if (imageData.getImageType() != null) {
                result.addProperty("imageType", imageData.getImageType().toString());
            }

            // Server type
            result.addProperty("serverType", server.getServerType().toString());

            // Available resolutions (pyramid levels)
            JsonArray resolutions = new JsonArray();
            for (int i = 0; i < server.nResolutions(); i++) {
                JsonObject level = new JsonObject();
                level.addProperty("level", i);
                level.addProperty("downsample", server.getDownsampleForResolution(i));
                resolutions.add(level);
            }
            result.add("resolutions", resolutions);

            // Channels
            JsonArray channels = new JsonArray();
            for (int c = 0; c < server.nChannels(); c++) {
                JsonObject channel = new JsonObject();
                channel.addProperty("index", c);
                channel.addProperty("name", server.getChannel(c).getName());
                channels.add(channel);
            }
            result.add("channels", channels);

            return ToolResult.success(result);
        }
    }

    /**
     * Get detailed metadata from the image server.
     */
    public static class GetImageMetadata implements McpTool {

        private static final String NAME = "qupath_get_image_metadata";

        private final McpToolRegistry registry;

        public GetImageMetadata(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");
            JsonObject properties = new JsonObject();
            JsonObject keys = new JsonObject();
            keys.addProperty("type", "array");
            keys.addProperty("description", "Specific metadata keys to retrieve (optional, returns all if not specified)");
            JsonObject items = new JsonObject();
            items.addProperty("type", "string");
            keys.add("items", items);
            properties.add("keys", keys);
            inputSchema.add("properties", properties);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Get detailed metadata from the image server. " +
                "Returns metadata like acquisition date, objective, magnification, etc. " +
                "Optionally filter by specific keys.",
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

            ImageServer<?> server = imageData.getServer();
            ImageServerMetadata metadata = server.getMetadata();

            JsonObject result = new JsonObject();

            // Basic metadata
            result.addProperty("name", metadata.getName());

            // All available metadata from the server
            JsonObject allMetadata = new JsonObject();
            allMetadata.addProperty("width", server.getWidth());
            allMetadata.addProperty("height", server.getHeight());
            allMetadata.addProperty("nChannels", server.nChannels());
            allMetadata.addProperty("nZSlices", server.nZSlices());
            allMetadata.addProperty("nTimepoints", server.nTimepoints());
            allMetadata.addProperty("pixelType", server.getPixelType().toString());
            allMetadata.addProperty("channelType", metadata.getChannelType().toString());
            allMetadata.addProperty("isRGB", server.isRGB());

            // Pixel calibration
            PixelCalibration cal = server.getPixelCalibration();
            JsonObject calibration = new JsonObject();
            calibration.addProperty("pixelWidthMicrons", cal.getPixelWidthMicrons());
            calibration.addProperty("pixelHeightMicrons", cal.getPixelHeightMicrons());
            if (cal.hasZSpacingMicrons()) {
                calibration.addProperty("zSpacingMicrons", cal.getZSpacingMicrons());
            }
            allMetadata.add("calibration", calibration);

            result.add("metadata", allMetadata);

            return ToolResult.success(result);
        }
    }

    /**
     * Read a region of the image.
     */
    public static class ReadRegion implements McpTool {

        private static final String NAME = "qupath_read_region";

        private final McpToolRegistry registry;

        public ReadRegion(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");

            JsonObject properties = new JsonObject();

            JsonObject x = new JsonObject();
            x.addProperty("type", "integer");
            x.addProperty("description", "X coordinate of the region (in pixels at full resolution)");
            properties.add("x", x);

            JsonObject y = new JsonObject();
            y.addProperty("type", "integer");
            y.addProperty("description", "Y coordinate of the region (in pixels at full resolution)");
            properties.add("y", y);

            JsonObject width = new JsonObject();
            width.addProperty("type", "integer");
            width.addProperty("description", "Width of the region (in pixels at full resolution)");
            width.addProperty("minimum", 1);
            width.addProperty("maximum", 4096);
            properties.add("width", width);

            JsonObject height = new JsonObject();
            height.addProperty("type", "integer");
            height.addProperty("description", "Height of the region (in pixels at full resolution)");
            height.addProperty("minimum", 1);
            height.addProperty("maximum", 4096);
            properties.add("height", height);

            JsonObject downsample = new JsonObject();
            downsample.addProperty("type", "number");
            downsample.addProperty("description", "Downsample factor (default: 1.0, use higher values for thumbnail)");
            downsample.addProperty("default", 1.0);
            properties.add("downsample", downsample);

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

            JsonObject format = new JsonObject();
            format.addProperty("type", "string");
            format.addProperty("description", "Output format: 'image' returns base64-encoded PNG, 'stats' returns pixel statistics");
            format.add("enum", GSON.toJsonTree(new String[]{"image", "stats"}));
            format.addProperty("default", "image");
            properties.add("format", format);

            inputSchema.add("properties", properties);

            JsonArray required = new JsonArray();
            required.add("x");
            required.add("y");
            required.add("width");
            required.add("height");
            inputSchema.add("required", required);

            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Read a region of the image. " +
                "Returns base64-encoded PNG image when format='image', " +
                "or pixel statistics (min/max/mean/histogram) when format='stats'. " +
                "Use this to extract specific regions or get pixel data.",
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

            // Parse arguments
            int x = arguments.get("x").getAsInt();
            int y = arguments.get("y").getAsInt();
            int width = arguments.get("width").getAsInt();
            int height = arguments.get("height").getAsInt();
            double downsample = arguments.has("downsample") ? arguments.get("downsample").getAsDouble() : 1.0;
            int z = arguments.has("z") ? arguments.get("z").getAsInt() : 0;
            int t = arguments.has("t") ? arguments.get("t").getAsInt() : 0;
            String format = arguments.has("format") ? arguments.get("format").getAsString() : "image";

            // Validate
            if (width <= 0 || height <= 0) {
                return ToolResult.error("Width and height must be positive");
            }
            if (width > 4096 || height > 4096) {
                return ToolResult.error("Maximum region size is 4096x4096 pixels");
            }

            try {
                @SuppressWarnings("unchecked")
                ImageServer<BufferedImage> server = (ImageServer<BufferedImage>) imageData.getServer();

                // Create region request
                RegionRequest request = RegionRequest.createInstance(
                    server.getPath(),
                    downsample,
                    x, y, width, height, z, t
                );

                // Read the region
                BufferedImage region = server.readRegion(request);

                if (region == null) {
                    return ToolResult.error("Failed to read region: null result");
                }

                if ("stats".equals(format)) {
                    // Return pixel statistics
                    JsonObject stats = computePixelStatistics(region);
                    return ToolResult.success(stats);
                } else {
                    // Return base64-encoded image
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(region, "PNG", baos);
                    String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

                    JsonObject result = new JsonObject();
                    result.addProperty("format", "png");
                    result.addProperty("width", region.getWidth());
                    result.addProperty("height", region.getHeight());
                    result.addProperty("data", base64);
                    result.addProperty("mimeType", "image/png");

                    return ToolResult.success(result);
                }

            } catch (Exception e) {
                logger.error("Failed to read region", e);
                return ToolResult.error("Failed to read region: " + e.getMessage(), e);
            }
        }

        private JsonObject computePixelStatistics(BufferedImage image) {
            JsonObject result = new JsonObject();
            result.addProperty("width", image.getWidth());
            result.addProperty("height", image.getHeight());

            int numBands = image.getRaster().getNumBands();
            JsonArray bands = new JsonArray();

            for (int b = 0; b < numBands; b++) {
                JsonObject bandStats = new JsonObject();
                bandStats.addProperty("band", b);

                double min = Double.MAX_VALUE;
                double max = Double.MIN_VALUE;
                double sum = 0;
                long count = 0;

                // Simple histogram (256 bins for 8-bit, fewer for higher bit depths)
                int[] histogram = new int[256];
                int maxBin = 0;

                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        double value = image.getRaster().getSampleDouble(x, y, b);
                        min = Math.min(min, value);
                        max = Math.max(max, value);
                        sum += value;
                        count++;

                        // Update histogram
                        int bin = (int) Math.min(255, Math.max(0, value));
                        histogram[bin]++;
                        maxBin = Math.max(maxBin, histogram[bin]);
                    }
                }

                bandStats.addProperty("min", min);
                bandStats.addProperty("max", max);
                bandStats.addProperty("mean", sum / count);

                // Include histogram (as base64 for compactness)
                JsonObject histObj = new JsonObject();
                histObj.addProperty("bins", 256);
                histObj.addProperty("maxCount", maxBin);
                bandStats.add("histogram", histObj);

                bands.add(bandStats);
            }

            result.add("bands", bands);
            return result;
        }
    }
}
