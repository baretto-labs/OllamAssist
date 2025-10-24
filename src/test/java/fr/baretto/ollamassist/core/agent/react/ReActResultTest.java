package fr.baretto.ollamassist.core.agent.react;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ReActResult
 */
public class ReActResultTest extends BasePlatformTestCase {

    private ReActContext context;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        context = new ReActContext("Test request", getProject());
    }

    @Override
    @AfterEach
    protected void tearDown() throws Exception {
        try {
            context = null;
        } finally {
            super.tearDown();
        }
    }

    @Test
    void testSuccessCreation() {
        // Given
        String message = "Task completed successfully";

        // When
        ReActResult result = ReActResult.success(context, message);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFinalMessage()).isEqualTo(message);
        assertThat(result.getStatus()).isEqualTo(ReActResult.ReActStatus.COMPLETED);
        assertThat(result.getContext()).isEqualTo(context);
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void testErrorCreation() {
        // Given
        String errorMessage = "Something went wrong";

        // When
        ReActResult result = ReActResult.error(context, errorMessage);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(result.getStatus()).isEqualTo(ReActResult.ReActStatus.ERROR);
        assertThat(result.getFinalMessage()).isNull();
    }

    @Test
    void testMaxIterationsReached() {
        // Given
        context.incrementIteration();
        context.incrementIteration();
        context.incrementIteration();

        // When
        ReActResult result = ReActResult.maxIterationsReached(context);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ReActResult.ReActStatus.MAX_ITERATIONS);
        assertThat(result.getFinalMessage()).contains("Max iterations");
        assertThat(result.getFinalMessage()).contains("3");
    }

    @Test
    void testCancelled() {
        // Given
        String reason = "User cancelled the operation";

        // When
        ReActResult result = ReActResult.cancelled(context, reason);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ReActResult.ReActStatus.CANCELLED);
        assertThat(result.getErrorMessage()).isEqualTo(reason);
    }

    @Test
    void testGetUserMessageForSuccess() {
        // Given
        String message = "Calculator class created successfully";
        ReActResult result = ReActResult.success(context, message);

        // When
        String userMessage = result.getUserMessage();

        // Then
        assertThat(userMessage).isEqualTo(message);
    }

    @Test
    void testGetUserMessageForError() {
        // Given
        String errorMessage = "Failed to compile";
        ReActResult result = ReActResult.error(context, errorMessage);

        // When
        String userMessage = result.getUserMessage();

        // Then
        assertThat(userMessage).contains("❌");
        assertThat(userMessage).contains("erreur");
        assertThat(userMessage).contains(errorMessage);
    }

    @Test
    void testGetUserMessageForMaxIterations() {
        // Given
        ReActResult result = ReActResult.maxIterationsReached(context);

        // When
        String userMessage = result.getUserMessage();

        // Then
        assertThat(userMessage).contains("⚠️");
        assertThat(userMessage).contains("Limite");
        assertThat(userMessage).contains("partiellement");
    }

    @Test
    void testGetUserMessageForCancelled() {
        // Given
        ReActResult result = ReActResult.cancelled(context, "User stopped");

        // When
        String userMessage = result.getUserMessage();

        // Then
        assertThat(userMessage).contains("🛑");
        assertThat(userMessage).contains("annulée");
        assertThat(userMessage).contains("User stopped");
    }

    @Test
    void testGetSummaryForSuccess() {
        // Given
        context.incrementIteration();
        context.addAction(new ReActContext.ActionStep("createJavaClass", "Create class", null));
        ReActResult result = ReActResult.success(context, "Task completed");

        // When
        String summary = result.getSummary();

        // Then
        assertThat(summary).contains("=== ReAct Result ===");
        assertThat(summary).contains("Status: COMPLETED");
        assertThat(summary).contains("Success: true");
        assertThat(summary).contains("Iterations: 1");
        assertThat(summary).contains("Actions executed: 1");
        assertThat(summary).contains("Final message: Task completed");
    }

    @Test
    void testGetSummaryForError() {
        // Given
        context.incrementIteration();
        ReActResult result = ReActResult.error(context, "Compilation failed");

        // When
        String summary = result.getSummary();

        // Then
        assertThat(summary).contains("Status: ERROR");
        assertThat(summary).contains("Success: false");
        assertThat(summary).contains("Error: Compilation failed");
    }

    @Test
    void testGetSummaryWithRemainingErrors() {
        // Given
        context.incrementIteration();
        context.addObservation(new ReActContext.ObservationStep(
                false,
                "Failed",
                List.of("Error 1: Missing import", "Error 2: Syntax error"),
                List.of()
        ));
        ReActResult result = ReActResult.error(context, "Failed");

        // When
        String summary = result.getSummary();

        // Then
        assertThat(summary).contains("Remaining errors:");
        assertThat(summary).contains("Missing import");
        assertThat(summary).contains("Syntax error");
    }

    @Test
    void testResultWithComplexContext() {
        // Given - Build a complex context
        context.incrementIteration();
        context.addThinking(new ReActContext.ThinkingStep("Plan action", "createJavaClass"));
        context.addAction(new ReActContext.ActionStep("createJavaClass", "Create class", null));
        context.addObservation(new ReActContext.ObservationStep(false, "Failed", List.of("Error 1"), List.of()));

        context.incrementIteration();
        context.addThinking(new ReActContext.ThinkingStep("Fix error", "createFile"));
        context.addAction(new ReActContext.ActionStep("createFile", "Fix import", null));
        context.addObservation(new ReActContext.ObservationStep(true, "Success", List.of(), List.of()));

        context.markValidationCompleted();

        // When
        ReActResult result = ReActResult.success(context, "All tasks completed");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContext().getIterationCount()).isEqualTo(2);
        assertThat(result.getContext().getActionSteps()).hasSize(2);
        assertThat(result.getContext().isCompletedValidation()).isTrue();
    }

    @Test
    void testResultStatus() {
        // When
        ReActResult.ReActStatus completed = ReActResult.ReActStatus.COMPLETED;
        ReActResult.ReActStatus error = ReActResult.ReActStatus.ERROR;
        ReActResult.ReActStatus maxIterations = ReActResult.ReActStatus.MAX_ITERATIONS;
        ReActResult.ReActStatus cancelled = ReActResult.ReActStatus.CANCELLED;

        // Then
        assertThat(completed).isNotNull();
        assertThat(error).isNotNull();
        assertThat(maxIterations).isNotNull();
        assertThat(cancelled).isNotNull();
        assertThat(ReActResult.ReActStatus.values()).hasSize(4);
    }

    @Test
    void testMultipleErrorsInSummary() {
        // Given
        context.incrementIteration();
        context.addObservation(new ReActContext.ObservationStep(
                false,
                "Compilation failed",
                List.of(
                        "Error 1: Missing import java.util.List",
                        "Error 2: Cannot find symbol String",
                        "Error 3: Syntax error at line 10"
                ),
                List.of()
        ));
        ReActResult result = ReActResult.error(context, "Multiple compilation errors");

        // When
        String summary = result.getSummary();

        // Then
        assertThat(summary).contains("Remaining errors:");
        assertThat(summary).contains("Missing import");
        assertThat(summary).contains("Cannot find symbol");
        assertThat(summary).contains("Syntax error");
    }

    @Test
    void testSuccessAfterMultipleIterations() {
        // Given - Simulate 5 iterations with final success
        for (int i = 0; i < 5; i++) {
            context.incrementIteration();
            context.addAction(new ReActContext.ActionStep("action" + i, "Action " + i, null));
        }
        context.markValidationCompleted();

        // When
        ReActResult result = ReActResult.success(context, "Completed after 5 iterations");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContext().getIterationCount()).isEqualTo(5);
        assertThat(result.getContext().getActionSteps()).hasSize(5);
        String summary = result.getSummary();
        assertThat(summary).contains("Iterations: 5");
        assertThat(summary).contains("Actions executed: 5");
    }

    @Test
    void testGetSummaryWithNoErrors() {
        // Given
        context.incrementIteration();
        context.addObservation(new ReActContext.ObservationStep(
                true,
                "Success",
                List.of(),
                List.of()
        ));
        ReActResult result = ReActResult.success(context, "Perfect execution");

        // When
        String summary = result.getSummary();

        // Then
        assertThat(summary).doesNotContain("Remaining errors");
        assertThat(summary).contains("Success: true");
    }

    @Test
    void testUserMessageLocalization() {
        // All user messages should be in French
        ReActResult success = ReActResult.success(context, "Done");
        ReActResult error = ReActResult.error(context, "Failed");
        ReActResult maxIter = ReActResult.maxIterationsReached(context);
        ReActResult cancelled = ReActResult.cancelled(context, "Stopped");

        // Check that error messages contain French text
        assertThat(error.getUserMessage()).containsAnyOf("erreur", "Erreur");
        assertThat(maxIter.getUserMessage()).containsAnyOf("Limite", "partiellement");
        assertThat(cancelled.getUserMessage()).containsAnyOf("annulée", "Opération");
    }
}
