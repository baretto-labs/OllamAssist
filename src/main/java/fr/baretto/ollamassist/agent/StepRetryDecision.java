package fr.baretto.ollamassist.agent;

/**
 * User decision when a step fails during agent execution.
 *
 * <p>Published via {@link fr.baretto.ollamassist.events.StepRetryNotifier}
 * and consumed by {@link AgentOrchestrator} to decide how to proceed.
 */
public enum StepRetryDecision {
    /** Re-dispatch the failed step with the same parameters. */
    RETRY,
    /** Skip this step and continue executing the remaining steps in the phase. */
    SKIP,
    /** Stop execution of the current phase; let the Critic decide recovery. */
    ABORT_PHASE
}
