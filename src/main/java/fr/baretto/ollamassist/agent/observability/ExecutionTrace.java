package fr.baretto.ollamassist.agent.observability;

import fr.baretto.ollamassist.agent.model.ExecutionState;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Complete trace of an agent execution workflow.
 * Contains all step traces, aggregated sources, and metrics.
 */
@Data
@Builder
public class ExecutionTrace {
    private String executionId;
    private String userRequestId;

    private Instant startTime;
    private Instant endTime;
    private Duration totalDuration;

    private ExecutionState finalState;
    private String errorMessage;

    @Builder.Default
    private List<StepTrace> stepTraces = new ArrayList<>();

    @Builder.Default
    private List<SourceReference> allSources = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Builder.Default
    private ExecutionMetrics metrics = new ExecutionMetrics();

    /**
     * Adds a step trace and accumulates its sources.
     */
    public void addStepTrace(StepTrace stepTrace) {
        stepTraces.add(stepTrace);
        if (stepTrace.getSources() != null) {
            allSources.addAll(stepTrace.getSources());
        }
    }

    /**
     * Returns the number of completed steps.
     */
    public int getCompletedSteps() {
        return (int) stepTraces.stream()
            .filter(s -> s.getState() == StepState.COMPLETED)
            .count();
    }

    /**
     * Returns the total number of steps.
     */
    public int getTotalSteps() {
        return stepTraces.size();
    }

    /**
     * Returns all failed steps.
     */
    public List<StepTrace> getFailedSteps() {
        return stepTraces.stream()
            .filter(s -> s.getState() == StepState.FAILED)
            .collect(Collectors.toList());
    }
}
