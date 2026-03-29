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

    @JsonCreator
    public Step(
            @JsonProperty("toolId") String toolId,
            @JsonProperty("description") String description,
            @JsonProperty("params") Map<String, Object> params) {
        this.id = UUID.randomUUID().toString();
        this.toolId = Objects.requireNonNull(toolId, "toolId must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.params = params != null ? Collections.unmodifiableMap(params) : Collections.emptyMap();
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

    @Override
    public String toString() {
        return "Step{toolId='" + toolId + "', description='" + description + "'}";
    }
}
