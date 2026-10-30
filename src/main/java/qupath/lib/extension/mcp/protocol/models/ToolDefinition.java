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
