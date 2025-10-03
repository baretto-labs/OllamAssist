package fr.baretto.ollamassist.core.agent.react;

import com.intellij.openapi.project.Project;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Context for ReAct cycle execution
 * Tracks thinking, actions, observations and state
 */
@Slf4j
@Getter
public class ReActContext {

    private final String originalRequest;
    private final Project project;
    private final LocalDateTime startTime;

    private final List<ThinkingStep> thinkingSteps = new ArrayList<>();
    private final List<ActionStep> actionSteps = new ArrayList<>();
    private final List<ObservationStep> observationSteps = new ArrayList<>();

    private boolean completedValidation = false;
    private boolean requiresFix = false;
    private String fixReason;
    private int iterationCount = 0;

    public ReActContext(String originalRequest, Project project) {
        this.originalRequest = originalRequest;
        this.project = project;
        this.startTime = LocalDateTime.now();
    }

    /**
     * Adds a thinking step to the context
     */
    public void addThinking(ThinkingStep thinking) {
        thinkingSteps.add(thinking);
        log.debug("Added thinking step: {}", thinking.getReasoning());
    }

    /**
     * Adds an action step to the context
     */
    public void addAction(ActionStep action) {
        actionSteps.add(action);
        log.debug("Added action step: {} - {}", action.getToolName(), action.getDescription());
    }

    /**
     * Adds an observation step to the context
     */
    public void addObservation(ObservationStep observation) {
        observationSteps.add(observation);
        log.debug("Added observation step: success={}", observation.isSuccess());
    }

    /**
     * Marks validation as completed
     */
    public void markValidationCompleted() {
        this.completedValidation = true;
        log.info("✅ Validation marked as completed");
    }

    /**
     * Marks context as requiring fix
     */
    public void markAsRequiringFix(String reason) {
        this.requiresFix = true;
        this.fixReason = reason;
        log.info("🔧 Context marked as requiring fix: {}", reason);
    }

    /**
     * Clears the fix requirement
     */
    public void clearFixRequirement() {
        this.requiresFix = false;
        this.fixReason = null;
    }

    /**
     * Increments iteration count
     */
    public void incrementIteration() {
        this.iterationCount++;
        log.debug("Iteration count: {}", iterationCount);
    }

    /**
     * Gets the last observation
     */
    public ObservationStep getLastObservation() {
        return observationSteps.isEmpty() ? null : observationSteps.get(observationSteps.size() - 1);
    }

    /**
     * Gets the last action
     */
    public ActionStep getLastAction() {
        return actionSteps.isEmpty() ? null : actionSteps.get(actionSteps.size() - 1);
    }

    /**
     * Gets the last thinking
     */
    public ThinkingStep getLastThinking() {
        return thinkingSteps.isEmpty() ? null : thinkingSteps.get(thinkingSteps.size() - 1);
    }

    /**
     * Checks if context has any errors
     */
    public boolean hasErrors() {
        return observationSteps.stream()
                .anyMatch(obs -> !obs.isSuccess() && obs.hasErrors());
    }

    /**
     * Gets all errors from observations
     */
    public List<String> getAllErrors() {
        List<String> allErrors = new ArrayList<>();
        for (ObservationStep obs : observationSteps) {
            if (obs.hasErrors()) {
                allErrors.addAll(obs.getErrors());
            }
        }
        return allErrors;
    }

    /**
     * Checks if the last observation was successful
     */
    public boolean lastObservationSuccessful() {
        ObservationStep last = getLastObservation();
        return last != null && last.isSuccess();
    }

    /**
     * Prepares context for a fix iteration
     */
    public void prepareFixIteration(List<String> errors) {
        markAsRequiringFix("Compilation errors detected");
        ObservationStep fixObservation = new ObservationStep(
                false,
                "Errors detected that need fixing",
                errors,
                new ArrayList<>()
        );
        addObservation(fixObservation);
    }

    /**
     * Gets a summary of the context state
     */
    public String getSummary() {
        return String.format(
                "ReAct Context [iterations=%d, thinking=%d, actions=%d, observations=%d, validation=%s, requiresFix=%s]",
                iterationCount,
                thinkingSteps.size(),
                actionSteps.size(),
                observationSteps.size(),
                completedValidation,
                requiresFix
        );
    }

    /**
     * Gets the full context as a string for debugging
     */
    public String getFullHistory() {
        StringBuilder history = new StringBuilder();
        history.append("=== ReAct Cycle History ===\n");
        history.append("Request: ").append(originalRequest).append("\n");
        history.append("Iterations: ").append(iterationCount).append("\n\n");

        for (int i = 0; i < Math.max(thinkingSteps.size(), Math.max(actionSteps.size(), observationSteps.size())); i++) {
            history.append("--- Iteration ").append(i + 1).append(" ---\n");

            if (i < thinkingSteps.size()) {
                history.append("THINK: ").append(thinkingSteps.get(i).getReasoning()).append("\n");
            }
            if (i < actionSteps.size()) {
                ActionStep action = actionSteps.get(i);
                history.append("ACT: ").append(action.getToolName())
                        .append(" - ").append(action.getDescription()).append("\n");
            }
            if (i < observationSteps.size()) {
                ObservationStep obs = observationSteps.get(i);
                history.append("OBSERVE: ").append(obs.isSuccess() ? "✅" : "❌")
                        .append(" ").append(obs.getResult()).append("\n");
            }
            history.append("\n");
        }

        return history.toString();
    }

    /**
     * Represents a thinking step in the ReAct cycle
     */
    @Getter
    public static class ThinkingStep {
        private final String reasoning;
        private final String nextAction;
        private final boolean hasFinalAnswer;
        private final String finalAnswer;
        private final LocalDateTime timestamp;

        public ThinkingStep(String reasoning, String nextAction) {
            this.reasoning = reasoning;
            this.nextAction = nextAction;
            this.hasFinalAnswer = false;
            this.finalAnswer = null;
            this.timestamp = LocalDateTime.now();
        }

        public ThinkingStep(String reasoning, boolean hasFinalAnswer, String finalAnswer) {
            this.reasoning = reasoning;
            this.nextAction = null;
            this.hasFinalAnswer = hasFinalAnswer;
            this.finalAnswer = finalAnswer;
            this.timestamp = LocalDateTime.now();
        }
    }

    /**
     * Represents an action step in the ReAct cycle
     */
    @Getter
    public static class ActionStep {
        private final String toolName;
        private final String description;
        private final java.util.Map<String, Object> parameters;
        private final LocalDateTime timestamp;

        public ActionStep(String toolName, String description, java.util.Map<String, Object> parameters) {
            this.toolName = toolName;
            this.description = description;
            this.parameters = parameters;
            this.timestamp = LocalDateTime.now();
        }
    }

    /**
     * Represents an observation step in the ReAct cycle
     */
    @Getter
    public static class ObservationStep {
        private final boolean success;
        private final String result;
        private final List<String> errors;
        private final List<String> warnings;
        private final LocalDateTime timestamp;

        public ObservationStep(boolean success, String result, List<String> errors, List<String> warnings) {
            this.success = success;
            this.result = result;
            this.errors = errors != null ? errors : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
            this.timestamp = LocalDateTime.now();
        }

        public boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }
    }
}
