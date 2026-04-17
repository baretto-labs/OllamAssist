package fr.baretto.ollamassist.agent;

import fr.baretto.ollamassist.agent.plan.AgentPlan;
import fr.baretto.ollamassist.agent.plan.Step;
import org.jetbrains.annotations.Nullable;

public final class AgentProgressEvent {

    public enum Type {
        PLANNING,
        PLAN_READY,
        STEP_STARTED,
        STEP_COMPLETED,
        STEP_FAILED,
        CRITIC_THINKING,
        PLAN_ADAPTED,
        COMPLETED,
        ABORTED
    }

    private final Type type;
    private final String message;
    @Nullable
    private final AgentPlan plan;
    @Nullable
    private final Step step;
    /** Tool output for STEP_COMPLETED events. Null for all other event types. */
    @Nullable
    private final String output;

    private AgentProgressEvent(Type type, String message, @Nullable AgentPlan plan, @Nullable Step step, @Nullable String output) {
        this.type = type;
        this.message = message;
        this.plan = plan;
        this.step = step;
        this.output = output;
    }

    public static AgentProgressEvent planning() {
        return new AgentProgressEvent(Type.PLANNING, "Generating execution plan...", null, null, null);
    }

    public static AgentProgressEvent planReady(AgentPlan plan) {
        return new AgentProgressEvent(Type.PLAN_READY, "Plan ready (" + plan.totalSteps() + " steps)", plan, null, null);
    }

    public static AgentProgressEvent stepStarted(Step step) {
        return new AgentProgressEvent(Type.STEP_STARTED, step.getDescription(), null, step, null);
    }

    /** Marks a step as completed, carrying the tool output for display in the UI. */
    public static AgentProgressEvent stepCompleted(Step step, String output) {
        return new AgentProgressEvent(Type.STEP_COMPLETED, "Done: " + step.getDescription(), null, step, output);
    }

    public static AgentProgressEvent stepFailed(Step step, String error) {
        // error is stored in the output field so the UI can display it independently from the description
        return new AgentProgressEvent(Type.STEP_FAILED, "Failed: " + step.getDescription(), null, step, error);
    }

    public static AgentProgressEvent criticThinking(String phaseDescription) {
        String msg = phaseDescription != null && !phaseDescription.isBlank()
                ? "Evaluating phase: " + phaseDescription
                : "Evaluating progress...";
        return new AgentProgressEvent(Type.CRITIC_THINKING, msg, null, null, null);
    }

    public static AgentProgressEvent planAdapted(AgentPlan revisedPlan) {
        return new AgentProgressEvent(Type.PLAN_ADAPTED, "Plan adapted (" + revisedPlan.totalSteps() + " remaining steps)", revisedPlan, null, null);
    }

    public static AgentProgressEvent completed() {
        return new AgentProgressEvent(Type.COMPLETED, "Task completed", null, null, null);
    }

    public static AgentProgressEvent completed(int stepCount) {
        String msg = stepCount > 0
                ? "Task completed — " + stepCount + " step(s) executed"
                : "Task completed";
        return new AgentProgressEvent(Type.COMPLETED, msg, null, null, null);
    }

    public static AgentProgressEvent aborted(String reason) {
        return new AgentProgressEvent(Type.ABORTED, "Aborted: " + reason, null, null, null);
    }

    public Type getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    @Nullable
    public AgentPlan getPlan() {
        return plan;
    }

    @Nullable
    public Step getStep() {
        return step;
    }

    /** Tool output — non-null only for {@link Type#STEP_COMPLETED} events. */
    @Nullable
    public String getOutput() {
        return output;
    }

    @Override
    public String toString() {
        return "AgentProgressEvent{type=" + type + ", message='" + message + "'}";
    }
}
