package fr.baretto.ollamassist.agent;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import fr.baretto.ollamassist.agent.critic.CriticAgent;
import fr.baretto.ollamassist.agent.critic.CriticDecision;
import fr.baretto.ollamassist.agent.plan.AgentPlan;
import fr.baretto.ollamassist.agent.plan.Phase;
import fr.baretto.ollamassist.agent.plan.Step;
import fr.baretto.ollamassist.agent.tools.ToolDispatcher;
import fr.baretto.ollamassist.agent.tools.ToolRegistry;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import fr.baretto.ollamassist.events.ChatModelModifiedNotifier;
import fr.baretto.ollamassist.setting.OllamaSettings;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service(Service.Level.PROJECT)
@Slf4j
public final class AgentOrchestrator implements Disposable {

    private final Project project;
    private volatile PlannerAgent plannerAgent;
    private volatile CriticAgent criticAgent;
    private volatile OllamaChatModel agentModel;
    private volatile ToolDispatcher toolDispatcher;
    private volatile boolean executionCancelled = false;

    public AgentOrchestrator(@NotNull Project project) {
        this.project = project;
        project.getMessageBus().connect(this).subscribe(
                ChatModelModifiedNotifier.TOPIC,
                (ChatModelModifiedNotifier) this::invalidateAgents
        );
    }

    // -------------------------------------------------------------------------
    // Planning
    // -------------------------------------------------------------------------

    public CompletableFuture<AgentPlan> plan(String userGoal) {
        return plan(userGoal, getOrCreatePlannerAgent());
    }

    private String enrichGoalWithMemory(String userGoal) {
        AgentMemoryService memory = project.getService(AgentMemoryService.class);
        if (memory == null) return userGoal;
        String context = memory.recentContextSummary();
        if (context.isBlank()) return userGoal;
        return userGoal + "\n\n--- Recent history (for context only, do not repeat past actions) ---\n" + context;
    }

    private long planTimeoutSeconds() {
        try {
            return OllamaSettings.getInstance().getAgentPlanTimeoutSeconds();
        } catch (Exception e) {
            return 120;
        }
    }

