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

    private AgentProgressEvent(Type type, String message, @Nullable AgentPlan plan, @Nullable Step step) {
        this.type = type;
        this.message = message;
        this.plan = plan;
        this.step = step;
    }

    public static AgentProgressEvent planning() {
        return new AgentProgressEvent(Type.PLANNING, "Generating execution plan...", null, null);
    }

    public static AgentProgressEvent planReady(AgentPlan plan) {
        return new AgentProgressEvent(Type.PLAN_READY, "Plan ready (" + plan.totalSteps() + " steps)", plan, null);
    }

    public static AgentProgressEvent stepStarted(Step step) {
        return new AgentProgressEvent(Type.STEP_STARTED, step.getDescription(), null, step);
    }

    public static AgentProgressEvent stepCompleted(Step step) {
        return new AgentProgressEvent(Type.STEP_COMPLETED, "Done: " + step.getDescription(), null, step);
    }

    public static AgentProgressEvent stepFailed(Step step, String error) {
        return new AgentProgressEvent(Type.STEP_FAILED, "Failed: " + step.getDescription() + " — " + error, null, step);
    }

    public static AgentProgressEvent criticThinking() {
        return new AgentProgressEvent(Type.CRITIC_THINKING, "Evaluating progress...", null, null);
    }

    public static AgentProgressEvent planAdapted(AgentPlan revisedPlan) {
        return new AgentProgressEvent(Type.PLAN_ADAPTED, "Plan adapted (" + revisedPlan.totalSteps() + " remaining steps)", revisedPlan, null);
    }

    public static AgentProgressEvent completed() {
        return new AgentProgressEvent(Type.COMPLETED, "Task completed", null, null);
    }

    public static AgentProgressEvent aborted(String reason) {
        return new AgentProgressEvent(Type.ABORTED, "Aborted: " + reason, null, null);
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

    @Override
    public String toString() {
        return "AgentProgressEvent{type=" + type + ", message='" + message + "'}";
    }
}
