package fr.baretto.ollamassist.agent.observability;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Duration;

/**
 * Metrics for entire execution.
 * Extends StepMetrics with additional execution-level metrics.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionMetrics extends StepMetrics {
    private double estimatedCost = 0.0;
    private Duration validationTime = Duration.ZERO;
    private long peakMemoryUsage = 0;
    private int embeddingSearches = 0;
    private int embeddingHits = 0;
    private double averageRelevanceScore = 0.0;
    private int retries = 0;
    private int failures = 0;

    @Override
    public void recordLLMCall(int inputTokens, int outputTokens, Duration duration) {
        super.recordLLMCall(inputTokens, outputTokens, duration);
        // Estimate cost: $0.01 per 1K tokens (example rate)
        this.estimatedCost += (inputTokens + outputTokens) / 1000.0 * 0.01;
    }

    /**
     * Increments the failure counter.
     */
    public void incrementFailures() {
        this.failures++;
    }

    /**
     * Calculates average tokens per LLM call.
     */
    public double getAverageTokensPerCall() {
        return getTotalLLMCalls() > 0
            ? (double) (getTotalTokensInput() + getTotalTokensOutput()) / getTotalLLMCalls()
            : 0.0;
    }
}