    @TestOnly
    CompletableFuture<AgentPlan> plan(String userGoal, PlannerAgent agent) {
        return CompletableFuture.supplyAsync(() -> {
            publishProgress(AgentProgressEvent.planning());
            try {
                long timeout = planTimeoutSeconds();
                String enrichedGoal = enrichGoalWithMemory(userGoal);
                AgentPlan agentPlan = CompletableFuture
                        .supplyAsync(() -> agent.plan(enrichedGoal))
                        .orTimeout(timeout, TimeUnit.SECONDS)
                        .join();
                validatePlan(agentPlan, userGoal);
                publishProgress(AgentProgressEvent.planReady(agentPlan));
                return agentPlan;
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                String message = cause instanceof TimeoutException
                        ? "Plan generation timed out after " + planTimeoutSeconds() + "s — the model may be unavailable or overloaded"
                        : cause.getMessage();
                log.error("Plan generation failed for goal: {}", userGoal, cause);
                publishProgress(AgentProgressEvent.aborted(message));
                throw new RuntimeException("Plan generation failed", cause);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Execution
    // -------------------------------------------------------------------------

    public CompletableFuture<Void> execute(AgentPlan plan) {
        return execute(plan, getOrCreateToolDispatcher(), getOrCreateCriticAgent());
    }

    /** Cancels the currently running execution, if any. Safe to call at any time. */
    public void cancelExecution() {
        executionCancelled = true;
    }

    private static final int MAX_CRITIC_ADAPTATIONS = 5;

    @TestOnly
    CompletableFuture<Void> execute(AgentPlan plan, ToolDispatcher dispatcher, CriticAgent critic) {
        executionCancelled = false;
        dispatcher.resetRateLimits();
        return CompletableFuture.runAsync(() -> {
            List<Phase> remainingPhases = new ArrayList<>(plan.getPhases());
            String lastStepOutput = "";
            int adaptationCount = 0;
            // Accumulates all step results across all phases for Critic context
            StringBuilder executionLog = new StringBuilder();

            while (!remainingPhases.isEmpty()) {
                if (executionCancelled) {
                    publishProgress(AgentProgressEvent.aborted("Execution cancelled by user."));
                    return;
                }
                Phase phase = remainingPhases.remove(0);
                executionLog.append("=== Phase: ").append(phase.getDescription()).append(" ===\n");

                // Execute all steps in this phase
                StringBuilder phaseResults = new StringBuilder();
                boolean phaseFailed = false;
                for (Step step : phase.getSteps()) {
                    publishProgress(AgentProgressEvent.stepStarted(step));
                    ToolResult result = dispatcher.dispatch(step, lastStepOutput);

                    if (result.isSuccess()) {
                        // Update execution state BEFORE notifying UI — ensures consistency
                        // even if publishProgress() throws (e.g. MessageBus offline).
                        lastStepOutput = result.getOutput() != null ? result.getOutput() : "";
                        String stepLine = "[" + step.getToolId() + "] "
                                + PromptSanitizer.sanitize(lastStepOutput) + "\n";
                        phaseResults.append(stepLine);
                        executionLog.append(stepLine);
                        publishProgress(AgentProgressEvent.stepCompleted(step));
                    } else {
                        String stepLine = "[" + step.getToolId() + "] FAILED: "
                                + PromptSanitizer.sanitize(result.getErrorMessage()) + "\n";
                        phaseResults.append(stepLine);
                        executionLog.append(stepLine);
                        publishProgress(AgentProgressEvent.stepFailed(step, result.getErrorMessage()));
                        phaseFailed = true;
                        break; // Stop remaining steps in this phase, let critic decide
                    }
                }

                // After each phase (success or failure): run the Critic
                publishProgress(AgentProgressEvent.criticThinking());
                String criticPrompt = buildCriticPrompt(plan.getGoal(), phase, phaseResults.toString(),
                        executionLog.toString(), remainingPhases, phaseFailed);
                CriticDecision decision;
                try {
                    decision = critic.evaluate(criticPrompt);
                    log.debug("Critic decision for phase '{}': {}", phase.getDescription(), decision);
                } catch (Exception e) {
                    log.error("Critic returned a malformed response for phase '{}': {}", phase.getDescription(), e.getMessage());
                    publishProgress(AgentProgressEvent.aborted(
                            "Critic returned an invalid response (likely malformed JSON from the LLM). "
                                    + "Phase: " + phase.getDescription() + ". Error: " + e.getMessage()));
                    return;
                }

                switch (decision.getStatus()) {
                    case OK -> { /* continue with remaining phases */ }
                    case ADAPT -> {
                        List<Phase> revised = decision.getRevisedPhases();
                        if (revised.isEmpty()) {
                            log.warn("Critic returned ADAPT but provided no revised phases — treating as OK");
                        } else {
                            adaptationCount++;
                            if (adaptationCount > MAX_CRITIC_ADAPTATIONS) {
                                publishProgress(AgentProgressEvent.aborted(
                                        "Too many plan adaptations (" + MAX_CRITIC_ADAPTATIONS + ") — the agent may be stuck in a loop. Aborting."));
                                return;
                            }
                            AgentPlan revisedPlan = new AgentPlan(plan.getGoal(), decision.getReasoning(), revised);
                            publishProgress(AgentProgressEvent.planAdapted(revisedPlan));
                            remainingPhases = new ArrayList<>(revised);
                        }
                    }
                    case ABORT -> {
                        recordMemory(plan.getGoal(), "ABORTED", decision.getReasoning());
                        publishProgress(AgentProgressEvent.aborted(decision.getReasoning()));
                        return;
                    }
                }
            }

            recordMemory(plan.getGoal(), "COMPLETED", "All phases succeeded.");
            publishProgress(AgentProgressEvent.completed());
        });
    }

    private void recordMemory(String goal, String status, String reason) {
        AgentMemoryService memory = project.getService(AgentMemoryService.class);
        if (memory != null) {
            memory.record(goal, status, reason);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String buildCriticPrompt(String goal, Phase completedPhase, String phaseResults,
                                      String fullExecutionLog, List<Phase> remaining, boolean phaseFailed) {
        String phaseStatus = phaseFailed ? "FAILED (one or more steps failed)" : "completed";
        StringBuilder prompt = new StringBuilder();
        prompt.append("Goal: ").append(PromptSanitizer.sanitizeGoal(goal)).append("\n\n");

        if (fullExecutionLog.length() > phaseResults.length()) {
            // There are results from previous phases — include them for context
            prompt.append("Execution history (all phases so far):\n").append(fullExecutionLog).append("\n");
        } else {
            prompt.append("Current phase ").append(phaseStatus).append(": ").append(completedPhase.getDescription()).append("\n");
            prompt.append("Step results:\n").append(phaseResults).append("\n");
        }

        prompt.append("Remaining phases: ").append(remaining.size()).append("\n\n");
        if (phaseFailed) {
            prompt.append("One or more steps in this phase failed. Evaluate: can the task recover? ")
                    .append("If a different approach is possible, reply ADAPT with revisedPhases. ")
                    .append("If the task cannot continue, reply ABORT with reasoning.");
        } else {
            prompt.append("Evaluate: did this phase succeed and move the task forward? ")
                    .append("If yes, reply OK. If the remaining plan needs adjustment, reply ADAPT with revisedPhases. ")
                    .append("If the task cannot continue, reply ABORT with reasoning.");
        }
        return prompt.toString();
    }

    /** Maximum FILE_DELETE steps allowed in a single plan (G3 blast-radius guard). */
    private static final int MAX_DELETE_STEPS = 3;

    private void validatePlan(AgentPlan plan, String goal) {
        if (plan == null) {
            throw new IllegalStateException("PlannerAgent returned null for goal: " + goal);
        }
        if (plan.isEmpty()) {
            throw new IllegalStateException("PlannerAgent returned an empty plan for goal: " + goal);
        }
        // G3: reject plans with an unusual number of destructive steps
        long deleteCount = plan.getPhases().stream()
                .flatMap(p -> p.getSteps().stream())
                .filter(s -> "FILE_DELETE".equals(s.getToolId()))
                .count();
        if (deleteCount > MAX_DELETE_STEPS) {
            throw new IllegalStateException(
                    "Plan contains " + deleteCount + " FILE_DELETE steps (max allowed: " + MAX_DELETE_STEPS + "). "
                    + "This looks unsafe — please review the plan manually.");
        }
    }

    private PlannerAgent getOrCreatePlannerAgent() {
        if (plannerAgent == null) {
            synchronized (this) {
                if (plannerAgent == null) {
                    plannerAgent = createAiService(PlannerAgent.class);
                }
            }
        }
        return plannerAgent;
    }

    private ToolDispatcher getOrCreateToolDispatcher() {
        if (toolDispatcher == null) {
            synchronized (this) {
                if (toolDispatcher == null) {
                    toolDispatcher = new ToolDispatcher(new ToolRegistry(project), project);
                }
            }
        }
        return toolDispatcher;
    }

    private CriticAgent getOrCreateCriticAgent() {
        if (criticAgent == null) {
            synchronized (this) {
                if (criticAgent == null) {
                    criticAgent = createAiService(CriticAgent.class);
                }
            }
        }
        return criticAgent;
    }

    private OllamaChatModel getOrCreateAgentModel() {
        if (agentModel == null) {
            synchronized (this) {
                if (agentModel == null) {
                    OllamaSettings settings = OllamaSettings.getInstance();
                    agentModel = OllamaChatModel.builder()
                            .baseUrl(settings.getChatOllamaUrl())
                            .modelName(settings.getChatModelName())
                            .responseFormat(ResponseFormat.JSON)
                            .temperature(0.2)
                            .timeout(settings.getTimeoutDuration())
                            .build();
                }
            }
        }
        return agentModel;
    }

    private <T> T createAiService(Class<T> serviceClass) {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(AgentOrchestrator.class.getClassLoader());
            return AiServices.builder(serviceClass)
                    .chatModel(getOrCreateAgentModel())
                    .build();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private synchronized void invalidateAgents() {
        plannerAgent = null;
        criticAgent = null;
        toolDispatcher = null;
        closeModel();
        agentModel = null;
        log.debug("Agents invalidated — will be recreated on next call");
    }

    private void closeModel() {
        OllamaChatModel model = agentModel;
        if (model instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.debug("Error closing agent model: {}", e.getMessage());
            }
        }
    }

    private void publishProgress(AgentProgressEvent event) {
        project.getMessageBus()
                .syncPublisher(AgentProgressNotifier.TOPIC)
                .onProgress(event);
    }

    @Override
    public void dispose() {
        plannerAgent = null;
        criticAgent = null;
        toolDispatcher = null;
        closeModel();
        agentModel = null;
    }
}
