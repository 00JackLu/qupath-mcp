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

package qupath.lib.extension.mcp.protocol.models;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Result from executing an MCP tool.
 */
public class ToolResult {

    private static final Gson GSON = new Gson();

    private List<Content> content;
    private boolean isError;

    private ToolResult(List<Content> content, boolean isError) {
        this.content = content;
        this.isError = isError;
    }

    /**
     * Create a successful result with text content.
     */
    public static ToolResult success(String text) {
        List<Content> content = new ArrayList<>();
        content.add(Content.text(text));
        return new ToolResult(content, false);
    }

    /**
     * Create a successful result with JSON content.
     */
    public static ToolResult success(JsonElement json) {
        List<Content> content = new ArrayList<>();
        content.add(Content.text(GSON.toJson(json)));
        return new ToolResult(content, false);
    }

    /**
     * Create a successful result with structured content.
     */
    public static ToolResult success(JsonElement json, JsonElement structuredContent) {
        List<Content> content = new ArrayList<>();
        content.add(Content.text(GSON.toJson(json)));
        return new ToolResult(content, false);
    }

    /**
     * Create a successful result with multiple content items.
     */
    public static ToolResult success(List<Content> content) {
        return new ToolResult(content, false);
    }

    /**
     * Create an error result.
     */
    public static ToolResult error(String message) {
        List<Content> content = new ArrayList<>();
        content.add(Content.text("Error: " + message));
        return new ToolResult(content, true);
    }

    /**
     * Create an error result with exception details.
     */
    public static ToolResult error(String message, Throwable t) {
        List<Content> content = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("Error: ").append(message);
        if (t != null) {
            sb.append("\nCause: ").append(t.getMessage());
        }
        content.add(Content.text(sb.toString()));
        return new ToolResult(content, true);
    }

    public List<Content> getContent() {
        return content;
    }

    public boolean isError() {
        return isError;
    }

    /**
     * Convert to MCP response format.
     */
    public JsonObject toJson() {
        JsonObject result = new JsonObject();
        result.addProperty("isError", isError);
        result.add("content", GSON.toJsonTree(content));
        return result;
    }

    /**
     * Content item in MCP response.
     */
    public static class Content {
        private String type;
        private String text;
        private String data;      // For base64-encoded data
        private String mimeType;  // For binary content

        private Content(String type, String text, String data, String mimeType) {
            this.type = type;
            this.text = text;
            this.data = data;
            this.mimeType = mimeType;
        }

        /**
         * Create text content.
         */
        public static Content text(String text) {
            return new Content("text", text, null, null);
        }

        /**
         * Create image content (base64-encoded).
         */
        public static Content image(String base64Data, String mimeType) {
            return new Content("image", null, base64Data, mimeType);
        }

        /**
         * Create resource content.
         */
        public static Content resource(String uri, String mimeType, String text) {
            Content c = new Content("resource", text, null, mimeType);
            return c;
        }

        public String getType() {
            return type;
        }

        public String getText() {
            return text;
        }

        public String getData() {
            return data;
        }

        public String getMimeType() {
            return mimeType;
        }
    }
}
