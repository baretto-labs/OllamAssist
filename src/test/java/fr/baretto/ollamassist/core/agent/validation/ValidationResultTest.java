package fr.baretto.ollamassist.core.agent.validation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ValidationResult
 */
public class ValidationResultTest {

    @Test
    public void testSuccessCreation() {
        // When
        ValidationResult result = ValidationResult.success();

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isNotNull();
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.hasWarnings()).isFalse();
    }

    @Test
    public void testSuccessWithMessage() {
        // Given
        String message = "Compilation successful";

        // When
        ValidationResult result = ValidationResult.success(message);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo(message);
    }

    @Test
    public void testFailedCreation() {
        // Given
        String message = "Compilation failed";

        // When
        ValidationResult result = ValidationResult.failed(message);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo(message);
    }

    @Test
    public void testFailedWithErrors() {
        // Given
        String message = "Compilation failed";
        List<String> errors = Arrays.asList("Error 1", "Error 2", "Error 3");

        // When
        ValidationResult result = ValidationResult.failed(message, errors);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo(message);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).hasSize(3);
        assertThat(result.getErrors()).containsExactly("Error 1", "Error 2", "Error 3");
    }

    @Test
    public void testWithWarnings() {
        // Given
        String message = "Compilation successful with warnings";
        List<String> warnings = Arrays.asList("Warning 1", "Warning 2");

        // When
        ValidationResult result = ValidationResult.withWarnings(message, warnings);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo(message);
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.getWarnings()).hasSize(2);
    }

    @Test
    public void testUnknown() {
        // When
        ValidationResult result = ValidationResult.unknown();

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("unknown");
    }

    @Test
    public void testHasErrorsWhenNoErrors() {
        // Given
        ValidationResult result = ValidationResult.success();

        // When
        boolean hasErrors = result.hasErrors();

        // Then
        assertThat(hasErrors).isFalse();
    }

    @Test
    public void testHasErrorsWhenEmptyList() {
        // Given
        ValidationResult result = ValidationResult.failed("Failed", Arrays.asList());

        // When
        boolean hasErrors = result.hasErrors();

        // Then
        assertThat(hasErrors).isFalse();
    }

    @Test
    public void testHasErrorsWhenNull() {
        // Given
        ValidationResult result = ValidationResult.builder()
                .success(false)
                .message("Failed")
                .errors(null)
                .build();

        // When
        boolean hasErrors = result.hasErrors();

        // Then
        assertThat(hasErrors).isFalse();
    }

    @Test
    public void testHasWarningsWhenNoWarnings() {
        // Given
        ValidationResult result = ValidationResult.success();

        // When
        boolean hasWarnings = result.hasWarnings();

        // Then
        assertThat(hasWarnings).isFalse();
    }

    @Test
    public void testGetFormattedErrors() {
        // Given
        List<String> errors = Arrays.asList(
                "Error: Missing import java.util.List",
                "Error: Cannot find symbol String",
                "Error: Syntax error"
        );
        ValidationResult result = ValidationResult.failed("Failed", errors);

        // When
        String formatted = result.getFormattedErrors();

        // Then
        assertThat(formatted).contains("Missing import");
        assertThat(formatted).contains("Cannot find symbol");
        assertThat(formatted).contains("Syntax error");
        assertThat(formatted.split("\n")).hasSize(3);
    }

    @Test
    public void testGetFormattedErrorsWhenNoErrors() {
        // Given
        ValidationResult result = ValidationResult.success();

        // When
        String formatted = result.getFormattedErrors();

        // Then
        assertThat(formatted).isEmpty();
    }

    @Test
    public void testGetFormattedWarnings() {
        // Given
        List<String> warnings = Arrays.asList(
                "Warning: Unused variable",
                "Warning: Deprecated method"
        );
        ValidationResult result = ValidationResult.withWarnings("Success", warnings);

        // When
        String formatted = result.getFormattedWarnings();

        // Then
        assertThat(formatted).contains("Unused variable");
        assertThat(formatted).contains("Deprecated method");
    }

    @Test
    public void testGetFormattedWarningsWhenNoWarnings() {
        // Given
        ValidationResult result = ValidationResult.success();

        // When
        String formatted = result.getFormattedWarnings();

        // Then
        assertThat(formatted).isEmpty();
    }

    @Test
    public void testBuilderPattern() {
        // Given
        List<String> errors = Arrays.asList("Error 1");
        List<String> warnings = Arrays.asList("Warning 1");

        // When
        ValidationResult result = ValidationResult.builder()
                .success(false)
                .message("Build result")
                .errors(errors)
                .warnings(warnings)
                .diagnostics("Detailed diagnostics")
                .build();

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Build result");
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getWarnings()).hasSize(1);
        assertThat(result.getDiagnostics()).isEqualTo("Detailed diagnostics");
    }

    @Test
    public void testMultipleErrors() {
        // Given
        List<String> errors = Arrays.asList(
                "Error 1: Missing semicolon at line 10",
                "Error 2: Undefined variable at line 15",
                "Error 3: Type mismatch at line 20",
                "Error 4: Missing return statement at line 25"
        );
        ValidationResult result = ValidationResult.failed("Multiple compilation errors", errors);

        // Then
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).hasSize(4);
        assertThat(result.getFormattedErrors()).contains("Missing semicolon");
        assertThat(result.getFormattedErrors()).contains("Undefined variable");
    }

    @Test
    public void testSuccessWithDiagnostics() {
        // Given
        String diagnostics = "Compilation completed in 2.5 seconds\n0 errors, 0 warnings";

        // When
        ValidationResult result = ValidationResult.builder()
                .success(true)
                .message("Compilation successful")
                .diagnostics(diagnostics)
                .build();

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDiagnostics()).contains("2.5 seconds");
        assertThat(result.getDiagnostics()).contains("0 errors");
    }

    @Test
    public void testFailureWithDetailedDiagnostics() {
        // Given
        String diagnostics = "BUILD FAILED\nTotal time: 5 seconds\nCompilation errors: 3\nWarnings: 1";
        List<String> errors = Arrays.asList("Error 1", "Error 2", "Error 3");

        // When
        ValidationResult result = ValidationResult.builder()
                .success(false)
                .message("Build failed")
                .errors(errors)
                .diagnostics(diagnostics)
                .build();

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getDiagnostics()).contains("BUILD FAILED");
        assertThat(result.getDiagnostics()).contains("5 seconds");
    }

    @Test
    public void testImmutability() {
        // Given
        List<String> errors = Arrays.asList("Error 1", "Error 2");
        ValidationResult result = ValidationResult.failed("Failed", errors);

        // When
        List<String> retrievedErrors = result.getErrors();

        // Then - ValidationResult is immutable (Lombok @Value)
        assertThat(retrievedErrors).hasSize(2);
        assertThat(result.getErrors()).hasSize(2);
    }
}
