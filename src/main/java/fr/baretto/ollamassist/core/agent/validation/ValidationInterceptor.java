package fr.baretto.ollamassist.core.agent.validation;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.agent.execution.BuildOperationExecutor;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validation interceptor that automatically validates code after creation/modification
 * Ensures compilation check is performed programmatically
 */
@Slf4j
public class ValidationInterceptor {

    private final Project project;
    private final BuildOperationExecutor buildExecutor;
    private final AsyncCompilationValidator asyncValidator;

    public ValidationInterceptor(Project project) {
        this.project = project;
        this.buildExecutor = new BuildOperationExecutor(project);
        this.asyncValidator = new AsyncCompilationValidator(project, buildExecutor);
    }

    /**
     * Determines if an action requires compilation validation
     */
    public boolean requiresCompilationCheck(String toolName, TaskResult result) {
        if (result != null && !result.isSuccess()) {
            return false; // Don't validate if action failed
        }

        // Tools that modify Java code require compilation check
        return toolName.matches("createJavaClass|createFile|modifyCode|updateFile");
    }

    /**
     * Automatically validates code after an action
     * Uses async compilation to reduce latency
     */
    public ValidationResult autoValidate(String toolName, TaskResult actionResult) {
        log.info("Auto-validating after {}", toolName);

        try {
            // Trigger async compilation
            asyncValidator.triggerAsyncCompilation();

            // Get the result (will wait if not ready)
            ValidationResult compilationResult = asyncValidator.getLastCompilationResult();

            if (compilationResult.isSuccess()) {
                log.info("Auto-validation passed for {}", toolName);
                return ValidationResult.success("Code compiles successfully");
            } else {
                log.warn("Auto-validation failed for {}: {}", toolName, compilationResult.getMessage());

                // Get detailed diagnostics
                TaskResult diagnostics = getDiagnostics();
                List<String> errors = extractErrors(diagnostics);

                return ValidationResult.failed(
                        "Compilation failed after " + toolName,
                        errors
                );
            }

        } catch (Exception e) {
            log.error("Error during auto-validation for {}", toolName, e);
            return ValidationResult.failed("Validation error: " + e.getMessage());
        }
    }

    /**
     * Validates code synchronously (blocking)
     * Used when async compilation is not desired
     */
    public ValidationResult validateSync() {
        log.debug("Synchronous validation started");

        try {
            // 1. Compile
            Task compileTask = Task.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .description("Compile project for validation")
                    .type(Task.TaskType.BUILD_OPERATION)
                    .priority(Task.TaskPriority.HIGH)
                    .parameters(Map.of("operation", "compile"))
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            TaskResult compileResult = buildExecutor.execute(compileTask);

            if (compileResult.isSuccess()) {
                log.debug("Synchronous compilation passed");
                return ValidationResult.success("Compilation successful");
            }

            // 2. Get diagnostics if compilation failed
            TaskResult diagnostics = getDiagnostics();
            List<String> errors = extractErrors(diagnostics);

            log.debug("Synchronous compilation failed with {} errors", errors.size());
            return ValidationResult.failed("Compilation failed", errors);

        } catch (Exception e) {
            log.error("Error during synchronous validation", e);
            return ValidationResult.failed("Validation error: " + e.getMessage());
        }
    }

    /**
     * Gets detailed compilation diagnostics
     */
    private TaskResult getDiagnostics() {
        Task diagnosticsTask = Task.builder()
                .id(java.util.UUID.randomUUID().toString())
                .description("Get compilation diagnostics")
                .type(Task.TaskType.BUILD_OPERATION)
                .priority(Task.TaskPriority.HIGH)
                .parameters(Map.of("operation", "diagnostics"))
                .createdAt(java.time.LocalDateTime.now())
                .build();

        return buildExecutor.execute(diagnosticsTask);
    }

    /**
     * Extracts error messages from diagnostics result
     */
    private List<String> extractErrors(TaskResult diagnostics) {
        List<String> errors = new ArrayList<>();

        if (diagnostics.getMessage() != null) {
            String message = diagnostics.getMessage();

            // Parse Gradle/Maven error format
            String[] lines = message.split("\n");
            for (String line : lines) {
                if (line.contains("error:") || line.contains("ERROR")) {
                    errors.add(line.trim());
                }
            }
        }

        if (errors.isEmpty() && diagnostics.getErrorMessage() != null) {
            errors.add(diagnostics.getErrorMessage());
        }

        return errors;
    }

    /**
     * Formats validation result for agent feedback
     */
    public String formatValidationFeedback(ValidationResult validation, String originalMessage) {
        if (validation.isSuccess()) {
            return String.format("%s\nCode validated - compilation successful", originalMessage);
        }

        StringBuilder feedback = new StringBuilder();
        feedback.append(originalMessage).append("\n\n");
        feedback.append("️ Compilation validation failed:\n\n");

        if (validation.hasErrors()) {
            feedback.append("Errors to fix:\n");
            for (String error : validation.getErrors()) {
                feedback.append("  - ").append(error).append("\n");
            }
        }

        if (validation.hasWarnings()) {
            feedback.append("\n️ Warnings:\n");
            for (String warning : validation.getWarnings()) {
                feedback.append("  - ").append(warning).append("\n");
            }
        }

        feedback.append("\nPlease fix these issues before continuing.");

        return feedback.toString();
    }

    /**
     * Gets the async compilation validator
     */
    public AsyncCompilationValidator getAsyncValidator() {
        return asyncValidator;
    }

    /**
     * Cleans up resources
     */
    public void cleanup() {
        if (asyncValidator != null) {
            asyncValidator.shutdown();
        }
    }
}
