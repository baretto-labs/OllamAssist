package fr.baretto.ollamassist.agent.tools;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-tool call counter that prevents an agent loop from invoking a single tool
 * an unbounded number of times, combined with a per-execution total invocation cap.
 *
 * <p>This is a blast-radius guard (G1 security requirement), not a
 * time-windowed rate limiter. All counters reset when a new execution
 * starts ({@link #reset()}).
 *
 * <p>Limits:
 * <ul>
 *   <li>Default: {@value #DEFAULT_LIMIT} calls per tool per execution</li>
 *   <li>Destructive-class tools (FILE_DELETE, FILE_WRITE, FILE_EDIT): {@value #DESTRUCTIVE_LIMIT}</li>
 *   <li>Total across all tools: {@value #MAX_TOTAL_INVOCATIONS} per execution</li>
 * </ul>
 */
@Slf4j
public final class ToolRateLimiter {

    public static final int DEFAULT_LIMIT = 50;
    public static final int DESTRUCTIVE_LIMIT = 10;
    /**
     * Hard cap on the total number of tool calls across all tools in a single execution.
     * Prevents a runaway agent from hammering the filesystem or network via many different tools
     * even if each individual tool stays within its per-tool limit.
     */
    public static final int MAX_TOTAL_INVOCATIONS = 150;

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final AtomicInteger totalCount = new AtomicInteger(0);

    /** Resets all counters. Call at the start of each new agent execution. */
    public void reset() {
        counters.clear();
        totalCount.set(0);
    }

    /**
     * Increments the counter for {@code toolId} and returns {@code true} if
     * the call is allowed, {@code false} if any limit has been reached.
     */
    public boolean tryAcquire(String toolId) {
        int total = totalCount.incrementAndGet();
        if (total > MAX_TOTAL_INVOCATIONS) {
            log.warn("ToolRateLimiter: total invocations limit reached ({}) — agent may be stuck in a loop", MAX_TOTAL_INVOCATIONS);
            return false;
        }
        int limit = isDestructiveClass(toolId) ? DESTRUCTIVE_LIMIT : DEFAULT_LIMIT;
        AtomicInteger counter = counters.computeIfAbsent(toolId, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();
        if (count > limit) {
            log.warn("ToolRateLimiter: tool '{}' has been called {} times (limit {})", toolId, count, limit);
            return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------

    private static boolean isDestructiveClass(String toolId) {
        return "FILE_DELETE".equals(toolId)
                || "FILE_WRITE".equals(toolId)
                || "FILE_EDIT".equals(toolId);
    }
}
