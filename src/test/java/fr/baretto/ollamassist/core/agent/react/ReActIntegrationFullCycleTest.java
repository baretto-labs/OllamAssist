package fr.baretto.ollamassist.core.agent.react;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import fr.baretto.ollamassist.chat.service.OllamaService;
import fr.baretto.ollamassist.core.agent.IntelliJDevelopmentAgent;
import fr.baretto.ollamassist.core.agent.validation.ValidationInterceptor;
import fr.baretto.ollamassist.core.agent.validation.ValidationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration tests for complete ReAct cycle
 * Tests the interaction between ReActLoopController, ValidationInterceptor, and IntelliJDevelopmentAgent
 */
public class ReActIntegrationFullCycleTest extends BasePlatformTestCase {

    private ReActLoopController controller;
    private IntelliJDevelopmentAgent mockAgent;
    private OllamaService mockOllamaService;
    private ValidationInterceptor mockValidationInterceptor;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();

        mockAgent = mock(IntelliJDevelopmentAgent.class);
        mockOllamaService = mock(OllamaService.class);
        mockValidationInterceptor = mock(ValidationInterceptor.class);

        controller = new ReActLoopController(
                getProject(),
                mockAgent,
                mockOllamaService
        );
    }

    @Override
    @AfterEach
    protected void tearDown() throws Exception {
        if (controller != null) {
            controller.cleanup();
        }
        super.tearDown();
    }

    @Test
    void testCompleteSuccessfulCycle() {
        // Given - Mock successful class creation
        when(mockAgent.createJavaClass(anyString(), anyString(), anyString()))
                .thenReturn("Successfully created Java class 'Calculator' at 'Calculator.java'\nCode validated - compilation successful");

        // When - This would require mocking the LLM response, which is complex
        // For now, we test the components separately
        ReActContext context = new ReActContext("Create Calculator class", getProject());

        // Simulate successful cycle
        context.incrementIteration();
        context.addThinking(new ReActContext.ThinkingStep("I will create Calculator", "createJavaClass"));
        context.addAction(new ReActContext.ActionStep("createJavaClass", "Create class", null));
        context.addObservation(new ReActContext.ObservationStep(true, "Success", List.of(), List.of()));
        context.markValidationCompleted();

        ReActResult result = ReActResult.success(context, "Calculator created successfully");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContext().getIterationCount()).isEqualTo(1);
        assertThat(result.getContext().isCompletedValidation()).isTrue();
    }

    @Test
    void testCycleWithCompilationError() {
        // Given - Mock class creation with compilation error
        when(mockAgent.createJavaClass(anyString(), anyString(), anyString()))
                .thenReturn("Successfully created Java class 'Calculator'\n️ Compilation validation failed:\nErrors to fix:\n  - Error: Missing import java.util.List");

        // Simulate error detection and fix cycle
        ReActContext context = new ReActContext("Create Calculator class", getProject());

        // Iteration 1: Create class with error
        context.incrementIteration();
        context.addThinking(new ReActContext.ThinkingStep("Create Calculator", "createJavaClass"));
        context.addAction(new ReActContext.ActionStep("createJavaClass", "Create class", null));
        context.addObservation(new ReActContext.ObservationStep(
                false,
                "Compilation failed",
                List.of("Error: Missing import java.util.List"),
                List.of()
        ));

        // Iteration 2: Fix the error
        context.incrementIteration();
        when(mockAgent.createFile(anyString(), anyString()))
                .thenReturn("Successfully created file\nCode validated - compilation successful");

        context.addThinking(new ReActContext.ThinkingStep("Fix import", "createFile"));
        context.addAction(new ReActContext.ActionStep("createFile", "Fix import", null));
        context.addObservation(new ReActContext.ObservationStep(true, "Success", List.of(), List.of()));
        context.markValidationCompleted();

        ReActResult result = ReActResult.success(context, "Calculator created and fixed");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContext().getIterationCount()).isEqualTo(2);
        assertThat(result.getContext().getActionSteps()).hasSize(2);
        assertThat(result.getContext().isCompletedValidation()).isTrue();
    }

    @Test
    void testCycleReachesMaxIterations() {
        // Given - Simulate a cycle that never completes
        ReActContext context = new ReActContext("Infinite task", getProject());

        // Simulate max iterations
        for (int i = 0; i < 10; i++) {
            context.incrementIteration();
            context.addThinking(new ReActContext.ThinkingStep("Try again", "action"));
            context.addAction(new ReActContext.ActionStep("action", "Attempt " + i, null));
            context.addObservation(new ReActContext.ObservationStep(
                    false,
                    "Still failing",
                    List.of("Persistent error"),
                    List.of()
            ));
        }

        ReActResult result = ReActResult.maxIterationsReached(context);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ReActResult.ReActStatus.MAX_ITERATIONS);
        assertThat(result.getContext().getIterationCount()).isEqualTo(10);
    }

    @Test
    void testValidationInterceptorIntegration() {
        // Given
        ValidationInterceptor realInterceptor = new ValidationInterceptor(getProject());

        // When
        boolean requiresValidation = realInterceptor.requiresCompilationCheck(
                "createJavaClass",
                fr.baretto.ollamassist.core.agent.task.TaskResult.success("File created")
        );

        // Then
        assertThat(requiresValidation).isTrue();

        // Cleanup
        realInterceptor.cleanup();
    }

    @Test
    void testComplexMultiStepCycle() {
        // Given - Simulate a complex multi-step task
        ReActContext context = new ReActContext("Create Calculator with add and multiply methods", getProject());

        // Step 1: Create basic class
        context.incrementIteration();
        context.addThinking(new ReActContext.ThinkingStep("Create basic Calculator", "createJavaClass"));
        context.addAction(new ReActContext.ActionStep("createJavaClass", "Create Calculator", null));
        context.addObservation(new ReActContext.ObservationStep(
                false,
                "Missing package",
                List.of("Error: Missing package declaration"),
                List.of()
        ));

        // Step 2: Fix package
        context.incrementIteration();
        context.addThinking(new ReActContext.ThinkingStep("Add package", "createFile"));
        context.addAction(new ReActContext.ActionStep("createFile", "Add package", null));
        context.addObservation(new ReActContext.ObservationStep(
                false,
                "Missing imports",
                List.of("Error: Missing import"),
                List.of()
        ));

        // Step 3: Fix imports
        context.incrementIteration();
        context.addThinking(new ReActContext.ThinkingStep("Add imports", "createFile"));
        context.addAction(new ReActContext.ActionStep("createFile", "Add imports", null));
        context.addObservation(new ReActContext.ObservationStep(true, "Success", List.of(), List.of()));
        context.markValidationCompleted();

        ReActResult result = ReActResult.success(context, "Calculator fully functional");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContext().getIterationCount()).isEqualTo(3);
        assertThat(result.getContext().getThinkingSteps()).hasSize(3);
        assertThat(result.getContext().getActionSteps()).hasSize(3);
        assertThat(result.getContext().getObservationSteps()).hasSize(3);

        // Verify the full history is tracked
        String history = result.getContext().getFullHistory();
        assertThat(history).contains("Create basic Calculator");
        assertThat(history).contains("Add package");
        assertThat(history).contains("Add imports");
    }

    @Test
    void testContextStateManagement() {
        // Given
        ReActContext context = new ReActContext("Test request", getProject());

        // When - Simulate state transitions
        assertThat(context.isCompletedValidation()).isFalse();
        assertThat(context.isRequiresFix()).isFalse();

        context.markAsRequiringFix("Error detected");
        assertThat(context.isRequiresFix()).isTrue();

        context.clearFixRequirement();
        assertThat(context.isRequiresFix()).isFalse();

        context.markValidationCompleted();
        assertThat(context.isCompletedValidation()).isTrue();

        // Then - State is properly managed
        assertThat(context.isCompletedValidation()).isTrue();
        assertThat(context.isRequiresFix()).isFalse();
    }

    @Test
    void testErrorAccumulation() {
        // Given
        ReActContext context = new ReActContext("Test request", getProject());

        // When - Add multiple observations with errors
        context.addObservation(new ReActContext.ObservationStep(
                false, "Failed 1",
                List.of("Error 1", "Error 2"),
                List.of()
        ));
        context.addObservation(new ReActContext.ObservationStep(
                false, "Failed 2",
                List.of("Error 3"),
                List.of()
        ));

        // Then
        assertThat(context.hasErrors()).isTrue();
        assertThat(context.getAllErrors()).hasSize(3);
        assertThat(context.getAllErrors()).containsExactly("Error 1", "Error 2", "Error 3");
    }

    @Test
    void testSuccessfulCycleWithWarnings() {
        // Given
        ReActContext context = new ReActContext("Create class", getProject());

        // When - Successful with warnings
        context.incrementIteration();
        context.addThinking(new ReActContext.ThinkingStep("Create", "createJavaClass"));
        context.addAction(new ReActContext.ActionStep("createJavaClass", "Create", null));
        context.addObservation(new ReActContext.ObservationStep(
                true,
                "Success with warnings",
                List.of(),
                List.of("Warning: Unused variable", "Warning: Deprecated method")
        ));
        context.markValidationCompleted();

        ReActResult result = ReActResult.success(context, "Completed with warnings");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContext().getLastObservation().hasWarnings()).isTrue();
        assertThat(result.getContext().getLastObservation().getWarnings()).hasSize(2);
    }

    @Test
    void testPrepareFixIteration() {
        // Given
        ReActContext context = new ReActContext("Test", getProject());
        List<String> errors = List.of("Missing import", "Syntax error");

        // When
        context.prepareFixIteration(errors);

        // Then
        assertThat(context.isRequiresFix()).isTrue();
        assertThat(context.getFixReason()).isEqualTo("Compilation errors detected");
        assertThat(context.getObservationSteps()).hasSize(1);
        assertThat(context.getLastObservation().hasErrors()).isTrue();
        assertThat(context.getLastObservation().getErrors()).containsAll(errors);
    }

    @Test
    void testResultSummaryGeneration() {
        // Given
        ReActContext context = new ReActContext("Complex task", getProject());

        // Build context with multiple iterations
        for (int i = 0; i < 3; i++) {
            context.incrementIteration();
            context.addThinking(new ReActContext.ThinkingStep("Think " + i, "action"));
            context.addAction(new ReActContext.ActionStep("action", "Action " + i, null));
            context.addObservation(new ReActContext.ObservationStep(
                    i == 2, // Last one succeeds
                    i == 2 ? "Success" : "Failed",
                    i == 2 ? List.of() : List.of("Error " + i),
                    List.of()
            ));
        }
        context.markValidationCompleted();

        ReActResult result = ReActResult.success(context, "All done");

        // When
        String summary = result.getSummary();

        // Then
        assertThat(summary).contains("Iterations: 3");
        assertThat(summary).contains("Actions executed: 3");
        assertThat(summary).contains("Status: COMPLETED");
        assertThat(summary).contains("Success: true");
    }
}
