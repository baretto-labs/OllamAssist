package fr.baretto.ollamassist.agent.observability;

/**
 * Execution state of a single step.
 */
public enum StepState {
    /** Step not yet started */
    PENDING,

    /** Step currently executing */
    RUNNING,

    /** Step completed successfully */
    COMPLETED,

    /** Step failed with error */
    FAILED
}
