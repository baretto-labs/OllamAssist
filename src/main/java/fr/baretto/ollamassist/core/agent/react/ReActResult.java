package fr.baretto.ollamassist.core.agent.react;

import lombok.Getter;

/**
 * Result of a ReAct cycle execution
 */
@Getter
public class ReActResult {

    private final ReActContext context;
    private final boolean success;
    private final String finalMessage;
    private final ReActStatus status;
    private final String errorMessage;

    private ReActResult(ReActContext context, boolean success, String finalMessage,
                        ReActStatus status, String errorMessage) {
        this.context = context;
        this.success = success;
        this.finalMessage = finalMessage;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public static ReActResult success(ReActContext context, String finalMessage) {
        return new ReActResult(context, true, finalMessage, ReActStatus.COMPLETED, null);
    }

    public static ReActResult error(ReActContext context, String errorMessage) {
        return new ReActResult(context, false, null, ReActStatus.ERROR, errorMessage);
    }

    public static ReActResult maxIterationsReached(ReActContext context) {
        String message = String.format(
                "⚠️ Max iterations (%d) reached. Task partially completed.",
                context.getIterationCount()
        );
        return new ReActResult(context, false, message, ReActStatus.MAX_ITERATIONS, message);
    }

    public static ReActResult cancelled(ReActContext context, String reason) {
        return new ReActResult(context, false, null, ReActStatus.CANCELLED, reason);
    }

    /**
     * Gets a detailed summary of the result
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("=== ReAct Result ===\n");
        summary.append("Status: ").append(status).append("\n");
        summary.append("Success: ").append(success).append("\n");
        summary.append("Iterations: ").append(context.getIterationCount()).append("\n");
        summary.append("Actions executed: ").append(context.getActionSteps().size()).append("\n");

        if (success) {
            summary.append("Final message: ").append(finalMessage).append("\n");
        } else {
            summary.append("Error: ").append(errorMessage).append("\n");
        }

        if (context.hasErrors()) {
            summary.append("\nRemaining errors:\n");
            for (String error : context.getAllErrors()) {
                summary.append("  - ").append(error).append("\n");
            }
        }

        return summary.toString();
    }

    /**
     * Gets a user-friendly message
     */
    public String getUserMessage() {
        if (success) {
            return finalMessage;
        }

        return switch (status) {
            case ERROR -> "❌ Une erreur s'est produite: " + errorMessage;
            case MAX_ITERATIONS -> "⚠️ Limite d'itérations atteinte. La tâche est partiellement complétée.";
            case CANCELLED -> "🛑 Opération annulée: " + errorMessage;
            default -> "❌ Échec de l'exécution";
        };
    }

    /**
     * ReAct execution status
     */
    public enum ReActStatus {
        COMPLETED,          // Successfully completed
        ERROR,              // Error occurred
        MAX_ITERATIONS,     // Max iterations reached
        CANCELLED           // Execution cancelled
    }
}
