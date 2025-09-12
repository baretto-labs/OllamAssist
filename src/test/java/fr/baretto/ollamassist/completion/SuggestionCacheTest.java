package fr.baretto.ollamassist.completion;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.CaretModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SuggestionCache.
 * Tests caching behavior, performance, and thread safety.
 */
class SuggestionCacheTest {

    @Mock
    private Editor mockEditor;
    
    @Mock
    private Document mockDocument;
    
    @Mock
    private CaretModel mockCaretModel;

    private SuggestionCache cache;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        cache = new SuggestionCache();
        // Always start with a clean cache state for each test
        cache.clear();
        
        // Setup mock behavior
        when(mockEditor.getDocument()).thenReturn(mockDocument);
        when(mockEditor.getCaretModel()).thenReturn(mockCaretModel);
        when(mockDocument.getModificationStamp()).thenReturn(123456L);
        when(mockCaretModel.getOffset()).thenReturn(100);
        when(mockDocument.getLineNumber(100)).thenReturn(5);
        when(mockDocument.getLineStartOffset(5)).thenReturn(80);
    }

    @Test
    void testBasicCacheOperations() {
        String context = "public void testMethod() {";
        String suggestion = "System.out.println(\"Hello World\");";
        
        // Generate cache key
        String cacheKey = cache.generateCacheKey(mockEditor, context);
        assertNotNull(cacheKey, "Cache key should not be null");
        // In test environment, ApplicationManager is null, so fallback is used (shorter key)
        assertTrue(cacheKey.length() >= 8, "Cache key should be at least 8 characters");
        
        // Test cache miss
        assertNull(cache.get(cacheKey), "Cache should be empty initially");
        
        // Test cache put and hit
        cache.put(cacheKey, suggestion);
        assertEquals(suggestion, cache.get(cacheKey), "Should retrieve cached suggestion");
        
        // Test cache stats
        SuggestionCache.CacheStats stats = cache.getStats();
        assertEquals(1, stats.size, "Cache size should be 1");
        assertEquals(1, stats.hitCount, "Should have 1 hit");
        assertEquals(1, stats.missCount, "Should have 1 miss");
        assertTrue(stats.hitRate > 0, "Hit rate should be positive");
    }

    @Test
    void testCacheKeyConsistency() {
        String context = "public void testMethod() {";
        
        // Same context should generate same key
        String key1 = cache.generateCacheKey(mockEditor, context);
        String key2 = cache.generateCacheKey(mockEditor, context);
        assertEquals(key1, key2, "Same context should generate same cache key");
        
        // Different context should generate different key
        String key3 = cache.generateCacheKey(mockEditor, context + " // different");
        assertNotEquals(key1, key3, "Different context should generate different cache key");
    }

    @Test
    void testCacheEviction() {
        // Clear cache first to ensure clean state
        cache.clear();
        
        // Fill cache beyond capacity to test eviction
        // Use simple unique keys to ensure real different cache entries
        for (int i = 0; i < 600; i++) {
            String cacheKey = "unique_key_" + i;
            String suggestion = "suggestion" + i;
            cache.put(cacheKey, suggestion);
        }
        
        // Force cache maintenance to trigger eviction (Caffeine uses async eviction)
        cache.getStats(); // This triggers maintenance operations in Caffeine
        
        // Give a small delay for async eviction to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        SuggestionCache.CacheStats stats = cache.getStats();
        assertTrue(stats.size <= 500, "Cache size should not exceed maximum capacity (500), got: " + stats.size);
        assertTrue(stats.evictionCount > 0, "Should have evicted some entries, got evictionCount: " + stats.evictionCount);
    }

    @Test
    void testCacheClear() {
        String context = "test context";
        String suggestion = "test suggestion";
        String cacheKey = cache.generateCacheKey(mockEditor, context);
        
        cache.put(cacheKey, suggestion);
        assertEquals(1, cache.getStats().size, "Cache should have one entry");
        
        cache.clear();
        assertEquals(0, cache.getStats().size, "Cache should be empty after clear");
        assertNull(cache.get(cacheKey), "Should not retrieve cleared entry");
    }

    @Test
    void testInvalidatePattern() {
        cache.put("file1_key", "suggestion1");
        cache.put("file2_key", "suggestion2");
        cache.put("other_key", "suggestion3");
        
        assertEquals(3, cache.getStats().size, "Should have 3 entries");
        
        cache.invalidatePattern("file1");
        assertEquals(2, cache.getStats().size, "Should have 2 entries after invalidation");
        assertNull(cache.get("file1_key"), "file1_key should be invalidated");
        assertNotNull(cache.get("file2_key"), "file2_key should still exist");
        assertNotNull(cache.get("other_key"), "other_key should still exist");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCachePerformance() {
        String baseContext = "public void method() {";
        
        // Performance test: Generate many cache keys quickly
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            String context = baseContext + " // variation " + i;
            String cacheKey = cache.generateCacheKey(mockEditor, context);
            cache.put(cacheKey, "suggestion " + i);
        }
        long duration = System.currentTimeMillis() - startTime;
        
        assertTrue(duration < 2000, "Cache operations should be fast (<2s for 1000 operations)");
        assertTrue(cache.getStats().size > 0, "Cache should contain entries");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testThreadSafety() throws InterruptedException {
        int numThreads = 5;
        int operationsPerThread = 50; // Reduced to stay well under cache limit
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int threadId = 0; threadId < numThreads; threadId++) {
            final int finalThreadId = threadId;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String context = "thread-" + finalThreadId + "-op-" + i;
                        String suggestion = "suggestion-" + finalThreadId + "-" + i;
                        String cacheKey = cache.generateCacheKey(mockEditor, context);
                        
                        // Put operation
                        cache.put(cacheKey, suggestion);
                        
                        // Get operation
                        String retrieved = cache.get(cacheKey);
                        assertEquals(suggestion, retrieved, "Retrieved value should match stored value");
                    }
                } catch (Exception e) {
                    fail("Thread safety test failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(8, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();
        
        // Verify final cache state
        SuggestionCache.CacheStats stats = cache.getStats();
        assertTrue(stats.size > 0, "Cache should contain entries after concurrent operations");
        assertEquals(0, stats.evictionCount, "Should have no evictions in this test");
    }

    @Test
    void testCacheStatsToString() {
        cache.put("key1", "value1");
        cache.get("key1"); // hit
        cache.get("nonexistent"); // miss
        
        SuggestionCache.CacheStats stats = cache.getStats();
        String statsString = stats.toString();
        
        assertNotNull(statsString, "Stats string should not be null");
        assertTrue(statsString.contains("size="), "Should contain size information");
        assertTrue(statsString.contains("hits="), "Should contain hit information");
        assertTrue(statsString.contains("hitRate="), "Should contain hit rate information");
    }

    @Test
    void testCacheKeyGeneration_WithNullContext() {
        // Test with null context - should not throw exception
        assertDoesNotThrow(() -> {
            String cacheKey = cache.generateCacheKey(mockEditor, null);
            assertNotNull(cacheKey, "Should generate key even with null context");
        }, "Should handle null context gracefully");
    }

    @Test
    void testCacheKeyGeneration_WithLongContext() {
        // Test with very long context
        StringBuilder longContext = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longContext.append("very long context line ").append(i).append("\n");
        }
        
        String cacheKey = cache.generateCacheKey(mockEditor, longContext.toString());
        assertNotNull(cacheKey, "Should generate key for long context");
        // In test environment, fallback generates shorter keys
        assertTrue(cacheKey.length() >= 8, "Cache key should be at least 8 characters");
    }
}