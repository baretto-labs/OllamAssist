package fr.baretto.ollamassist.agent.plan;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Step {

    /** Unique identifier generated at deserialisation time — never exposed to the LLM. */
    private final String id;
    private final String toolId;
    private final String description;
    private final Map<String, Object> params;
    /**
     * Optional named output variable. When set, the tool's output is stored under this name
     * and can be referenced in subsequent steps as {@code {{var.<outputVar>}}}.
     * Example: "outputVar": "serviceFilePath" → later step uses "{{var.serviceFilePath}}".
     */
    @org.jetbrains.annotations.Nullable
    private final String outputVar;

    @JsonCreator
    public Step(
            @JsonProperty("toolId") String toolId,
            @JsonProperty("description") String description,
            @JsonProperty("params") Map<String, Object> params) {
        this(toolId, description, params, null);
    }

    public Step(
            @JsonProperty("toolId") String toolId,
            @JsonProperty("description") String description,
            @JsonProperty("params") Map<String, Object> params,
            @JsonProperty("outputVar") @org.jetbrains.annotations.Nullable String outputVar) {
        this.id = UUID.randomUUID().toString();
        this.toolId = Objects.requireNonNull(toolId, "toolId must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.params = params != null ? Collections.unmodifiableMap(params) : Collections.emptyMap();
        this.outputVar = (outputVar != null && outputVar.isBlank()) ? null : outputVar;
    }

    /** Unique identifier stable for the lifetime of this step object. */
    public String getId() {
        return id;
    }

    public String getToolId() {
        return toolId;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    @org.jetbrains.annotations.Nullable
    public String getOutputVar() {
        return outputVar;
    }

    /** Returns a copy of this step with a different description. All other fields are preserved. */
    public Step withDescription(String newDescription) {
        return new Step(this.toolId, Objects.requireNonNull(newDescription, "description must not be null"), this.params, this.outputVar);
    }

    @Override
    public String toString() {
        return "Step{toolId='" + toolId + "', description='" + description + "'}";
    }
}
