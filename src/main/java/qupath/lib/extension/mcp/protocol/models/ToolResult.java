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
