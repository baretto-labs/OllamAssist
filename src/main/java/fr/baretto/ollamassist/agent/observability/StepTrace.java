package fr.baretto.ollamassist.agent.observability;

import fr.baretto.ollamassist.agent.model.AgentType;
import fr.baretto.ollamassist.agent.tool.ToolResult;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Trace of a single step execution in an agent workflow.
 * Captures input, output, reasoning, sources, and metrics.
 */
@Data
@Builder
public class StepTrace {
    private String stepId;
    private int stepNumber;
    private String executionId;

    private AgentType agentType;
    private String toolName;
    private String action;

    private Instant startTime;
    private Instant endTime;
    private Duration duration;

    private StepState state;
    private String errorMessage;

    private Map<String, Object> inputParameters;
    private ToolResult output;

    private String reasoning;
    @Builder.Default
    private List<SourceReference> sources = new ArrayList<>();
    @Builder.Default
    private List<String> logs = new ArrayList<>();

    @Builder.Default
    private StepMetrics metrics = new StepMetrics();

    /**
     * Records the start of step execution.
     */
    public void recordStart() {
        this.startTime = Instant.now();
        this.state = StepState.RUNNING;
    }

    /**
     * Records successful step completion.
     */
    public void recordSuccess(ToolResult result) {
        this.endTime = Instant.now();
        this.duration = Duration.between(startTime, endTime);
        this.state = StepState.COMPLETED;
        this.output = result;
        if (result != null && result.getSources() != null) {
            this.sources = result.getSources();
        }
    }

    /**
     * Records step failure.
     */
    public void recordError(Exception e) {
        this.endTime = Instant.now();
        this.duration = Duration.between(startTime, endTime);
        this.state = StepState.FAILED;
        this.errorMessage = e.getMessage();
        addLog("ERROR: " + e.getMessage());
    }

    /**
     * Adds a log entry with timestamp.
     */
    public void addLog(String message) {
        logs.add(String.format("[%s] %s", Instant.now().toString(), message));
    }

    /**
     * Records reasoning for this step.
     */
    public void recordReasoning(String reasoning) {
        this.reasoning = reasoning;
        addLog("REASONING: " + reasoning);
    }
}
