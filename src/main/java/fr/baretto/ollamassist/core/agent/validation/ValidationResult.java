package fr.baretto.ollamassist.core.agent.validation;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Result of a validation operation
 */
@Value
@Builder
public class ValidationResult {
    boolean success;
    String message;
    List<String> errors;
    List<String> warnings;
    String diagnostics;

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }

    public static ValidationResult success() {
        return ValidationResult.builder()
                .success(true)
                .message("Validation passed successfully")
                .build();
    }

    public static ValidationResult success(String message) {
        return ValidationResult.builder()
                .success(true)
                .message(message)
                .build();
    }

    public static ValidationResult failed(String message) {
        return ValidationResult.builder()
                .success(false)
                .message(message)
                .build();
    }

    public static ValidationResult failed(String message, List<String> errors) {
        return ValidationResult.builder()
                .success(false)
                .message(message)
                .errors(errors)
                .build();
    }

    public static ValidationResult withWarnings(String message, List<String> warnings) {
        return ValidationResult.builder()
                .success(true)
                .message(message)
                .warnings(warnings)
                .build();
    }

    public static ValidationResult unknown() {
        return ValidationResult.builder()
                .success(false)
                .message("Validation status unknown")
                .build();
    }

    public String getFormattedErrors() {
        if (!hasErrors()) {
            return "";
        }
        return String.join("\n", errors);
    }

    public String getFormattedWarnings() {
        if (!hasWarnings()) {
            return "";
        }
        return String.join("\n", warnings);
    }
}
