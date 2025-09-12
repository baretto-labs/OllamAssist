package fr.baretto.ollamassist.completion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CompletionDebouncer.
 * Tests debouncing behavior, cancellation, and resource management.
 */
class CompletionDebouncerTest {

    private CompletionDebouncer debouncer;

    @BeforeEach
    void setUp() {
        debouncer = new CompletionDebouncer();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testBasicDebouncing() throws InterruptedException {
        AtomicInteger executionCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        String key = "test-key";

        Runnable task = () -> {
            executionCount.incrementAndGet();
            latch.countDown();
        };

        // Execute debounced task
        debouncer.debounce(key, 100, task);

        // Wait for execution
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Task should execute within timeout");
        assertEquals(1, executionCount.get(), "Task should execute exactly once");
        assertEquals(0, debouncer.getPendingRequestCount(), "Should have no pending requests after execution");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testDebouncingCancellation() throws InterruptedException {
        AtomicInteger executionCount = new AtomicInteger(0);
        String key = "test-key";

        Runnable task = () -> executionCount.incrementAndGet();

        // Fire multiple rapid requests - only the last one should execute
        for (int i = 0; i < 10; i++) {
            debouncer.debounce(key, 200, task);
            Thread.sleep(10); // Small delay between requests
        }

        // Wait longer than debounce delay
        Thread.sleep(400);

        assertEquals(1, executionCount.get(), "Only the last debounced task should execute");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testMultipleKeys() throws InterruptedException {
        AtomicInteger execution1 = new AtomicInteger(0);
        AtomicInteger execution2 = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);

        Runnable task1 = () -> {
            execution1.incrementAndGet();
            latch.countDown();
        };

        Runnable task2 = () -> {
            execution2.incrementAndGet();
            latch.countDown();
        };

        // Execute tasks with different keys - both should execute
        debouncer.debounce("key1", 100, task1);
        debouncer.debounce("key2", 100, task2);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Both tasks should execute");
        assertEquals(1, execution1.get(), "Task 1 should execute once");
        assertEquals(1, execution2.get(), "Task 2 should execute once");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCancelSpecificKey() throws InterruptedException {
        AtomicInteger executionCount = new AtomicInteger(0);
        String key = "test-key";

        Runnable task = () -> executionCount.incrementAndGet();

        // Schedule a debounced task
        debouncer.debounce(key, 200, task);
        
        // Cancel it before execution
        debouncer.cancel(key);
        
        // Wait longer than the debounce delay
        Thread.sleep(300);
        
        assertEquals(0, executionCount.get(), "Cancelled task should not execute");
        assertEquals(0, debouncer.getPendingRequestCount(), "Should have no pending requests after cancellation");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCancelNonExistentKey() {
        // Cancelling non-existent key should not throw exception
        assertDoesNotThrow(() -> debouncer.cancel("non-existent-key"), 
            "Cancelling non-existent key should not throw exception");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testPendingRequestCount() throws InterruptedException {
        String key1 = "key1";
        String key2 = "key2";
        
        assertEquals(0, debouncer.getPendingRequestCount(), "Should start with 0 pending requests");
        
        // Add a pending request
        debouncer.debounce(key1, 500, () -> {});
        assertEquals(1, debouncer.getPendingRequestCount(), "Should have 1 pending request");
        
        // Add another pending request with different key
        debouncer.debounce(key2, 500, () -> {});
        assertEquals(2, debouncer.getPendingRequestCount(), "Should have 2 pending requests");
        
        // Cancel one request
        debouncer.cancel(key1);
        assertEquals(1, debouncer.getPendingRequestCount(), "Should have 1 pending request after cancellation");
        
        // Cancel the other request
        debouncer.cancel(key2);
        assertEquals(0, debouncer.getPendingRequestCount(), "Should have 0 pending requests after all cancellations");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testDispose() throws InterruptedException {
        AtomicInteger executionCount = new AtomicInteger(0);
        String key = "test-key";

        Runnable task = () -> executionCount.incrementAndGet();

        // Schedule some debounced tasks
        debouncer.debounce(key + "1", 200, task);
        debouncer.debounce(key + "2", 200, task);
        assertEquals(2, debouncer.getPendingRequestCount(), "Should have 2 pending requests");

        // Dispose debouncer
        debouncer.dispose();
        
        // Wait longer than debounce delay
        Thread.sleep(300);
        
        assertEquals(0, executionCount.get(), "No tasks should execute after dispose");
        assertEquals(0, debouncer.getPendingRequestCount(), "Should have 0 pending requests after dispose");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testTaskException() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        String key = "test-key";

        Runnable failingTask = () -> {
            latch.countDown();
            throw new RuntimeException("Test exception");
        };

        // Task that throws exception should still complete the debounce cycle
        debouncer.debounce(key, 100, failingTask);
        
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Task should execute despite throwing exception");
        
        // Wait a bit more to ensure cleanup
        assertEquals(0, debouncer.getPendingRequestCount(), "Should have no pending requests after exception");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testRapidFireSameKey() throws InterruptedException {
        AtomicInteger executionCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        String key = "rapid-fire-key";

        Runnable task = () -> {
            executionCount.incrementAndGet();
            latch.countDown();
        };

        // Fire 50 rapid requests with same key
        for (int i = 0; i < 50; i++) {
            debouncer.debounce(key, 100, task);
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Task should eventually execute");

        
        assertEquals(1, executionCount.get(), "Should execute exactly once despite 50 requests");
    }

    @Test
    @Timeout(value = 8, unit = TimeUnit.SECONDS)
    void testConcurrentAccess() throws InterruptedException {
        int numThreads = 5;
        int requestsPerThread = 20;
        AtomicInteger totalExecutions = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        // Create multiple threads that all try to debounce tasks
        for (int threadId = 0; threadId < numThreads; threadId++) {
            final int finalThreadId = threadId;
            Thread thread = new Thread(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    for (int i = 0; i < requestsPerThread; i++) {
                        String key = "thread-" + finalThreadId + "-key";
                        debouncer.debounce(key, 100, totalExecutions::incrementAndGet);
                        Thread.sleep(10);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            thread.start();
        }

        // Start all threads
        startLatch.countDown();
        
        // Wait for all threads to finish
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "All threads should complete");
        
        // Wait for all debounced tasks to execute
        Thread.sleep(500);
        
        // Each thread should have exactly one execution (one per unique key)
        assertEquals(numThreads, totalExecutions.get(), 
            "Should have exactly " + numThreads + " executions (one per thread key)");
    }
}