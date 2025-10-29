package fr.baretto.ollamassist.core.agent.validation;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.agent.execution.BuildOperationExecutor;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Asynchronous compilation validator
 * Compiles code in background to reduce latency
 */
@Slf4j
public class AsyncCompilationValidator {

    private final Project project;
    private final BuildOperationExecutor buildExecutor;
    private final ExecutorService compilationExecutor;
    private CompletableFuture<ValidationResult> lastCompilation;
    private volatile boolean isCompiling = false;

    public AsyncCompilationValidator(Project project, BuildOperationExecutor buildExecutor) {
        this.project = project;
        this.buildExecutor = buildExecutor;
        this.compilationExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "AsyncCompilation-" + project.getName());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Triggers async compilation in background
     */
    public void triggerAsyncCompilation() {
        if (isCompiling) {
            log.debug("Compilation already in progress, skipping trigger");
            return;
        }

        log.info("Triggering async compilation");
        isCompiling = true;

        lastCompilation = CompletableFuture.supplyAsync(() -> {
            try {
                return performCompilation();
            } catch (Exception e) {
                log.error("Error during async compilation", e);
                return ValidationResult.failed("Async compilation error: " + e.getMessage());
            } finally {
                isCompiling = false;
            }
        }, compilationExecutor);
    }

    /**
     * Gets the last compilation result
     * Blocks if compilation is still in progress
     */
    public ValidationResult getLastCompilationResult() {
        if (lastCompilation == null) {
            log.warn("No compilation has been triggered yet, performing synchronous compilation");
            return performCompilation();
        }

        try {
            // Wait for compilation to complete (with timeout)
            ValidationResult result = lastCompilation.get(120, TimeUnit.SECONDS);
            log.debug("Retrieved async compilation result: {}", result.isSuccess());
            return result;
        } catch (Exception e) {
            log.error("Error getting compilation result", e);
            return ValidationResult.failed("Failed to get compilation result: " + e.getMessage());
        }
    }

    /**
     * Gets the last compilation result without blocking
     * Returns null if compilation is still in progress
     */
    public ValidationResult getLastCompilationResultNonBlocking() {
        if (lastCompilation == null || !lastCompilation.isDone()) {
            return null;
        }

        try {
            return lastCompilation.getNow(null);
        } catch (Exception e) {
            log.error("Error getting non-blocking compilation result", e);
            return null;
        }
    }

    /**
     * Checks if compilation is currently in progress
     */
    public boolean isCompilationInProgress() {
        return isCompiling;
    }

    /**
     * Performs the actual compilation
     */
    private ValidationResult performCompilation() {
        log.debug("Performing compilation");

        try {
            Task compileTask = Task.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .description("Async compilation validation")
                    .type(Task.TaskType.BUILD_OPERATION)
                    .priority(Task.TaskPriority.HIGH)
                    .parameters(Map.of("operation", "compile"))
                    .createdAt(LocalDateTime.now())
                    .build();

            TaskResult result = buildExecutor.execute(compileTask);

            if (result.isSuccess()) {
                log.debug("Compilation successful");
                return ValidationResult.success("Compilation successful");
            } else {
                log.debug("Compilation failed: {}", result.getErrorMessage());
                return ValidationResult.failed(
                        "Compilation failed",
                        extractErrors(result)
                );
            }

        } catch (Exception e) {
            log.error("Exception during compilation", e);
            return ValidationResult.failed("Compilation exception: " + e.getMessage());
        }
    }

    /**
     * Extracts error messages from compilation result
     */
    private java.util.List<String> extractErrors(TaskResult result) {
        java.util.List<String> errors = new java.util.ArrayList<>();

        if (result.getErrorMessage() != null) {
            String errorMessage = result.getErrorMessage();
            String[] lines = errorMessage.split("\n");

            for (String line : lines) {
                if (line.contains("error:") || line.contains("ERROR") || line.contains("failed")) {
                    errors.add(line.trim());
                }
            }
        }

        if (errors.isEmpty() && result.getErrorMessage() != null) {
            errors.add(result.getErrorMessage());
        }

        return errors;
    }

    /**
     * Cancels ongoing compilation
     */
    public void cancelCompilation() {
        if (lastCompilation != null && !lastCompilation.isDone()) {
            log.info("Cancelling ongoing compilation");
            lastCompilation.cancel(true);
            isCompiling = false;
        }
    }

    /**
     * Waits for compilation to complete with timeout
     */
    public boolean awaitCompletion(long timeout, TimeUnit unit) {
        if (lastCompilation == null) {
            return true;
        }

        try {
            lastCompilation.get(timeout, unit);
            return true;
        } catch (Exception e) {
            log.warn("Timeout waiting for compilation to complete", e);
            return false;
        }
    }

    /**
     * Cleans up resources
     */
    public void shutdown() {
        log.info("Shutting down async compilation validator");

        cancelCompilation();

        compilationExecutor.shutdown();
        try {
            if (!compilationExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                compilationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            compilationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Gets compilation status summary
     */
    public String getStatus() {
        if (isCompiling) {
            return "Compilation in progress...";
        }
        if (lastCompilation == null) {
            return "No compilation triggered yet";
        }
        if (lastCompilation.isDone()) {
            ValidationResult result = getLastCompilationResultNonBlocking();
            return result != null && result.isSuccess()
                    ? "Last compilation: SUCCESS"
                    : "Last compilation: FAILED";
        }
        return "Compilation pending...";
    }
}
