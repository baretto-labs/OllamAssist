package fr.baretto.ollamassist.core.agent.validation;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ValidationInterceptor
 */
public class ValidationInterceptorTest extends BasePlatformTestCase {

    private ValidationInterceptor validationInterceptor;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        validationInterceptor = new ValidationInterceptor(getProject());
    }

    @Override
    @AfterEach
    protected void tearDown() throws Exception {
        if (validationInterceptor != null) {
            validationInterceptor.cleanup();
        }
        super.tearDown();
    }

    @Test
    public void testRequiresCompilationCheckForJavaClass() {
        // Given
        TaskResult successResult = TaskResult.success("File created");

        // When
        boolean requires = validationInterceptor.requiresCompilationCheck("createJavaClass", successResult);

        // Then
        assertThat(requires).isTrue();
    }

    @Test
    public void testRequiresCompilationCheckForCreateFile() {
        // Given
        TaskResult successResult = TaskResult.success("File created");

        // When
        boolean requires = validationInterceptor.requiresCompilationCheck("createFile", successResult);

        // Then
        assertThat(requires).isTrue();
    }

    @Test
    public void testDoesNotRequireCompilationCheckForFailedTask() {
        // Given
        TaskResult failedResult = TaskResult.failure("Task failed");

        // When
        boolean requires = validationInterceptor.requiresCompilationCheck("createJavaClass", failedResult);

        // Then
        assertThat(requires).isFalse();
    }

    @Test
    public void testDoesNotRequireCompilationCheckForNonCodeOperations() {
        // Given
        TaskResult successResult = TaskResult.success("Operation successful");

        // When
        boolean requiresGit = validationInterceptor.requiresCompilationCheck("executeGitCommand", successResult);
        boolean requiresBuild = validationInterceptor.requiresCompilationCheck("buildProject", successResult);
        boolean requiresSearch = validationInterceptor.requiresCompilationCheck("searchWeb", successResult);

        // Then
        assertThat(requiresGit).isFalse();
        assertThat(requiresBuild).isFalse();
        assertThat(requiresSearch).isFalse();
    }

    @Test
    public void testFormatValidationFeedbackForSuccess() {
        // Given
        ValidationResult validation = ValidationResult.success("Compilation successful");
        String originalMessage = "File created";

        // When
        String feedback = validationInterceptor.formatValidationFeedback(validation, originalMessage);

        // Then
        assertThat(feedback).contains("File created");
        assertThat(feedback).contains("✅");
        assertThat(feedback).contains("validated");
    }

    @Test
    public void testFormatValidationFeedbackForFailure() {
        // Given
        ValidationResult validation = ValidationResult.failed(
                "Compilation failed",
                java.util.List.of("Error: Missing import java.util.List", "Error: Unknown symbol String")
        );
        String originalMessage = "File created";

        // When
        String feedback = validationInterceptor.formatValidationFeedback(validation, originalMessage);

        // Then
        assertThat(feedback).contains("File created");
        assertThat(feedback).contains("⚠️");
        assertThat(feedback).contains("Compilation validation failed");
        assertThat(feedback).contains("Missing import");
        assertThat(feedback).contains("Unknown symbol");
    }

    @Test
    public void testAsyncValidatorIsInitialized() {
        // When
        AsyncCompilationValidator asyncValidator = validationInterceptor.getAsyncValidator();

        // Then
        assertThat(asyncValidator).isNotNull();
    }

    @Test
    public void testCleanupReleasesResources() {
        // Given
        AsyncCompilationValidator asyncValidator = validationInterceptor.getAsyncValidator();

        // When
        validationInterceptor.cleanup();

        // Then
        // No exception should be thrown
        assertThat(asyncValidator).isNotNull();
    }
}
