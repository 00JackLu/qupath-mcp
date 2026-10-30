/*
 * Copyright 2026 Zhaoqi Lu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.regions.RegionRequest;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

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

                if ("stats".equals(format)) {
                    // Statistics need the raw pixel values, so read straight from the source server.
                    RegionRequest request = RegionRequest.createInstance(
                        server.getPath(), downsample, x, y, width, height, z, t);
                    BufferedImage region = server.readRegion(request);
                    if (region == null) {
                        return ToolResult.error("Failed to read region: null result");
                    }
                    JsonObject stats = computePixelStatistics(region);
                    return ToolResult.success(stats);
                }

                // For an image, render the RGB composite the user actually sees. Reading the raw
                // server directly returns a BufferedImage that PNG cannot encode for non-RGB inputs
                // (e.g. multi-channel FLOAT32 fluorescence), which silently produced an empty result.
                BufferedImage region;
                QuPathViewer viewer = qupath.getViewer();
                if (viewer != null && viewer.getImageData() == imageData && viewer.getImageDisplay() != null) {
                    // createRenderedServer applies the viewer's display settings (channel colors,
                    // brightness/contrast) and yields an RGB image, without annotation/detection overlays.
                    try (ImageServer<BufferedImage> renderServer =
                             RenderedImageServer.createRenderedServer(server, viewer.getImageDisplay())) {
                        RegionRequest request = RegionRequest.createInstance(
                            renderServer.getPath(), downsample, x, y, width, height, z, t);
                        region = renderServer.readRegion(request);
                    }
                } else {
                    // No matching viewer (e.g. headless) - fall back to the raw server.
                    RegionRequest request = RegionRequest.createInstance(
                        server.getPath(), downsample, x, y, width, height, z, t);
                    region = server.readRegion(request);
                }

                if (region == null) {
                    return ToolResult.error("Failed to read region: null result");
                }

                // Return base64-encoded PNG. Guard against ImageIO silently failing to encode an
                // unsupported image type: repaint into a standard RGB buffer, then error if still empty.
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                if (!ImageIO.write(region, "PNG", baos) || baos.size() == 0) {
                    BufferedImage rgb = new BufferedImage(
                        region.getWidth(), region.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = rgb.createGraphics();
                    g.drawImage(region, 0, 0, null);
                    g.dispose();
                    baos.reset();
                    if (!ImageIO.write(rgb, "PNG", baos) || baos.size() == 0) {
                        return ToolResult.error(
                            "Failed to encode region as PNG (unsupported image type: " + region.getType() + ")");
                    }
                }
                String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

                JsonObject result = new JsonObject();
                result.addProperty("format", "png");
                result.addProperty("width", region.getWidth());
                result.addProperty("height", region.getHeight());
                result.addProperty("data", base64);
                result.addProperty("mimeType", "image/png");

                return ToolResult.success(result);

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

    /**
     * Capture what QuPath is currently displaying (VLM perception).
     * Returns the snapshot as an MCP image content block so a vision-capable
     * agent can actually see it, plus a small text block of metadata.
     */
    public static class CaptureView implements McpTool {

        private static final String NAME = "qupath_capture_view";

        /**
         * Default hard cap on the encoded image size. Kept well under typical model prompt limits
         * because some MCP clients count image payloads as base64 text (base64 inflates by ~4/3),
         * which can otherwise blow past the context window for a full-resolution screenshot.
         */
        private static final int DEFAULT_MAX_BYTES = 350_000;

        private final McpToolRegistry registry;

        public CaptureView(McpToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");

            JsonObject properties = new JsonObject();

            JsonObject type = new JsonObject();
            type.addProperty("type", "string");
            type.addProperty("description", "What to capture: 'viewer' (rendered image + annotation/detection " +
                "overlays at the current zoom/pan), 'window' (the whole QuPath window), or 'full' (entire screen)");
            type.add("enum", GSON.toJsonTree(new String[]{"viewer", "window", "full"}));
            type.addProperty("default", "viewer");
            properties.add("type", type);

            JsonObject maxDimension = new JsonObject();
            maxDimension.addProperty("type", "integer");
            maxDimension.addProperty("description", "Scale the longest side down to at most this many pixels " +
                "(keeps the image small for vision models). Use 0 to disable scaling.");
            maxDimension.addProperty("default", 1024);
            maxDimension.addProperty("minimum", 0);
            properties.add("maxDimension", maxDimension);

            JsonObject format = new JsonObject();
            format.addProperty("type", "string");
            format.addProperty("description", "Image encoding. 'jpeg' is much smaller and is the default; " +
                "use 'png' only if you need lossless output (produces far larger payloads for busy images).");
            format.add("enum", GSON.toJsonTree(new String[]{"jpeg", "png"}));
            format.addProperty("default", "jpeg");
            properties.add("format", format);

            JsonObject maxBytes = new JsonObject();
            maxBytes.addProperty("type", "integer");
            maxBytes.addProperty("description", "Hard upper bound on the encoded image size in bytes. If the " +
                "capture is larger it is progressively downscaled/recompressed until it fits, so the result can " +
                "never overflow the model's context. Default " + DEFAULT_MAX_BYTES + ".");
            maxBytes.addProperty("default", DEFAULT_MAX_BYTES);
            maxBytes.addProperty("minimum", 0);
            properties.add("maxBytes", maxBytes);

            inputSchema.add("properties", properties);
            inputSchema.addProperty("additionalProperties", false);

            return new ToolDefinition(
                NAME,
                "Capture what QuPath is currently displaying and return it as an image. " +
                "The 'viewer' capture shows the rendered slide together with annotation/detection overlays " +
                "at the current zoom and pan - exactly what a user sees on screen. " +
                "Use this to visually perceive the image, inspect detection results, or decide where to act next.",
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

            // View capture is opt-in (it sends on-screen pixels to the agent). Toggle it via
            // Extensions > MCP Server > "Enable View Capture", or QUPATH_MCP_ENABLE_CAPTURE_VIEW.
            if (!registry.getConfig().isEnableCaptureView()) {
                return ToolResult.error("View capture is disabled. Enable it in QuPath via "
                    + "Extensions ▸ MCP Server ▸ \"Enable View Capture\" "
                    + "(or set QUPATH_MCP_ENABLE_CAPTURE_VIEW=true).");
            }

            String typeArg = arguments.has("type") ? arguments.get("type").getAsString() : "viewer";
            int maxDimension = arguments.has("maxDimension") ? arguments.get("maxDimension").getAsInt() : 1024;
            String format = arguments.has("format") ? arguments.get("format").getAsString().toLowerCase() : "jpeg";
            boolean png = "png".equals(format);
            int maxBytes = arguments.has("maxBytes") ? arguments.get("maxBytes").getAsInt() : DEFAULT_MAX_BYTES;

            GuiTools.SnapshotType snapshotType;
            switch (typeArg) {
                case "window":
                    snapshotType = GuiTools.SnapshotType.MAIN_WINDOW_SCREENSHOT;
                    break;
                case "full":
                    snapshotType = GuiTools.SnapshotType.FULL_SCREENSHOT;
                    break;
                case "viewer":
                default:
                    snapshotType = GuiTools.SnapshotType.VIEWER;
                    break;
            }

            try {
                // makeSnapshot hops to the JavaFX application thread internally as needed.
                BufferedImage image = GuiTools.makeSnapshot(qupath, snapshotType);
                if (image == null) {
                    return ToolResult.error("Failed to capture view: null snapshot");
                }
                int originalW = image.getWidth();
                int originalH = image.getHeight();

                boolean scaled = false;
                if (maxDimension > 0 && Math.max(image.getWidth(), image.getHeight()) > maxDimension) {
                    image = scaleToMax(image, maxDimension);
                    scaled = true;
                }

                // Encode, and if the payload exceeds maxBytes, progressively shrink (and, for JPEG,
                // lower quality) until it fits. This guarantees the result cannot overflow the context.
                String mimeType = png ? "image/png" : "image/jpeg";
                byte[] bytes = null;
                double quality = 0.85;
                for (int attempt = 0; attempt < 8; attempt++) {
                    bytes = png ? encodePng(image) : encodeJpeg(image, quality);
                    if (bytes == null) {
                        return ToolResult.error("Failed to encode snapshot as " + (png ? "PNG" : "JPEG"));
                    }
                    if (maxBytes <= 0 || bytes.length <= maxBytes)
                        break;
                    // Too big: reduce quality first (JPEG only), then dimensions.
                    if (!png && quality > 0.4) {
                        quality -= 0.15;
                    } else {
                        int longest = Math.max(image.getWidth(), image.getHeight());
                        if (longest <= 128)
                            break; // give up shrinking; return whatever we have
                        image = scaleToMax(image, (int) Math.round(longest * 0.75));
                        scaled = true;
                        if (!png)
                            quality = 0.85; // reset quality after a resize
                    }
                }

                String base64 = Base64.getEncoder().encodeToString(bytes);

                JsonObject meta = new JsonObject();
                meta.addProperty("format", png ? "png" : "jpeg");
                meta.addProperty("captured", typeArg);
                meta.addProperty("width", image.getWidth());
                meta.addProperty("height", image.getHeight());
                meta.addProperty("originalWidth", originalW);
                meta.addProperty("originalHeight", originalH);
                meta.addProperty("scaled", scaled);
                meta.addProperty("bytes", bytes.length);

                List<ToolResult.Content> content = new ArrayList<>();
                content.add(ToolResult.Content.text(GSON.toJson(meta)));
                content.add(ToolResult.Content.image(base64, mimeType));
                return ToolResult.success(content);

            } catch (Exception e) {
                logger.error("Failed to capture view", e);
                return ToolResult.error("Failed to capture view: " + e.getMessage(), e);
            }
        }

        private static byte[] encodePng(BufferedImage image) throws java.io.IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "PNG", baos) || baos.size() == 0)
                return null;
            return baos.toByteArray();
        }

        private static byte[] encodeJpeg(BufferedImage src, double quality) throws java.io.IOException {
            // JPEG has no alpha channel; flatten onto an opaque RGB image first.
            BufferedImage rgb = src;
            if (src.getType() != BufferedImage.TYPE_INT_RGB) {
                rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                g.drawImage(src, 0, 0, null);
                g.dispose();
            }
            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality((float) Math.max(0.1, Math.min(1.0, quality)));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new javax.imageio.IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }
            return baos.size() == 0 ? null : baos.toByteArray();
        }

        private static BufferedImage scaleToMax(BufferedImage src, int maxDimension) {
            int w = src.getWidth();
            int h = src.getHeight();
            double scale = (double) maxDimension / Math.max(w, h);
            int nw = Math.max(1, (int) Math.round(w * scale));
            int nh = Math.max(1, (int) Math.round(h * scale));
            BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();
            return dst;
        }
    }

    /**
     * Report the current viewer state: displayed region, zoom, position, channels and overlay flags.
     * This grounds an agent's actions in what the user is actually looking at.
     */
    public static class GetViewerState implements McpTool {

        private static final String NAME = "qupath_get_viewer_state";

        private final McpToolRegistry registry;

        public GetViewerState(McpToolRegistry registry) {
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
                "Get the current viewer state: the region of the image currently displayed (x, y, width, " +
                "height in full-resolution pixels), the zoom (downsample) and magnification, the z/t position, " +
                "the channels currently shown, and overlay display flags. Use this to ground actions in what " +
                "the user is actually looking at.",
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
            var viewer = qupath.getViewer();
            if (viewer == null || viewer.getImageData() == null) {
                return ToolResult.error("No image is currently open in the viewer");
            }
            try {
                JsonObject result = new JsonObject();
                result.addProperty("downsample", viewer.getDownsampleFactor());
                result.addProperty("magnification", viewer.getMagnification());
                result.addProperty("zPosition", viewer.getZPosition());
                result.addProperty("tPosition", viewer.getTPosition());

                java.awt.Rectangle bounds = viewer.getDisplayedRegionShape().getBounds();
                JsonObject region = new JsonObject();
                region.addProperty("x", bounds.x);
                region.addProperty("y", bounds.y);
                region.addProperty("width", bounds.width);
                region.addProperty("height", bounds.height);
                result.add("displayedRegion", region);

                var display = viewer.getImageDisplay();
                JsonArray shown = new JsonArray();
                if (display != null) {
                    for (qupath.lib.display.ChannelDisplayInfo c : display.selectedChannels())
                        shown.add(c.getName());
                    result.addProperty("channelsAvailable", display.availableChannels().size());
                }
                result.add("displayedChannels", shown);

                var ov = viewer.getOverlayOptions();
                JsonObject overlay = new JsonObject();
                overlay.addProperty("showAnnotations", ov.getShowAnnotations());
                overlay.addProperty("showDetections", ov.getShowDetections());
                overlay.addProperty("fillDetections", ov.getFillDetections());
                overlay.addProperty("showNames", ov.getShowNames());
                overlay.addProperty("showConnections", ov.getShowConnections());
                result.add("overlayOptions", overlay);

                return ToolResult.success(result);
            } catch (Exception e) {
                logger.error("Failed to get viewer state", e);
                return ToolResult.error("Failed to get viewer state: " + e.getMessage(), e);
            }
        }
    }
}
