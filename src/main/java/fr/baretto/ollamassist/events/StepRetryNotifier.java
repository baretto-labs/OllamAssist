package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;
import fr.baretto.ollamassist.agent.StepRetryDecision;
import fr.baretto.ollamassist.agent.plan.Step;
import lombok.Builder;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;

/**
 * MessageBus topic used by {@link fr.baretto.ollamassist.agent.AgentOrchestrator} to request
 * a retry decision from the user when a step fails.
 *
 * <p>The orchestrator publishes a {@link RetryRequest} on the background thread and blocks
 * waiting for the future to be completed (by the UI on the EDT).
 */
public interface StepRetryNotifier {

    Topic<StepRetryNotifier> TOPIC = Topic.create("Step Retry Request", StepRetryNotifier.class);

    void requestRetry(RetryRequest request);

    @Getter
    @Builder
    class RetryRequest {
        private final Step step;
        private final String errorMessage;
        private final CompletableFuture<StepRetryDecision> responseFuture;
    }
}
