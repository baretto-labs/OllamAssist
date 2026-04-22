package fr.baretto.ollamassist.agent;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.plan.Step;
import fr.baretto.ollamassist.events.StepRetryNotifier;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes a {@link StepRetryNotifier} request and blocks the calling thread until
 * the user makes a retry decision (or the timeout elapses).
 *
 * <p>Must be called from a background thread — never from the EDT.
 */
@Slf4j
public final class StepRetryHelper {

    private static final long DEFAULT_TIMEOUT_MINUTES = 5;

    private final Project project;

    public StepRetryHelper(Project project) {
        this.project = project;
    }

    /**
     * Requests a retry decision from the user and blocks until a response is received.
     *
     * <p>On timeout or interruption, defaults to {@link StepRetryDecision#ABORT_PHASE}
     * so the Critic can evaluate the failure rather than silently continuing.
     *
     * @param step         the step that failed
     * @param errorMessage the failure message to display
     * @return the user's decision
     */
    public StepRetryDecision requestDecision(Step step, String errorMessage) {
        CompletableFuture<StepRetryDecision> future = new CompletableFuture<>();

        StepRetryNotifier.RetryRequest request = StepRetryNotifier.RetryRequest.builder()
                .step(step)
                .errorMessage(errorMessage)
                .responseFuture(future)
                .build();

        project.getMessageBus()
                .syncPublisher(StepRetryNotifier.TOPIC)
                .requestRetry(request);

        try {
            return future.get(DEFAULT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Retry decision timed out for step '{}' — defaulting to ABORT_PHASE", step.getDescription());
            return StepRetryDecision.ABORT_PHASE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Retry decision interrupted for step '{}'", step.getDescription());
            return StepRetryDecision.ABORT_PHASE;
        } catch (Exception e) {
            log.error("Retry request failed for step '{}'", step.getDescription(), e);
            return StepRetryDecision.ABORT_PHASE;
        }
    }
}
