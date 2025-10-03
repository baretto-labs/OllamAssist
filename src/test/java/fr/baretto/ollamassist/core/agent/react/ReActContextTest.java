package fr.baretto.ollamassist.core.agent.react;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ReActContext
 */
public class ReActContextTest extends BasePlatformTestCase {

    private ReActContext context;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        context = new ReActContext("Create a Calculator class", getProject());
    }

    @Test
    public void testInitialization() {
        // Then
        assertThat(context.getOriginalRequest()).isEqualTo("Create a Calculator class");
        assertThat(context.getProject()).isEqualTo(getProject());
        assertThat(context.getStartTime()).isNotNull();
        assertThat(context.getIterationCount()).isEqualTo(0);
        assertThat(context.isCompletedValidation()).isFalse();
        assertThat(context.isRequiresFix()).isFalse();
    }

    @Test
    public void testIncrementIteration() {
        // When
        context.incrementIteration();
        context.incrementIteration();
        context.incrementIteration();

        // Then
        assertThat(context.getIterationCount()).isEqualTo(3);
    }

    @Test
    public void testAddThinkingStep() {
        // Given
        ReActContext.ThinkingStep thinking = new ReActContext.ThinkingStep(
                "I will create a Calculator class",
                "createJavaClass"
        );

        // When
        context.addThinking(thinking);

        // Then
        assertThat(context.getThinkingSteps()).hasSize(1);
        assertThat(context.getLastThinking()).isEqualTo(thinking);
        assertThat(context.getLastThinking().getReasoning()).isEqualTo("I will create a Calculator class");
    }

    @Test
    public void testAddActionStep() {
        // Given
        ReActContext.ActionStep action = new ReActContext.ActionStep(
                "createJavaClass",
                "Create Calculator class",
                Map.of("className", "Calculator", "filePath", "Calculator.java")
        );

        // When
        context.addAction(action);

        // Then
        assertThat(context.getActionSteps()).hasSize(1);
        assertThat(context.getLastAction()).isEqualTo(action);
        assertThat(context.getLastAction().getToolName()).isEqualTo("createJavaClass");
    }

    @Test
    public void testAddObservationStep() {
        // Given
        ReActContext.ObservationStep observation = new ReActContext.ObservationStep(
                true,
                "Class created successfully",
                List.of(),
                List.of()
        );

        // When
        context.addObservation(observation);

        // Then
        assertThat(context.getObservationSteps()).hasSize(1);
        assertThat(context.getLastObservation()).isEqualTo(observation);
        assertThat(context.getLastObservation().isSuccess()).isTrue();
    }

    @Test
    public void testMarkValidationCompleted() {
        // When
        context.markValidationCompleted();

        // Then
        assertThat(context.isCompletedValidation()).isTrue();
    }

    @Test
    public void testMarkAsRequiringFix() {
        // When
        context.markAsRequiringFix("Compilation errors detected");

        // Then
        assertThat(context.isRequiresFix()).isTrue();
        assertThat(context.getFixReason()).isEqualTo("Compilation errors detected");
    }

    @Test
    public void testClearFixRequirement() {
        // Given
        context.markAsRequiringFix("Some error");

        // When
        context.clearFixRequirement();

        // Then
        assertThat(context.isRequiresFix()).isFalse();
        assertThat(context.getFixReason()).isNull();
    }

    @Test
    public void testHasErrorsWhenNoObservations() {
        // Then
        assertThat(context.hasErrors()).isFalse();
    }

    @Test
    public void testHasErrorsWhenSuccessfulObservation() {
        // Given
        context.addObservation(new ReActContext.ObservationStep(
                true, "Success", List.of(), List.of()
        ));

        // Then
        assertThat(context.hasErrors()).isFalse();
    }

    @Test
    public void testHasErrorsWhenFailedObservation() {
        // Given
        context.addObservation(new ReActContext.ObservationStep(
                false,
                "Failed",
                List.of("Error 1", "Error 2"),
                List.of()
        ));

        // Then
        assertThat(context.hasErrors()).isTrue();
    }

    @Test
    public void testGetAllErrors() {
        // Given
        context.addObservation(new ReActContext.ObservationStep(
                false, "Failed 1", List.of("Error 1", "Error 2"), List.of()
        ));
        context.addObservation(new ReActContext.ObservationStep(
                false, "Failed 2", List.of("Error 3"), List.of()
        ));

        // When
        List<String> allErrors = context.getAllErrors();

        // Then
        assertThat(allErrors).hasSize(3);
        assertThat(allErrors).contains("Error 1", "Error 2", "Error 3");
    }

    @Test
    public void testLastObservationSuccessful() {
        // Given
        context.addObservation(new ReActContext.ObservationStep(
                true, "Success", List.of(), List.of()
        ));

        // Then
        assertThat(context.lastObservationSuccessful()).isTrue();
    }

    @Test
    public void testLastObservationSuccessfulWhenNoObservations() {
        // Then
        assertThat(context.lastObservationSuccessful()).isFalse();
    }

    @Test
    public void testPrepareFixIteration() {
        // Given
        List<String> errors = List.of("Missing import", "Syntax error");

        // When
        context.prepareFixIteration(errors);

        // Then
        assertThat(context.isRequiresFix()).isTrue();
        assertThat(context.getFixReason()).isEqualTo("Compilation errors detected");
        assertThat(context.getObservationSteps()).hasSize(1);
        assertThat(context.getLastObservation().hasErrors()).isTrue();
    }

    @Test
    public void testGetSummary() {
        // Given
        context.incrementIteration();
        context.addThinking(new ReActContext.ThinkingStep("Think", "act"));
        context.addAction(new ReActContext.ActionStep("tool", "desc", Map.of()));
        context.addObservation(new ReActContext.ObservationStep(true, "ok", List.of(), List.of()));

        // When
        String summary = context.getSummary();

        // Then
        assertThat(summary).contains("ReAct Context");
        assertThat(summary).contains("iterations=1");
        assertThat(summary).contains("thinking=1");
        assertThat(summary).contains("actions=1");
        assertThat(summary).contains("observations=1");
    }

    @Test
    public void testGetFullHistory() {
        // Given
        context.incrementIteration();
        context.addThinking(new ReActContext.ThinkingStep("I will create a class", "createJavaClass"));
        context.addAction(new ReActContext.ActionStep("createJavaClass", "Create Calculator", Map.of()));
        context.addObservation(new ReActContext.ObservationStep(true, "Class created", List.of(), List.of()));

        // When
        String history = context.getFullHistory();

        // Then
        assertThat(history).contains("ReAct Cycle History");
        assertThat(history).contains("Request: Create a Calculator class");
        assertThat(history).contains("Iterations: 1");
        assertThat(history).contains("THINK: I will create a class");
        assertThat(history).contains("ACT: createJavaClass - Create Calculator");
        assertThat(history).contains("OBSERVE: ✅ Class created");
    }

    @Test
    public void testComplexCycle() {
        // Given - Iteration 1: Create class with error
        context.incrementIteration();
        context.addThinking(new ReActContext.ThinkingStep("Create Calculator", "createJavaClass"));
        context.addAction(new ReActContext.ActionStep("createJavaClass", "Create class", Map.of()));
        context.addObservation(new ReActContext.ObservationStep(
                false, "Compilation failed", List.of("Missing import"), List.of()
        ));

        // Iteration 2: Fix the error
        context.incrementIteration();
        context.addThinking(new ReActContext.ThinkingStep("Fix import", "createFile"));
        context.addAction(new ReActContext.ActionStep("createFile", "Add import", Map.of()));
        context.addObservation(new ReActContext.ObservationStep(
                true, "Compilation successful", List.of(), List.of()
        ));
        context.markValidationCompleted();

        // Then
        assertThat(context.getIterationCount()).isEqualTo(2);
        assertThat(context.getThinkingSteps()).hasSize(2);
        assertThat(context.getActionSteps()).hasSize(2);
        assertThat(context.getObservationSteps()).hasSize(2);
        assertThat(context.isCompletedValidation()).isTrue();
        assertThat(context.lastObservationSuccessful()).isTrue();
    }

    @Test
    public void testThinkingStepWithFinalAnswer() {
        // Given
        ReActContext.ThinkingStep thinking = new ReActContext.ThinkingStep(
                "Task is complete",
                true,
                "Calculator class created successfully"
        );

        // When
        context.addThinking(thinking);

        // Then
        assertThat(thinking.isHasFinalAnswer()).isTrue();
        assertThat(thinking.getFinalAnswer()).isEqualTo("Calculator class created successfully");
        assertThat(thinking.getNextAction()).isNull();
    }

    @Test
    public void testObservationStepWithWarnings() {
        // Given
        ReActContext.ObservationStep observation = new ReActContext.ObservationStep(
                true,
                "Compilation successful",
                List.of(),
                List.of("Warning: Unused variable", "Warning: Deprecated method")
        );

        // When
        context.addObservation(observation);

        // Then
        assertThat(observation.hasWarnings()).isTrue();
        assertThat(observation.getWarnings()).hasSize(2);
        assertThat(context.getLastObservation().hasWarnings()).isTrue();
    }

    @Test
    public void testMultipleIterationsWithDifferentOutcomes() {
        // Iteration 1: Fail
        context.incrementIteration();
        context.addObservation(new ReActContext.ObservationStep(false, "Failed", List.of("Error 1"), List.of()));

        // Iteration 2: Fail again
        context.incrementIteration();
        context.addObservation(new ReActContext.ObservationStep(false, "Failed", List.of("Error 2"), List.of()));

        // Iteration 3: Success
        context.incrementIteration();
        context.addObservation(new ReActContext.ObservationStep(true, "Success", List.of(), List.of()));
        context.markValidationCompleted();

        // Then
        assertThat(context.getIterationCount()).isEqualTo(3);
        assertThat(context.getObservationSteps()).hasSize(3);
        assertThat(context.lastObservationSuccessful()).isTrue();
        assertThat(context.isCompletedValidation()).isTrue();
    }

    @Test
    public void testActionStepParameters() {
        // Given
        Map<String, Object> params = Map.of(
                "className", "Calculator",
                "filePath", "src/main/java/Calculator.java",
                "content", "public class Calculator { }"
        );
        ReActContext.ActionStep action = new ReActContext.ActionStep(
                "createJavaClass",
                "Create Calculator class",
                params
        );

        // When
        context.addAction(action);

        // Then
        assertThat(action.getParameters()).containsKeys("className", "filePath", "content");
        assertThat(action.getParameters().get("className")).isEqualTo("Calculator");
        assertThat(action.getTimestamp()).isNotNull();
    }
}
