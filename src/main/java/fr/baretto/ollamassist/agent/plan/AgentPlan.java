package fr.baretto.ollamassist.agent.plan;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class AgentPlan {

    private final String goal;
    private final String reasoning;
    private final List<Phase> phases;

    @JsonCreator
    public AgentPlan(
            @JsonProperty("goal") String goal,
            @JsonProperty("reasoning") String reasoning,
            @JsonProperty("phases") List<Phase> phases) {
        this.goal = Objects.requireNonNull(goal, "goal must not be null");
        this.reasoning = reasoning != null ? reasoning : "";
        this.phases = phases != null ? Collections.unmodifiableList(phases) : Collections.emptyList();
    }

    public String getGoal() {
        return goal;
    }

    public String getReasoning() {
        return reasoning;
    }

    public List<Phase> getPhases() {
        return phases;
    }

    public boolean isEmpty() {
        return phases.isEmpty();
    }

    public int totalSteps() {
        return phases.stream().mapToInt(p -> p.getSteps().size()).sum();
    }

    @Override
    public String toString() {
        return "AgentPlan{goal='" + goal + "', phases=" + phases.size() + ", steps=" + totalSteps() + "}";
    }
}
