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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service(Service.Level.PROJECT)
@Slf4j
public final class AgentOrchestrator implements Disposable {

    private final Project project;
    private volatile PlannerAgent plannerAgent;
    private volatile CriticAgent criticAgent;
    /** Model dedicated to the PlannerAgent (may be the same instance as criticModel). */
    private volatile OllamaChatModel plannerModel;
    /** Model dedicated to the CriticAgent (may be the same instance as plannerModel). */
    private volatile OllamaChatModel criticModel;
    private volatile ToolDispatcher toolDispatcher;
    private volatile boolean executionCancelled = false;
    private final AtomicReference<CompletableFuture<Void>> currentExecution = new AtomicReference<>(null);

    public AgentOrchestrator(@NotNull Project project) {
        this.project = project;
        project.getMessageBus().connect(this).subscribe(
                ChatModelModifiedNotifier.TOPIC,
                (ChatModelModifiedNotifier) this::invalidateAgents
        );
    }

    // -------------------------------------------------------------------------
    // Model compatibility check
    // -------------------------------------------------------------------------

    /**
     * Sends a minimal JSON prompt to the configured planner model and returns {@code null} if
     * the model responds with valid JSON, or an error message string if it does not.
     *
     * <p>Runs on the calling thread (must be invoked from a background thread).
     * Timeout: 15 seconds.
     */
    @org.jetbrains.annotations.Nullable
    public String checkModelCompatibility() {
        OllamaSettings settings;
        try {
            settings = OllamaSettings.getInstance();
        } catch (Exception e) {
            return "Settings unavailable: " + e.getMessage();
        }
        try {
            dev.langchain4j.model.ollama.OllamaChatModel testModel =
                    dev.langchain4j.model.ollama.OllamaChatModel.builder()
                            .baseUrl(settings.getChatOllamaUrl())
                            .modelName(settings.getAgentPlannerModelName())
                            .responseFormat(dev.langchain4j.model.chat.request.ResponseFormat.JSON)
                            .temperature(0.0)
                            .timeout(java.time.Duration.ofSeconds(15))
                            .build();
            String response = CompletableFuture
                    .supplyAsync(() -> testModel.chat(
                            "Reply with exactly this JSON and nothing else: {\"compatible\":true}"))
                    .orTimeout(15, TimeUnit.SECONDS)
                    .join();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(response); // throws if not valid JSON
            return null; // compatible
        } catch (java.util.concurrent.CompletionException ce) {
            Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
            if (cause instanceof TimeoutException) {
                return "Model did not respond within 15s — check that Ollama is running and the model '"
                        + settings.getAgentPlannerModelName() + "' is loaded.";
            }
            return "Cannot reach model '" + settings.getAgentPlannerModelName()
                    + "': " + cause.getMessage();
        } catch (Exception e) {
            return "Model '" + settings.getAgentPlannerModelName()
                    + "' does not support structured JSON output. "
                    + "Agent mode requires a model with reliable JSON generation "
                    + "(e.g. qwen2.5:14b, mistral-nemo, deepseek-coder:33b). "
                    + "Error: " + e.getMessage();
        }
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

    /**
     * Resolves {@code @ClassName} file references in the goal, then enriches with memory context.
     * The resolved file contents are prepended to the goal so the PlannerAgent has direct access
     * to the relevant source without needing a separate FILE_READ step.
     */
    private String enrichGoal(String userGoal) {
        String withFiles = GoalContextResolver.resolve(userGoal, project);
        return enrichGoalWithMemory(withFiles);
    }

    private String buildPlanErrorMessage(Throwable cause) {
        if (cause instanceof TimeoutException) {
            return "Plan generation timed out after " + planTimeoutSeconds() + "s — the model may be unavailable or overloaded";
        }
        String msg = cause.getMessage();
        if (msg != null && (msg.contains("JsonParseException") || msg.contains("JsonMappingException")
                || msg.contains("MismatchedInputException") || msg.contains("Unrecognized token")
                || msg.contains("was expecting") || (cause.getClass().getName().contains("jackson")))) {
            return "The model returned invalid JSON — agent mode requires a model with structured output support "
                    + "(e.g. qwen2.5:14b, mistral-nemo, llama3.1:70b). "
                    + "Change your chat model in Settings → Ollama.";
        }
        // Check cause chain for Jackson errors
        Throwable t = cause;
        while (t != null) {
            if (t.getClass().getName().contains("jackson") || t.getClass().getName().contains("JsonParse")) {
                return "The model returned invalid JSON — agent mode requires a model with structured output support "
                        + "(e.g. qwen2.5:14b, mistral-nemo, llama3.1:70b). "
                        + "Change your chat model in Settings → Ollama.";
            }
            t = t.getCause();
        }
        return msg != null ? msg : cause.getClass().getSimpleName();
    }

    private long planTimeoutSeconds() {
        try {
            return OllamaSettings.getInstance().getAgentPlanTimeoutSeconds();
        } catch (Exception e) {
            return 120;
        }
    }

    private long globalTimeoutSeconds() {
        try {
            int minutes = OllamaSettings.getInstance().getAgentGlobalTimeoutMinutes();
            return minutes > 0 ? (long) minutes * 60 : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @TestOnly
    CompletableFuture<AgentPlan> plan(String userGoal, PlannerAgent agent) {
        return CompletableFuture.supplyAsync(() -> {
            publishProgress(AgentProgressEvent.planning());
            try {
                long timeout = planTimeoutSeconds();
                String enrichedGoal = enrichGoal(userGoal);
                AgentPlan agentPlan = CompletableFuture
                        .supplyAsync(() -> agent.plan(enrichedGoal))
                        .orTimeout(timeout, TimeUnit.SECONDS)
                        .join();
                validatePlan(agentPlan, userGoal);
                publishProgress(AgentProgressEvent.planReady(agentPlan));
                return agentPlan;
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                String message = buildPlanErrorMessage(cause);
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
        // Capture both dispatcher and critic inside a single synchronized block so that
        // a concurrent invalidateAgents() call (triggered by ChatModelModifiedNotifier) cannot
        // null them out between the two acquisitions, which would cause execution to start with
        // a dispatcher and a critic built against different (possibly mismatched) model configs.
        final ToolDispatcher dispatcher;
        final CriticAgent critic;
        synchronized (this) {
            dispatcher = getOrCreateToolDispatcher();
            critic = getOrCreateCriticAgent();
        }
        return executeGuarded(plan, dispatcher, critic);
    }

    @TestOnly
    CompletableFuture<Void> executeGuarded(AgentPlan plan, ToolDispatcher dispatcher, CriticAgent critic) {
        CompletableFuture<Void> running = currentExecution.get();
        if (running != null && !running.isDone()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("An execution is already in progress. Cancel it before starting a new one."));
        }
        StepRetryHelper retryHelper = new StepRetryHelper(project);
        CompletableFuture<Void> future = execute(plan, dispatcher, critic, isParanoidMode(),
                retryHelper::requestDecision);
        currentExecution.set(future);
        future.whenComplete((v, t) -> currentExecution.compareAndSet(future, null));
        return future;
    }

    /** Cancels the currently running execution, if any. Safe to call at any time. */
    public void cancelExecution() {
        executionCancelled = true;
    }

    private static final int MAX_CRITIC_ADAPTATIONS = 5;

    /** Maximum chars kept for a single step output injected into the Critic or next step params. */
    private static final int MAX_STEP_OUTPUT_CHARS = 4_096;
    /** Maximum total chars accumulated in the execution log before older entries are trimmed. */
    static final int MAX_EXECUTION_LOG_CHARS = 100_000;
    /** Marker inserted at the top of the log when older entries are dropped. */
    private static final String TRIM_MARKER = "[earlier history trimmed]\n";

    @TestOnly
    CompletableFuture<Void> execute(AgentPlan plan, ToolDispatcher dispatcher, CriticAgent critic) {
        // null retry provider = legacy test behavior: stop the phase on first failure, let Critic decide
        return execute(plan, dispatcher, critic, isParanoidMode(), null);
    }

    @TestOnly
    CompletableFuture<Void> execute(AgentPlan plan, ToolDispatcher dispatcher, CriticAgent critic, boolean paranoidMode) {
        return execute(plan, dispatcher, critic, paranoidMode, null);
    }

    /**
     * Test-only entry-point that injects a custom retry decision provider.
     * Use this to test RETRY / SKIP / ABORT_PHASE behavior without blocking on real user input.
     */
    @TestOnly
    CompletableFuture<Void> execute(AgentPlan plan, ToolDispatcher dispatcher, CriticAgent critic,
                                     BiFunction<Step, String, StepRetryDecision> retryProvider) {
        return execute(plan, dispatcher, critic, isParanoidMode(), retryProvider);
    }

    private CompletableFuture<Void> execute(AgentPlan plan, ToolDispatcher dispatcher, CriticAgent critic,
                                             boolean paranoidMode,
                                             @org.jetbrains.annotations.Nullable BiFunction<Step, String, StepRetryDecision> retryProvider) {
        executionCancelled = false;
        dispatcher.resetRateLimits();
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        // Compute the original destructive count BEFORE execution starts.
        // Passed to validateRevisedPhases() so the Critic cannot silently escalate blast radius.
        final long originalDestructiveCount = countDestructiveSteps(plan.getPhases());
        log.debug("Starting execution correlationId={} paranoid={} destructiveSteps={} goal='{}'",
                correlationId, paranoidMode, originalDestructiveCount, plan.getGoal());
        long globalTimeout = globalTimeoutSeconds();
        CompletableFuture<Void> executionFuture = CompletableFuture.runAsync(() -> {
            List<Phase> remainingPhases = new ArrayList<>(plan.getPhases());
            String lastStepOutput = "";
            int adaptationCount = 0;
            int completedStepCount = 0;
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
                boolean abortedByParanoidCritic = false;

                stepLoop:
                for (Step step : phase.getSteps()) {
                    boolean retryStep = true;
                    while (retryStep) {
                        retryStep = false;
                        publishProgress(AgentProgressEvent.stepStarted(step));
                        ToolResult result = dispatcher.dispatch(step, lastStepOutput, correlationId);

                        if (result.isSuccess()) {
                            // Update execution state BEFORE notifying UI — ensures consistency
                            // even if publishProgress() throws (e.g. MessageBus offline).
                            lastStepOutput = truncateStepOutput(result.getOutput());
                            String stepLine = "[" + step.getToolId() + "] "
                                    + PromptSanitizer.sanitize(lastStepOutput) + "\n";
                            phaseResults.append(stepLine);
                            executionLog.append(stepLine);
                            trimExecutionLog(executionLog);
                            completedStepCount++;
                            publishProgress(AgentProgressEvent.stepCompleted(step, lastStepOutput));

                            // Paranoid mode: evaluate after every successful step
                            if (paranoidMode) {
                                CriticDecision stepDecision = runCritic(critic, plan.getGoal(), phase,
                                        phaseResults.toString(), executionLog.toString(), remainingPhases, false);
                                if (stepDecision == null) { abortedByParanoidCritic = true; phaseFailed = true; break stepLoop; }
                                if (stepDecision.getStatus() == CriticDecision.Status.ABORT) {
                                    recordMemory(correlationId, plan.getGoal(), "ABORTED", stepDecision.getReasoning());
                                    publishProgress(AgentProgressEvent.aborted(stepDecision.getReasoning()));
                                    return;
                                }
                                log.debug("Paranoid Critic step decision for step '{}': status={} reasoning='{}'",
                                        step.getDescription(), stepDecision.getStatus(), stepDecision.getReasoning());
                                if (stepDecision.getStatus() == CriticDecision.Status.ADAPT && !stepDecision.getRevisedPhases().isEmpty()) {
                                    List<Phase> revisedFromStep = stepDecision.getRevisedPhases();
                                    String stepValidationError = validateRevisedPhases(revisedFromStep, plan.getGoal(), originalDestructiveCount);
                                    if (stepValidationError != null) {
                                        log.error("Paranoid Critic ADAPT rejected — invalid revised phases: {}", stepValidationError);
                                        publishProgress(AgentProgressEvent.aborted(
                                                "Critic returned invalid revised phases: " + stepValidationError));
                                        return;
                                    }
                                    adaptationCount++;
                                    if (adaptationCount > MAX_CRITIC_ADAPTATIONS) {
                                        publishProgress(AgentProgressEvent.aborted(
                                                "Too many plan adaptations (" + MAX_CRITIC_ADAPTATIONS + ") — the agent may be stuck in a loop. Aborting."));
                                        return;
                                    }
                                    AgentPlan revisedPlan = new AgentPlan(plan.getGoal(), stepDecision.getReasoning(), revisedFromStep);
                                    publishProgress(AgentProgressEvent.planAdapted(revisedPlan));
                                    remainingPhases = new ArrayList<>(revisedFromStep);
                                    phaseFailed = true; // skip per-phase Critic, restart loop
                                    break stepLoop;
                                }
                            }
                        } else {
                            String stepLine = "[" + step.getToolId() + "] FAILED: "
                                    + PromptSanitizer.sanitize(result.getErrorMessage()) + "\n";
                            phaseResults.append(stepLine);
                            executionLog.append(stepLine);
                            trimExecutionLog(executionLog);
                            publishProgress(AgentProgressEvent.stepFailed(step, result.getErrorMessage()));

                            if (retryProvider == null) {
                                // Legacy / test behavior: stop this phase, let the Critic decide recovery
                                phaseFailed = true;
                                break stepLoop;
                            }

                            StepRetryDecision retryDecision = retryProvider.apply(step, result.getErrorMessage());
                            switch (retryDecision) {
                                case RETRY -> retryStep = true;
                                case SKIP -> { /* step skipped — continue to next step in phase */ }
                                case ABORT_PHASE -> { phaseFailed = true; break stepLoop; }
                            }
                        }
                    } // end while (retryStep)
                } // end stepLoop: for

                if (abortedByParanoidCritic) return;

                // After each phase (success or failure): run the Critic
                CriticDecision decision = runCritic(critic, plan.getGoal(), phase,
                        phaseResults.toString(), executionLog.toString(), remainingPhases, phaseFailed);
                if (decision == null) return; // aborted by Critic exception

                switch (decision.getStatus()) {
                    case OK -> {
                        // Phase succeeded — save a checkpoint so we can resume from here on crash (A-3)
                        saveCheckpoint(correlationId, plan.getGoal(), plan, remainingPhases);
                    }
                    case ADAPT -> {
                        List<Phase> revised = decision.getRevisedPhases();
                        if (revised.isEmpty()) {
                            // An ADAPT with no revised phases is an ambiguous / malformed Critic response.
                            // Treat it as ABORT rather than silently continuing — this makes the agent
                            // stop and surface the issue rather than proceeding on an undefined basis.
                            log.warn("Critic returned ADAPT but provided no revised phases — aborting execution");
                            publishProgress(AgentProgressEvent.aborted(
                                    "Critic requested plan adaptation but provided no revised phases. "
                                    + "Reasoning: " + decision.getReasoning()));
                            return;
                        } else {
                            String validationError = validateRevisedPhases(revised, plan.getGoal(), originalDestructiveCount);
                            if (validationError != null) {
                                log.error("Critic ADAPT rejected — invalid revised phases: {}", validationError);
                                publishProgress(AgentProgressEvent.aborted(
                                        "Critic returned invalid revised phases: " + validationError));
                                return;
                            }
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
                        recordMemory(correlationId, plan.getGoal(), "ABORTED", decision.getReasoning());
                        clearCheckpoint();
                        publishProgress(AgentProgressEvent.aborted(decision.getReasoning()));
                        return;
                    }
                }
            }

            recordMemory(correlationId, plan.getGoal(), "COMPLETED", "All phases succeeded.");
            clearCheckpoint();
            publishProgress(AgentProgressEvent.completed(completedStepCount));
        });
        if (globalTimeout > 0) {
            executionFuture = executionFuture.orTimeout(globalTimeout, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        if (ex instanceof TimeoutException || (ex.getCause() instanceof TimeoutException)) {
                            log.warn("Global execution timeout reached after {}s for correlationId={}", globalTimeout, correlationId);
                            publishProgress(AgentProgressEvent.aborted(
                                    "Execution aborted: global timeout of " + (globalTimeout / 60) + " minute(s) reached."));
                        }
                        return null;
                    });
        }
        return executionFuture;
    }

    private void recordMemory(String correlationId, String goal, String status, String reason) {
        AgentMemoryService memory = project.getService(AgentMemoryService.class);
        if (memory != null) {
            memory.record(correlationId, goal, status, reason);
        }
    }

    /**
     * Saves the current execution checkpoint (remaining phases) so the execution can be
     * resumed from this point if the IDE or agent crashes (A-3).
     * No-op when the checkpoint service is unavailable.
     */
    private void saveCheckpoint(String correlationId, String goal, AgentPlan plan,
                                List<Phase> remainingPhases) {
        AgentCheckpointService ckpt = project.getService(AgentCheckpointService.class);
        if (ckpt == null) return;
        int nextIndex = plan.getPhases().size() - remainingPhases.size();
        ckpt.save(correlationId, goal, plan, nextIndex);
    }

    /** Deletes any existing checkpoint for this execution. */
    private void clearCheckpoint() {
        AgentCheckpointService ckpt = project.getService(AgentCheckpointService.class);
        if (ckpt != null) ckpt.clear();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Runs the Critic and publishes progress events. Returns {@code null} if the Critic
     * threw a malformed response (ABORTED event already published).
     */
    private CriticDecision runCritic(CriticAgent critic, String goal, Phase phase,
                                     String phaseResults, String fullExecutionLog,
                                     List<Phase> remaining, boolean phaseFailed) {
        publishProgress(AgentProgressEvent.criticThinking(phase.getDescription()));
        String criticPrompt = buildCriticPrompt(goal, phase, phaseResults, fullExecutionLog, remaining, phaseFailed);
        log.debug("Critic prompt for phase '{}':\n{}", phase.getDescription(), criticPrompt);
        try {
            CriticDecision decision = critic.evaluate(criticPrompt);
            log.debug("Critic decision for phase '{}': {}", phase.getDescription(), decision);
            return decision;
        } catch (Exception e) {
            log.error("Critic returned a malformed response for phase '{}': {}", phase.getDescription(), e.getMessage());
            publishProgress(AgentProgressEvent.aborted(
                    "Critic returned an invalid response (likely malformed JSON from the LLM). "
                            + "Phase: " + phase.getDescription() + ". Error: " + e.getMessage()));
            return null;
        }
    }

    private boolean isParanoidMode() {
        try {
            return OllamaSettings.getInstance().isAgentParanoidMode();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Truncates a step output to {@link #MAX_STEP_OUTPUT_CHARS} using a first-60% + last-40%
     * strategy. Keeping both ends means the Critic always sees:
     * <ul>
     *   <li>the beginning of the output (context / operation description), and</li>
     *   <li>the end of the output (where error root causes and stack traces typically appear).</li>
     * </ul>
     * A naive head-only truncation would silently discard error details exactly when they matter most.
     */
    private static String truncateStepOutput(@org.jetbrains.annotations.Nullable String output) {
        if (output == null) return "";
        if (output.length() <= MAX_STEP_OUTPUT_CHARS) return output;
        int firstPart = (int) (MAX_STEP_OUTPUT_CHARS * 0.6);
        int lastPart = MAX_STEP_OUTPUT_CHARS - firstPart;
        int omitted = output.length() - MAX_STEP_OUTPUT_CHARS;
        return output.substring(0, firstPart)
                + "\n... [" + omitted + " chars omitted] ...\n"
                + output.substring(output.length() - lastPart);
    }

    /**
     * Trims the execution log when it exceeds {@link #MAX_EXECUTION_LOG_CHARS}, keeping the most
     * recent entries. Drops whole lines from the front to avoid splitting mid-entry.
     *
     * <p>The marker {@value #TRIM_MARKER} is accounted for in the budget so that after this
     * method returns, {@code log.length() <= MAX_EXECUTION_LOG_CHARS} is always true.
     * A second immediate call with no new content added must be a no-op.
     */
    static void trimExecutionLog(StringBuilder log) {
        if (log.length() <= MAX_EXECUTION_LOG_CHARS) return;
        // Reserve room for the marker so the post-insert invariant holds.
        int keepChars = MAX_EXECUTION_LOG_CHARS - TRIM_MARKER.length();
        int keepFrom = log.length() - keepChars;
        // Advance to the next newline so we don't cut a line mid-entry.
        // If the newline adjustment pushes keepFrom past the end, clamp it.
        int nl = log.indexOf("\n", keepFrom);
        if (nl > 0 && nl < log.length() - 1) keepFrom = nl + 1;
        log.delete(0, keepFrom);
        log.insert(0, TRIM_MARKER);
        // Post-condition: log.length() <= MAX_EXECUTION_LOG_CHARS
    }

    /** Maximum chars of tool output injected into a single Critic prompt (prevents context overflow). */
    static final int MAX_CRITIC_CONTEXT_CHARS = 3_000;

    /** Delimiter wrapping the execution data block in the Critic prompt (SI-4 / A4). */
    static final String EXEC_LOG_DELIMITER_START = "<<EXECUTION_LOG>>";
    static final String EXEC_LOG_DELIMITER_END   = "<</EXECUTION_LOG>>";

    private String buildCriticPrompt(String goal, Phase completedPhase, String phaseResults,
                                      String fullExecutionLog, List<Phase> remaining, boolean phaseFailed) {
        String phaseStatus = phaseFailed ? "FAILED (one or more steps failed)" : "completed";
        StringBuilder prompt = new StringBuilder();
        prompt.append("Goal: ").append(PromptSanitizer.sanitizeGoal(goal)).append("\n\n");

        // Wrap execution data in explicit delimiters so the LLM cannot interpret
        // tool output or phase headers as instructions (SI-4 / Rule A4).
        if (fullExecutionLog.length() > phaseResults.length()) {
            // There are results from previous phases — include them for context
            prompt.append("Execution history (all phases so far):\n")
                    .append(EXEC_LOG_DELIMITER_START).append("\n")
                    .append(truncateCriticContext(fullExecutionLog)).append("\n")
                    .append(EXEC_LOG_DELIMITER_END).append("\n");
        } else {
            prompt.append("Current phase ").append(phaseStatus).append(": ")
                    .append(completedPhase.getDescription()).append("\n");
            prompt.append("Step results:\n")
                    .append(EXEC_LOG_DELIMITER_START).append("\n")
                    .append(truncateCriticContext(phaseResults)).append("\n")
                    .append(EXEC_LOG_DELIMITER_END).append("\n");
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

    private static String truncateCriticContext(String text) {
        if (text == null || text.length() <= MAX_CRITIC_CONTEXT_CHARS) return text;
        int omitted = text.length() - MAX_CRITIC_CONTEXT_CHARS;
        return text.substring(0, MAX_CRITIC_CONTEXT_CHARS)
                + "\n... [context truncated — " + omitted + " chars omitted] ...";
    }

    /**
     * Validates revised phases proposed by the Critic before accepting them.
     * Applies the same rules as {@link #validatePlan} (unknown toolIds, blast-radius guards)
     * PLUS an additional check: the revised plan must not introduce more destructive operations
     * than were present in the original plan. This prevents a confused or adversarial Critic
     * from escalating the blast radius by adding FILE_DELETE / FILE_WRITE steps that the
     * user never agreed to.
     *
     * @param originalDestructiveCount number of destructive steps in the original plan (for comparison)
     * @return {@code null} if valid, or an error message if invalid
     */
    @org.jetbrains.annotations.Nullable
    private String validateRevisedPhases(List<Phase> phases, String goal, long originalDestructiveCount) {
        if (phases == null || phases.isEmpty()) return null;
        try {
            validatePlan(new AgentPlan(goal, "critic revision", phases), goal);
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
        long revisedDestructiveCount = countDestructiveSteps(phases);
        if (revisedDestructiveCount > originalDestructiveCount) {
            return "Revised plan contains " + revisedDestructiveCount
                    + " destructive operation(s) but the original plan only had " + originalDestructiveCount
                    + ". Critic cannot escalate blast radius — user must validate the revised plan manually.";
        }
        return null;
    }

    /** Returns the number of steps using write/delete/run tools in the given phases. */
    private static long countDestructiveSteps(List<Phase> phases) {
        return phases.stream()
                .flatMap(p -> p.getSteps().stream())
                .filter(s -> "FILE_DELETE".equals(s.getToolId())
                        || "FILE_WRITE".equals(s.getToolId())
                        || "FILE_EDIT".equals(s.getToolId())
                        || "RUN_COMMAND".equals(s.getToolId()))
                .count();
    }

    /** Maximum FILE_DELETE steps allowed in a single plan (G3 blast-radius guard). */
    private static final int MAX_DELETE_STEPS = 3;
    private static final int MAX_PHASES = 10;
    private static final int MAX_TOTAL_STEPS = 30;

    private void validatePlan(AgentPlan plan, String goal) {
        if (plan == null) {
            throw new IllegalStateException("PlannerAgent returned null for goal: " + goal);
        }
        if (plan.isEmpty()) {
            throw new IllegalStateException("PlannerAgent returned an empty plan for goal: " + goal);
        }
        if (plan.getPhases().size() > MAX_PHASES) {
            throw new IllegalStateException(
                    "Plan contains " + plan.getPhases().size() + " phases (max allowed: " + MAX_PHASES + "). "
                    + "Break the goal into smaller sub-goals.");
        }
        long totalSteps = plan.getPhases().stream().mapToLong(p -> p.getSteps().size()).sum();
        if (totalSteps > MAX_TOTAL_STEPS) {
            throw new IllegalStateException(
                    "Plan contains " + totalSteps + " steps total (max allowed: " + MAX_TOTAL_STEPS + "). "
                    + "Break the goal into smaller sub-goals.");
        }
        // Reject plans containing unknown tool IDs — prevents LLM hallucinations from reaching execution
        List<String> unknownToolIds = plan.getPhases().stream()
                .flatMap(p -> p.getSteps().stream())
                .map(Step::getToolId)
                .filter(id -> !ToolRegistry.KNOWN_TOOL_IDS.contains(id))
                .distinct()
                .collect(Collectors.toList());
        if (!unknownToolIds.isEmpty()) {
            throw new IllegalStateException(
                    "Plan contains unknown tool IDs: " + unknownToolIds
                    + ". Valid tools: " + ToolRegistry.KNOWN_TOOL_IDS);
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
                    plannerAgent = createAiService(PlannerAgent.class, getOrCreatePlannerModel());
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
                    criticAgent = createAiService(CriticAgent.class, getOrCreateCriticModel());
                }
            }
        }
        return criticAgent;
    }

    private OllamaChatModel getOrCreatePlannerModel() {
        if (plannerModel == null) {
            synchronized (this) {
                if (plannerModel == null) {
                    OllamaSettings settings = OllamaSettings.getInstance();
                    plannerModel = OllamaChatModel.builder()
                            .baseUrl(settings.getChatOllamaUrl())
                            .modelName(settings.getAgentPlannerModelName())
                            .responseFormat(ResponseFormat.JSON)
                            .temperature(0.3)
                            .timeout(settings.getTimeoutDuration())
                            .build();
                    log.debug("Planner model created: {}", settings.getAgentPlannerModelName());
                }
            }
        }
        return plannerModel;
    }

    private OllamaChatModel getOrCreateCriticModel() {
        if (criticModel == null) {
            synchronized (this) {
                if (criticModel == null) {
                    OllamaSettings settings = OllamaSettings.getInstance();
                    criticModel = OllamaChatModel.builder()
                            .baseUrl(settings.getChatOllamaUrl())
                            .modelName(settings.getAgentCriticModelName())
                            .responseFormat(ResponseFormat.JSON)
                            .temperature(0.1)
                            .timeout(settings.getTimeoutDuration())
                            .build();
                    log.debug("Critic model created: {}", settings.getAgentCriticModelName());
                }
            }
        }
        return criticModel;
    }

    private <T> T createAiService(Class<T> serviceClass, OllamaChatModel model) {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(AgentOrchestrator.class.getClassLoader());
            return AiServices.builder(serviceClass)
                    .chatModel(model)
                    .build();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private synchronized void invalidateAgents() {
        plannerAgent = null;
        criticAgent = null;
        if (toolDispatcher != null) {
            toolDispatcher.dispose();
            toolDispatcher = null;
        }
        closeModel(plannerModel);
        closeModel(criticModel);
        plannerModel = null;
        criticModel = null;
        log.debug("Agents invalidated — will be recreated on next call");
    }

    private static void closeModel(OllamaChatModel model) {
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
        cancelExecution();
        synchronized (this) {
            plannerAgent = null;
            criticAgent = null;
            if (toolDispatcher != null) {
                toolDispatcher.dispose();
                toolDispatcher = null;
            }
            closeModel(plannerModel);
            closeModel(criticModel);
            plannerModel = null;
            criticModel = null;
        }
    }
}
