package fr.baretto.ollamassist.core.agent.react;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import fr.baretto.ollamassist.chat.service.OllamaService;
import fr.baretto.ollamassist.core.agent.IntelliJDevelopmentAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for ReActLoopController
 */
public class ReActLoopControllerTest extends BasePlatformTestCase {

    private ReActLoopController controller;
    private IntelliJDevelopmentAgent mockAgent;
    private OllamaService mockOllamaService;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();

        // Create mocks
        mockAgent = mock(IntelliJDevelopmentAgent.class);
        mockOllamaService = mock(OllamaService.class);

        // Create controller with mocks
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
    public void testControllerIsInitialized() {
        // Then
        assertThat(controller).isNotNull();
    }

    @Test
    public void testExecuteWithLoopReturnsCompletableFuture() {
        // Given
        String userRequest = "Create a Calculator class";

        // When
        var future = controller.executeWithLoop(userRequest);

        // Then
        assertThat(future).isNotNull();
        assertThat(future).isInstanceOf(java.util.concurrent.CompletableFuture.class);
    }

    @Test
    public void testCleanupReleasesResources() {
        // When
        controller.cleanup();

        // Then
        // No exception should be thrown
        assertThat(controller).isNotNull();
    }

    @Test
    public void testReActContextTracksIterations() {
        // Given
        String userRequest = "Test request";
        ReActContext context = new ReActContext(userRequest, getProject());

        // When
        context.incrementIteration();
        context.incrementIteration();
        context.incrementIteration();

        // Then
        assertThat(context.getIterationCount()).isEqualTo(3);
    }

    @Test
    public void testReActContextTracksThinkingSteps() {
        // Given
        String userRequest = "Test request";
        ReActContext context = new ReActContext(userRequest, getProject());

        // When
        context.addThinking(new ReActContext.ThinkingStep("First thought", "action1"));
        context.addThinking(new ReActContext.ThinkingStep("Second thought", "action2"));

        // Then
        assertThat(context.getThinkingSteps()).hasSize(2);
        assertThat(context.getLastThinking().getReasoning()).isEqualTo("Second thought");
    }

    @Test
    public void testReActContextTracksActions() {
        // Given
        String userRequest = "Test request";
        ReActContext context = new ReActContext(userRequest, getProject());

        // When
        context.addAction(new ReActContext.ActionStep(
                "createJavaClass",
                "Create Calculator",
                java.util.Map.of("className", "Calculator")
        ));

        // Then
        assertThat(context.getActionSteps()).hasSize(1);
        assertThat(context.getLastAction().getToolName()).isEqualTo("createJavaClass");
    }

    @Test
    public void testReActContextTracksObservations() {
        // Given
        String userRequest = "Test request";
        ReActContext context = new ReActContext(userRequest, getProject());

        // When
        context.addObservation(new ReActContext.ObservationStep(
                true,
                "Class created successfully",
                java.util.List.of(),
                java.util.List.of()
        ));

        // Then
        assertThat(context.getObservationSteps()).hasSize(1);
        assertThat(context.getLastObservation().isSuccess()).isTrue();
    }

    @Test
    public void testReActContextMarksValidationCompleted() {
        // Given
        String userRequest = "Test request";
        ReActContext context = new ReActContext(userRequest, getProject());

        // When
        context.markValidationCompleted();

        // Then
        assertThat(context.isCompletedValidation()).isTrue();
    }

    @Test
    public void testReActContextDetectsErrors() {
        // Given
        String userRequest = "Test request";
        ReActContext context = new ReActContext(userRequest, getProject());

        // When
        context.addObservation(new ReActContext.ObservationStep(
                false,
                "Compilation failed",
                java.util.List.of("Error: Missing import", "Error: Syntax error"),
                java.util.List.of()
        ));

        // Then
        assertThat(context.hasErrors()).isTrue();
        assertThat(context.getAllErrors()).hasSize(2);
    }

    @Test
    public void testReActContextPrepareFixIteration() {
        // Given
        String userRequest = "Test request";
        ReActContext context = new ReActContext(userRequest, getProject());
        java.util.List<String> errors = java.util.List.of("Error 1", "Error 2");

        // When
        context.prepareFixIteration(errors);

        // Then
        assertThat(context.isRequiresFix()).isTrue();
        assertThat(context.getFixReason()).isEqualTo("Compilation errors detected");
        assertThat(context.getObservationSteps()).hasSize(1);
    }

    @Test
    public void testReActResultSuccessCreation() {
        // Given
        ReActContext context = new ReActContext("test", getProject());
        String message = "Task completed successfully";

        // When
        ReActResult result = ReActResult.success(context, message);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFinalMessage()).isEqualTo(message);
        assertThat(result.getStatus()).isEqualTo(ReActResult.ReActStatus.COMPLETED);
    }

    @Test
    public void testReActResultErrorCreation() {
        // Given
        ReActContext context = new ReActContext("test", getProject());
        String errorMessage = "Something went wrong";

        // When
        ReActResult result = ReActResult.error(context, errorMessage);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(result.getStatus()).isEqualTo(ReActResult.ReActStatus.ERROR);
    }

    @Test
    public void testReActResultMaxIterationsReached() {
        // Given
        ReActContext context = new ReActContext("test", getProject());
        context.incrementIteration();
        context.incrementIteration();
        context.incrementIteration();

        // When
        ReActResult result = ReActResult.maxIterationsReached(context);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ReActResult.ReActStatus.MAX_ITERATIONS);
        assertThat(result.getFinalMessage()).contains("Max iterations");
    }

    @Test
    public void testReActResultGetUserMessage() {
        // Given
        ReActContext context = new ReActContext("test", getProject());

        // When - Success
        ReActResult successResult = ReActResult.success(context, "All done");
        String successMessage = successResult.getUserMessage();

        // When - Error
        ReActResult errorResult = ReActResult.error(context, "Failed");
        String errorMessage = errorResult.getUserMessage();

        // Then
        assertThat(successMessage).isEqualTo("All done");
        assertThat(errorMessage).contains("❌");
        assertThat(errorMessage).contains("Failed");
    }

    @Test
    public void testReActContextGetSummary() {
        // Given
        String userRequest = "Test request";
        ReActContext context = new ReActContext(userRequest, getProject());
        context.incrementIteration();
        context.addThinking(new ReActContext.ThinkingStep("Think", "act"));
        context.addAction(new ReActContext.ActionStep("tool", "desc", java.util.Map.of()));

        // When
        String summary = context.getSummary();

        // Then
        assertThat(summary).contains("iterations=1");
        assertThat(summary).contains("thinking=1");
        assertThat(summary).contains("actions=1");
    }

    @Test
    public void testReActContextGetFullHistory() {
        // Given
        String userRequest = "Create a class";
        ReActContext context = new ReActContext(userRequest, getProject());

        context.incrementIteration();
        context.addThinking(new ReActContext.ThinkingStep("I will create a class", "createJavaClass"));
        context.addAction(new ReActContext.ActionStep("createJavaClass", "Create Calculator", java.util.Map.of()));
        context.addObservation(new ReActContext.ObservationStep(true, "Class created", java.util.List.of(), java.util.List.of()));

        // When
        String history = context.getFullHistory();

        // Then
        assertThat(history).contains("ReAct Cycle History");
        assertThat(history).contains("Create a class");
        assertThat(history).contains("THINK:");
        assertThat(history).contains("ACT:");
        assertThat(history).contains("OBSERVE:");
        assertThat(history).contains("✅");
    }
}
