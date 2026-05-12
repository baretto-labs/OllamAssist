package fr.baretto.ollamassist.agent;

import fr.baretto.ollamassist.agent.tools.ToolRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRateLimiterTest {

    private ToolRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new ToolRateLimiter();
    }

    // -------------------------------------------------------------------------
    // Default limit (non-destructive tools)
    // -------------------------------------------------------------------------

    @Test
    void firstCall_alwaysAllowed() {
        assertThat(limiter.tryAcquire("GIT_STATUS")).isTrue();
    }

    @Test
    void callsUpToDefaultLimit_areAllowed() {
        for (int i = 0; i < ToolRateLimiter.DEFAULT_LIMIT; i++) {
            assertThat(limiter.tryAcquire("FILE_READ"))
                    .as("call %d should be allowed", i + 1)
                    .isTrue();
        }
    }

    @Test
    void callBeyondDefaultLimit_isRejected() {
        for (int i = 0; i < ToolRateLimiter.DEFAULT_LIMIT; i++) {
            limiter.tryAcquire("FILE_READ");
        }
        assertThat(limiter.tryAcquire("FILE_READ")).isFalse();
    }

    @Test
    void differentTools_haveIndependentCounters() {
        for (int i = 0; i < ToolRateLimiter.DEFAULT_LIMIT; i++) {
            limiter.tryAcquire("FILE_READ");
        }
        // GIT_STATUS counter is still at 0 — should be allowed
        assertThat(limiter.tryAcquire("GIT_STATUS")).isTrue();
    }

    // -------------------------------------------------------------------------
    // Destructive limit (FILE_DELETE, FILE_WRITE, FILE_EDIT)
    // -------------------------------------------------------------------------

    @Test
    void fileDelete_callsUpToDestructiveLimit_areAllowed() {
        for (int i = 0; i < ToolRateLimiter.DESTRUCTIVE_LIMIT; i++) {
            assertThat(limiter.tryAcquire("FILE_DELETE"))
                    .as("call %d should be allowed", i + 1)
                    .isTrue();
        }
    }

    @Test
    void fileDelete_callBeyondDestructiveLimit_isRejected() {
        for (int i = 0; i < ToolRateLimiter.DESTRUCTIVE_LIMIT; i++) {
            limiter.tryAcquire("FILE_DELETE");
        }
        assertThat(limiter.tryAcquire("FILE_DELETE")).isFalse();
    }

    @Test
    void fileWrite_hasDestructiveLimit() {
        for (int i = 0; i < ToolRateLimiter.DESTRUCTIVE_LIMIT; i++) {
            limiter.tryAcquire("FILE_WRITE");
        }
        assertThat(limiter.tryAcquire("FILE_WRITE")).isFalse();
    }

    @Test
    void fileEdit_hasDestructiveLimit() {
        for (int i = 0; i < ToolRateLimiter.DESTRUCTIVE_LIMIT; i++) {
            limiter.tryAcquire("FILE_EDIT");
        }
        assertThat(limiter.tryAcquire("FILE_EDIT")).isFalse();
    }

    @Test
    void destructiveLimitIsLowerThanDefaultLimit() {
        assertThat(ToolRateLimiter.DESTRUCTIVE_LIMIT).isLessThan(ToolRateLimiter.DEFAULT_LIMIT);
    }

    // -------------------------------------------------------------------------
    // Reset
    // -------------------------------------------------------------------------

    @Test
    void reset_clearsAllCounters() {
        for (int i = 0; i < ToolRateLimiter.DESTRUCTIVE_LIMIT; i++) {
            limiter.tryAcquire("FILE_DELETE");
        }
        assertThat(limiter.tryAcquire("FILE_DELETE")).isFalse();

        limiter.reset();

        assertThat(limiter.tryAcquire("FILE_DELETE")).isTrue();
    }

    @Test
    void reset_allowsNewExecutionToStartFresh() {
        for (int i = 0; i < ToolRateLimiter.DEFAULT_LIMIT; i++) {
            limiter.tryAcquire("CODE_SEARCH");
        }
        limiter.reset();

        for (int i = 0; i < ToolRateLimiter.DEFAULT_LIMIT; i++) {
            assertThat(limiter.tryAcquire("CODE_SEARCH")).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // Thread safety — concurrent calls must not exceed limit
    // -------------------------------------------------------------------------

    @Test
    void concurrentCalls_neverExceedLimit() throws InterruptedException {
        int threads = 20;
        int[] allowed = {0};
        Object lock = new Object();

        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                // Each thread tries DEFAULT_LIMIT calls
                for (int j = 0; j < ToolRateLimiter.DEFAULT_LIMIT; j++) {
                    if (limiter.tryAcquire("FILE_READ")) {
                        synchronized (lock) {
                            allowed[0]++;
                        }
                    }
                }
            });
        }
        for (Thread t : workers) t.start();
        for (Thread t : workers) t.join();

        // Exactly DEFAULT_LIMIT calls should have been allowed
        assertThat(allowed[0]).isEqualTo(ToolRateLimiter.DEFAULT_LIMIT);
    }

    // -------------------------------------------------------------------------
    // T-2: Rigorous concurrent test with ExecutorService + CountDownLatch
    // -------------------------------------------------------------------------

    @Test
    void concurrentCalls_withCountDownLatch_exactlyLimitAllowed() throws InterruptedException {
        int threadCount = 50;
        int callsPerThread = 5; // total attempts = 250 > DEFAULT_LIMIT (50)

        AtomicInteger allowedCount = new AtomicInteger(0);
        CountDownLatch startGate = new CountDownLatch(1);   // all threads start simultaneously
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startGate.await(); // hold until all threads are ready
                    for (int j = 0; j < callsPerThread; j++) {
                        if (limiter.tryAcquire("CODE_SEARCH")) {
                            allowedCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown(); // release all threads at once
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // Regardless of concurrency, exactly DEFAULT_LIMIT calls must have been granted
        assertThat(allowedCount.get()).isEqualTo(ToolRateLimiter.DEFAULT_LIMIT);
    }

    @Test
    void concurrentReset_doesNotLeaveStaleCountAfterReset() throws InterruptedException {
        // Exhaust the limiter, then reset while concurrent callers are still trying
        for (int i = 0; i < ToolRateLimiter.DEFAULT_LIMIT; i++) {
            limiter.tryAcquire("GIT_DIFF");
        }
        assertThat(limiter.tryAcquire("GIT_DIFF")).isFalse();

        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(1);
        AtomicInteger allowedAfterReset = new AtomicInteger(0);

        Thread acquirer = new Thread(() -> {
            try {
                ready.await();
                // Try immediately after reset signal
                if (limiter.tryAcquire("GIT_DIFF")) allowedAfterReset.incrementAndGet();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        acquirer.start();
        limiter.reset();
        ready.countDown();     // let acquirer proceed after reset
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();

        // After reset, at least one more call must be allowed
        assertThat(allowedAfterReset.get()).isEqualTo(1);
    }

    @Test
    void totalInvocationCap_enforcedAcrossTools() throws InterruptedException {
        // Fill the total cap using several different tools
        int toolCount = 3;
        int callsPerTool = ToolRateLimiter.MAX_TOTAL_INVOCATIONS / toolCount;

        for (int i = 0; i < callsPerTool; i++) {
            limiter.tryAcquire("GIT_STATUS");
            limiter.tryAcquire("FILE_READ");
            limiter.tryAcquire("CODE_SEARCH");
        }

        // The total cap should be reached (within rounding); the next call must be rejected
        // for all tools regardless of per-tool counter
        boolean anyRejected = false;
        for (int i = 0; i < 10; i++) {
            if (!limiter.tryAcquire("FILE_FIND")) { anyRejected = true; break; }
        }
        assertThat(anyRejected).isTrue();
    }
}
