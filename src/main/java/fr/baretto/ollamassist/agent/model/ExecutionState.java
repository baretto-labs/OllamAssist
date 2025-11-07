package fr.baretto.ollamassist.agent.model;

/**
 * Execution state of the entire workflow.
 */
public enum ExecutionState {
    /** Execution not yet started */
    PENDING,

    /** Execution in progress */
    RUNNING,

    /** Execution paused by user */
    PAUSED,

    /** Execution completed successfully */
    COMPLETED,

    /** Execution failed with error */
    FAILED,

    /** Execution cancelled by user */
    CANCELLED;

    /**
     * Returns true if this is a terminal state (execution finished).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
