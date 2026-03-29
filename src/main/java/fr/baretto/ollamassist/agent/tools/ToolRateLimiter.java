package fr.baretto.ollamassist.agent.tools;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple per-tool call counter that prevents an agent loop from invoking
 * a single tool an unbounded number of times within one execution.
 *
 * <p>This is a blast-radius guard (G1 security requirement), not a
 * time-windowed rate limiter. The counter resets when a new execution
 * starts ({@link #reset()}).
 *
 * <p>Limits:
 * <ul>
 *   <li>Default: {@value #DEFAULT_LIMIT} calls per tool per execution</li>
 *   <li>Destructive-class tools (FILE_DELETE, FILE_WRITE): {@value #DESTRUCTIVE_LIMIT}</li>
 * </ul>
 */
@Slf4j
public final class ToolRateLimiter {

    static final int DEFAULT_LIMIT = 50;
    static final int DESTRUCTIVE_LIMIT = 10;

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    /** Resets all counters. Call at the start of each new agent execution. */
    public void reset() {
        counters.clear();
    }

    /**
     * Increments the counter for {@code toolId} and returns {@code true} if
     * the call is allowed, {@code false} if the limit has been reached.
     */
    public boolean tryAcquire(String toolId) {
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
