package fr.baretto.ollamassist.agent.observability;

import lombok.Data;

import java.time.Duration;

/**
 * Metrics for a single step execution.
 */
@Data
public class StepMetrics {
    private int totalLLMCalls = 0;
    private int totalTokensInput = 0;
    private int totalTokensOutput = 0;
    private Duration llmTotalTime = Duration.ZERO;
    private Duration toolExecutionTime = Duration.ZERO;

    /**
     * Records a single LLM call with its metrics.
     */
    public void recordLLMCall(int inputTokens, int outputTokens, Duration duration) {
        this.totalLLMCalls++;
        this.totalTokensInput += inputTokens;
        this.totalTokensOutput += outputTokens;
        this.llmTotalTime = llmTotalTime.plus(duration);
    }
}
