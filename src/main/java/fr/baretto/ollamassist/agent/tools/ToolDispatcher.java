package fr.baretto.ollamassist.agent.tools;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.AuditLogger;
import fr.baretto.ollamassist.agent.plan.Step;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public final class ToolDispatcher {

    /**
     * Per-instance thread pool for tool execution. Daemon threads so the JVM can exit normally
     * even if a tool is stuck. Instance (not static) so the pool can be shut down via
     * {@link #dispose()} when the plugin unloads, preventing thread leaks on long IDE sessions.
     */
    private final ExecutorService toolExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ollamassist-tool");
        t.setDaemon(true);
        return t;
    });

    /** Maximum chars stored per named step variable to prevent unbounded memory growth. */
    private static final int MAX_STEP_VARIABLE_CHARS = 5_120; // 5 KB
    /** Number of consecutive approval timeouts before the entire execution is aborted. */
    private static final int MAX_APPROVAL_TIMEOUTS = 3;

    private final ToolRegistry registry;
    @Nullable
    private final AuditLogger auditLogger;
    private final ToolRateLimiter rateLimiter = new ToolRateLimiter();
    /**
     * Named outputs declared by steps via {@code "outputVar"}.
     * Keyed by the variable name; reset at the start of each execution via {@link #resetRateLimits()}.
     */
    private final ConcurrentHashMap<String, String> stepVariables = new ConcurrentHashMap<>();
    /** Counts consecutive approval timeouts; triggers an abort circuit-breaker at MAX_APPROVAL_TIMEOUTS. */
    private final AtomicInteger approvalTimeoutCount = new AtomicInteger(0);
    /** 0 means "read from settings". Overridden in tests. */
    private final int toolTimeoutSecondsOverride;

    public ToolDispatcher(ToolRegistry registry, Project project) {
        this(registry, project, 0);
    }

    /** Test-only constructor — allows injecting a short timeout without touching settings. */
    @org.jetbrains.annotations.TestOnly
    public ToolDispatcher(ToolRegistry registry, Project project, int toolTimeoutSecondsOverride) {
        this.registry = registry;
        this.auditLogger = project != null ? project.getService(AuditLogger.class) : null;
        this.toolTimeoutSecondsOverride = toolTimeoutSecondsOverride;
    }

    private int toolTimeoutSeconds() {
        if (toolTimeoutSecondsOverride > 0) return toolTimeoutSecondsOverride;
        try {
            return fr.baretto.ollamassist.setting.OllamaSettings.getInstance().getAgentToolTimeoutSeconds();
        } catch (Exception e) {
            return 120;
        }
    }

    /** Resets per-execution rate-limit counters, named step variables, and approval timeout counter. Call before each new agent execution. */
    public void resetRateLimits() {
        rateLimiter.reset();
        stepVariables.clear();
        approvalTimeoutCount.set(0);
    }

    /** Dispatches a step with no previous context or correlation ID. */
    public ToolResult dispatch(Step step) {
        return dispatch(step, "", null);
    }

    /** Dispatches a step with previous output but no correlation ID. */
    public ToolResult dispatch(Step step, String previousOutput) {
        return dispatch(step, previousOutput, null);
    }

    /**
     * Dispatches a step, resolving any {@code {{prev_output}}} / {@code {{prev_output_first_line}}}
     * placeholders in params using the output of the previous step.
     *
     * @param correlationId execution correlation ID propagated to the audit log
     */
    public ToolResult dispatch(Step step, String previousOutput, String correlationId) {
        AgentTool tool = registry.get(step.getToolId());
        if (tool == null) {
            ToolResult failure = ToolResult.failure("Unknown tool: '" + step.getToolId() + "'");
            audit(correlationId, step, Map.of(), failure);
            return failure;
        }
        if (!rateLimiter.tryAcquire(step.getToolId())) {
            ToolResult failure = ToolResult.failure(
                    "Tool '" + step.getToolId() + "' has exceeded its call limit for this execution. "
                    + "The agent may be stuck in a loop.");
            audit(correlationId, step, Map.of(), failure);
            return failure;
        }
        // SI-3 / A2: RUN_COMMAND must not use dynamic placeholders in the command string.
        // Substituted values (file content, tool output) may contain shell metacharacters that
        // are interpreted by `sh -c`, enabling arbitrary command injection (e.g. prev_output = "; rm -rf /").
        if ("RUN_COMMAND".equals(step.getToolId())) {
            Object cmdTemplate = step.getParams().get("command");
            if (cmdTemplate instanceof String cmd
                    && (cmd.contains(StepParamResolver.PREV_OUTPUT)
                    || cmd.contains(StepParamResolver.PREV_OUTPUT_FIRST_LINE)
                    || cmd.contains("{{var."))) {
                ToolResult failure = ToolResult.failure(
                        "Dynamic placeholders ({{prev_output}}, {{var.*}}) are not allowed in RUN_COMMAND "
                        + "'command' parameters: shell injection risk. Use a fixed command string "
                        + "and pass dynamic data through a temporary file instead.");
                audit(correlationId, step, Map.of(), failure);
                return failure;
            }
        }

        Map<String, Object> resolvedParams;
        try {
            resolvedParams = new HashMap<>(StepParamResolver.resolve(step.getParams(), previousOutput, Map.copyOf(stepVariables)));
            // Inject correlation ID so write-capable tools can group their undo entries (Phase 4).
            if (correlationId != null) {
                resolvedParams.put("__correlationId", correlationId);
            }
        } catch (StepParamResolver.UnresolvablePlaceholderException e) {
            log.warn("Unresolvable placeholder in step '{}': {}", step.getDescription(), e.getMessage());
            ToolResult failure = ToolResult.failure(e.getMessage());
            audit(correlationId, step, Map.of(), failure);
            return failure;
        }

        log.debug("Dispatching step '{}' to tool '{}' with params {}",
                step.getDescription(), step.getToolId(), resolvedParams);

        try {
            int timeout = toolTimeoutSeconds();
            ToolResult result = CompletableFuture
                    .supplyAsync(() -> tool.execute(resolvedParams), toolExecutor)
                    .orTimeout(timeout, TimeUnit.SECONDS)
                    .join();
            // Store named output if the step declared an outputVar.
            // compute() makes the check-and-set atomic with respect to concurrent resetRateLimits() calls.
            if (result.isSuccess()) {
                String outputVar = step.getOutputVar();
                if (outputVar != null && !outputVar.isBlank()) {
                    stepVariables.compute(outputVar, (k, ignored) -> {
                        String varValue = result.getOutput() != null ? result.getOutput() : "";
                        if (varValue.length() > MAX_STEP_VARIABLE_CHARS) {
                            varValue = varValue.substring(0, MAX_STEP_VARIABLE_CHARS) + "... [truncated]";
                            log.debug("Step variable '{}' truncated to {}KB", k, MAX_STEP_VARIABLE_CHARS / 1024);
                        }
                        log.debug("Stored step variable '{}' = '{}...'", k,
                                varValue.substring(0, Math.min(60, varValue.length())));
                        return varValue;
                    });
                }
            }
            audit(correlationId, step, resolvedParams, result);
            return result;
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause();
            if (cause instanceof TimeoutException) {
                int timeout = toolTimeoutSeconds();
                log.warn("Tool '{}' timed out after {}s for step '{}'",
                        step.getToolId(), timeout, step.getDescription());
                ToolResult failure = ToolResult.failure(
                        "Tool '" + step.getToolId() + "' timed out after " + timeout + "s");
                audit(correlationId, step, resolvedParams, failure);
                return failure;
            }
            if (cause instanceof ToolApprovalHelper.ApprovalTimeoutException ate) {
                int count = approvalTimeoutCount.incrementAndGet();
                log.warn("Approval timed out for step '{}' ({}/{} before abort): {}",
                        step.getDescription(), count, MAX_APPROVAL_TIMEOUTS, ate.getMessage());
                if (count >= MAX_APPROVAL_TIMEOUTS) {
                    ToolResult failure = ToolResult.failure(
                            "Execution aborted after " + MAX_APPROVAL_TIMEOUTS
                            + " consecutive approval timeouts. The approval dialog was not answered. "
                            + "Check that the IDE window is in focus.");
                    audit(correlationId, step, resolvedParams, failure);
                    return failure;
                }
                ToolResult failure = ToolResult.failure("Approval timed out: " + ate.getMessage());
                audit(correlationId, step, resolvedParams, failure);
                return failure;
            }
            Throwable root = cause != null ? cause : ce;
            log.error("Tool '{}' threw an exception for step '{}'", step.getToolId(), step.getDescription(), root);
            ToolResult failure = ToolResult.failure("Tool '" + step.getToolId() + "' failed: " + root.getMessage());
            audit(correlationId, step, resolvedParams, failure);
            return failure;
        }
    }

    /**
     * Shuts down the tool execution thread pool. Must be called when the owning
     * {@code AgentOrchestrator} is disposed so threads are not leaked across IDE sessions.
     */
    public void dispose() {
        toolExecutor.shutdown();
    }

    private void audit(String correlationId, Step step, Map<String, Object> params, ToolResult result) {
        if (auditLogger == null) return;
        auditLogger.record(
                correlationId,
                step.getToolId(),
                step.getDescription(),
                params.keySet(),
                result.isSuccess(),
                result.isSuccess() ? null : result.getErrorMessage()
        );
    }
}
