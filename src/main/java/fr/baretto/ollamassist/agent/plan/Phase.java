package fr.baretto.ollamassist.agent.plan;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Phase {

    private final String description;
    private final List<Step> steps;

    @JsonCreator
    public Phase(
            @JsonProperty("description") String description,
            @JsonProperty("steps") List<Step> steps) {
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.steps = steps != null ? Collections.unmodifiableList(steps) : Collections.emptyList();
    }

    public String getDescription() {
        return description;
    }

    public List<Step> getSteps() {
        return steps;
    }

    @Override
    public String toString() {
        return "Phase{description='" + description + "', steps=" + steps.size() + "}";
    }
}
