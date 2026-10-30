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

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * MCP tool definition.
 */
public class ToolDefinition {

    private String name;
    private String description;
    private JsonObject inputSchema;
    private JsonObject outputSchema;
    private Annotations annotations;

    public ToolDefinition(String name, String description, JsonObject inputSchema) {
        this(name, description, inputSchema, null, null);
    }

    public ToolDefinition(String name, String description, JsonObject inputSchema,
                          JsonObject outputSchema, Annotations annotations) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
        this.annotations = annotations;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JsonObject getInputSchema() {
        return inputSchema;
    }

    public JsonObject getOutputSchema() {
        return outputSchema;
    }

    public Annotations getAnnotations() {
        return annotations;
    }

    /**
     * Tool annotations as defined in MCP specification.
     */
    public static class Annotations {
        @SerializedName("readOnlyHint")
        private Boolean readOnlyHint;

        @SerializedName("destructiveHint")
        private Boolean destructiveHint;

        @SerializedName("idempotentHint")
        private Boolean idempotentHint;

        @SerializedName("openWorldHint")
        private Boolean openWorldHint;

        public Annotations(Boolean readOnlyHint, Boolean destructiveHint,
                          Boolean idempotentHint, Boolean openWorldHint) {
            this.readOnlyHint = readOnlyHint;
            this.destructiveHint = destructiveHint;
            this.idempotentHint = idempotentHint;
            this.openWorldHint = openWorldHint;
        }

        public static Annotations readOnly() {
            return new Annotations(true, false, true, true);
        }

        public static Annotations write() {
            return new Annotations(false, true, false, true);
        }

        public static Annotations destructive() {
            return new Annotations(false, true, false, true);
        }
    }
}
