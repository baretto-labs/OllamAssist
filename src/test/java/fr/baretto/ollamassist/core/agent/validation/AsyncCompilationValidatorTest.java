package fr.baretto.ollamassist.core.agent.validation;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import fr.baretto.ollamassist.core.agent.execution.BuildOperationExecutor;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for AsyncCompilationValidator
 */
public class AsyncCompilationValidatorTest extends BasePlatformTestCase {

    private AsyncCompilationValidator validator;
    private BuildOperationExecutor mockBuildExecutor;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        mockBuildExecutor = mock(BuildOperationExecutor.class);
        validator = new AsyncCompilationValidator(getProject(), mockBuildExecutor);
    }

    @Override
    @AfterEach
    protected void tearDown() throws Exception {
        if (validator != null) {
            validator.shutdown();
        }
        super.tearDown();
    }

    @Test
    void testTriggerAsyncCompilation() {
        // When
        validator.triggerAsyncCompilation();

        // Then
        assertThat(validator.isCompilationInProgress()).isTrue();
    }

    @Test
    void testGetLastCompilationResultWhenNoneTriggered() {
        // Given
        when(mockBuildExecutor.execute(any())).thenReturn(TaskResult.success("Compilation successful"));

        // When
        ValidationResult result = validator.getLastCompilationResult();

        // Then
        assertThat(result).isNotNull();
        verify(mockBuildExecutor, times(1)).execute(any());
    }

    @Test
    void testGetLastCompilationResultNonBlocking() {
        // When - No compilation triggered
        ValidationResult result = validator.getLastCompilationResultNonBlocking();

        // Then
        assertThat(result).isNull();
    }

    @Test
    void testCompilationSuccessful() {
        // Given
        when(mockBuildExecutor.execute(any())).thenReturn(TaskResult.success("Compilation successful"));

        // When
        validator.triggerAsyncCompilation();
        ValidationResult result = validator.getLastCompilationResult();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Compilation successful");
    }

    @Test
    void testCompilationFailed() {
        // Given
        String errorMessage = "error: cannot find symbol\n  symbol: class List\nerror: package java.util does not exist";
        when(mockBuildExecutor.execute(any())).thenReturn(TaskResult.failure(errorMessage));

        // When
        validator.triggerAsyncCompilation();
        ValidationResult result = validator.getLastCompilationResult();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    void testCancelCompilation() {
        // Given
        when(mockBuildExecutor.execute(any())).thenAnswer(invocation -> {
            Thread.sleep(5000); // Simulate long compilation
            return TaskResult.success("Done");
        });

        // When
        validator.triggerAsyncCompilation();
        validator.cancelCompilation();

        // Then
        assertThat(validator.isCompilationInProgress()).isFalse();
    }

    @Test
    void testAwaitCompletionWithTimeout() throws Exception {
        // Given
        when(mockBuildExecutor.execute(any())).thenReturn(TaskResult.success("Compilation successful"));

        // When
        validator.triggerAsyncCompilation();
        boolean completed = validator.awaitCompletion(5, TimeUnit.SECONDS);

        // Then
        assertThat(completed).isTrue();
    }

    @Test
    void testGetStatusWhenNoCompilation() {
        // When
        String status = validator.getStatus();

        // Then
        assertThat(status).contains("No compilation triggered");
    }

    @Test
    void testGetStatusWhenCompilationInProgress() {
        // Given
        when(mockBuildExecutor.execute(any())).thenAnswer(invocation -> {
            Thread.sleep(100);
            return TaskResult.success("Done");
        });

        // When
        validator.triggerAsyncCompilation();
        String status = validator.getStatus();

        // Then
        assertThat(status).contains("progress");
    }

    @Test
    void testGetStatusAfterSuccessfulCompilation() {
        // Given
        when(mockBuildExecutor.execute(any())).thenReturn(TaskResult.success("Compilation successful"));

        // When
        validator.triggerAsyncCompilation();
        validator.getLastCompilationResult(); // Wait for completion
        String status = validator.getStatus();

        // Then
        assertThat(status).contains("SUCCESS");
    }

    @Test
    void testGetStatusAfterFailedCompilation() {
        // Given
        when(mockBuildExecutor.execute(any())).thenReturn(TaskResult.failure("Compilation failed"));

        // When
        validator.triggerAsyncCompilation();
        validator.getLastCompilationResult(); // Wait for completion
        String status = validator.getStatus();

        // Then
        assertThat(status).contains("FAILED");
    }

    @Test
    void testShutdownCleansUpResources() throws Exception {
        // Given
        when(mockBuildExecutor.execute(any())).thenReturn(TaskResult.success("Done"));
        validator.triggerAsyncCompilation();

        // When
        validator.shutdown();

        // Then
        // No exception should be thrown
        assertThat(validator).isNotNull();
    }

    @Test
    void testMultipleTriggersIgnoredWhileCompiling() {
        // Given
        when(mockBuildExecutor.execute(any())).thenAnswer(invocation -> {
            Thread.sleep(100);
            return TaskResult.success("Done");
        });

        // When
        validator.triggerAsyncCompilation();
        validator.triggerAsyncCompilation(); // Should be ignored
        validator.triggerAsyncCompilation(); // Should be ignored

        // Then
        // Only one compilation should occur
        validator.awaitCompletion(2, TimeUnit.SECONDS);
        verify(mockBuildExecutor, times(1)).execute(any());
    }

    @Test
    void testErrorExtractionFromCompilationResult() {
        // Given
        String errorMessage = "BUILD FAILED\nerror: cannot find symbol\nerror: missing return statement\nERROR: compilation failed";
        when(mockBuildExecutor.execute(any())).thenReturn(TaskResult.failure(errorMessage));

        // When
        validator.triggerAsyncCompilation();
        ValidationResult result = validator.getLastCompilationResult();

        // Then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testCompilationExceptionHandling() {
        // Given
        when(mockBuildExecutor.execute(any())).thenThrow(new RuntimeException("Executor crashed"));

        // When
        validator.triggerAsyncCompilation();
        ValidationResult result = validator.getLastCompilationResult();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).containsIgnoringCase("exception");
    }
}
