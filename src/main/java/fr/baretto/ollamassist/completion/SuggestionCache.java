package fr.baretto.ollamassist.completion;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.openapi.editor.Editor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.MessageDigest;
import java.time.Duration;

/**
 * Intelligent LRU cache for code completion suggestions.
 * Uses Caffeine cache with eviction policies and statistics.
 */
@Slf4j
public class SuggestionCache {
    
    private final Cache<String, CachedSuggestion> cache;
    
    public SuggestionCache() {
        this.cache = Caffeine.newBuilder()
            .maximumSize(500)                           // Max 500 cached suggestions
            .expireAfterWrite(Duration.ofMinutes(10))   // Expire after 10 minutes
            .expireAfterAccess(Duration.ofMinutes(5))   // Expire if not accessed for 5 minutes
            .recordStats()                              // Enable statistics
            .build();
    }
    
    /**
     * Generates a cache key based on editor state and context.
     * Takes into account file content hash, cursor position, and surrounding context.
     */
    @NotNull
    public String generateCacheKey(@NotNull Editor editor, @Nullable String context) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            
            // Access editor data safely with ReadAction
            String keyData = com.intellij.openapi.application.ApplicationManager.getApplication()
                .runReadAction((com.intellij.openapi.util.Computable<String>) () -> {
                    // Include file modification timestamp for cache invalidation
                    long modificationStamp = editor.getDocument().getModificationStamp();
                    
                    // Include cursor position context (line number and position in line)
                    int offset = editor.getCaretModel().getOffset();
                    int lineNumber = editor.getDocument().getLineNumber(offset);
                    int columnNumber = offset - editor.getDocument().getLineStartOffset(lineNumber);
                    
                    // Create composite key
                    String safeContext = context != null ? context : "";
                    String contextPart = safeContext.length() > 200 ? safeContext.substring(safeContext.length() - 200) : safeContext;
                    return String.format(
                        "mod:%d|line:%d|col:%d|ctx:%s",
                        modificationStamp,
                        lineNumber,
                        columnNumber,
                        contextPart
                    );
                });
            
            byte[] hash = md.digest(keyData.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString().substring(0, 16); // Use first 16 characters
            
        } catch (Exception e) {
            log.warn("Failed to generate cache key, using fallback", e);
            // Use context only for consistent fallback keys (no timestamp)
            return String.valueOf((context != null ? context : "").hashCode());
        }
    }
    
    /**
     * Retrieves a cached suggestion if available and still valid.
     */
    @Nullable
    public String get(@NotNull String key) {
        CachedSuggestion cached = cache.getIfPresent(key);
        if (cached != null) {
            log.debug("Cache hit for key: {}", key.substring(0, Math.min(8, key.length())));
            return cached.suggestion;
        }
        
        log.debug("Cache miss for key: {}", key.substring(0, Math.min(8, key.length())));
        return null;
    }
    
    /**
     * Stores a suggestion in the cache with metadata.
     */
    public void put(@NotNull String key, @NotNull String suggestion) {
        CachedSuggestion cached = new CachedSuggestion(
            suggestion,
            System.currentTimeMillis(),
            Thread.currentThread().getName()
        );
        
        cache.put(key, cached);
        log.debug("Cached suggestion for key: {} (length: {})", key.substring(0, Math.min(8, key.length())), suggestion.length());
    }
    
    /**
     * Returns cache statistics for monitoring and debugging.
     */
    @NotNull
    public CacheStats getStats() {
        var stats = cache.stats();
        return new CacheStats(
            cache.estimatedSize(),
            stats.hitCount(),
            stats.missCount(),
            stats.hitRate(),
            stats.evictionCount(),
            stats.loadCount()
        );
    }
    
    /**
     * Clears the entire cache.
     */
    public void clear() {
        cache.invalidateAll();
        log.debug("Cache cleared");
    }
    
    /**
     * Removes entries that match a specific pattern (e.g., for a specific file).
     */
    public void invalidatePattern(@NotNull String pattern) {
        cache.asMap().entrySet().removeIf(entry -> entry.getKey().contains(pattern));
        log.debug("Invalidated cache entries matching pattern: {}", pattern);
    }
    
    /**
     * Represents a cached suggestion with metadata.
     */
    private static class CachedSuggestion {
        final String suggestion;
        final long timestamp;
        final String threadName;
        
        CachedSuggestion(String suggestion, long timestamp, String threadName) {
            this.suggestion = suggestion;
            this.timestamp = timestamp;
            this.threadName = threadName;
        }
    }
    
    /**
     * Cache statistics for monitoring.
     */
    public static class CacheStats {
        public final long size;
        public final long hitCount;
        public final long missCount;
        public final double hitRate;
        public final long evictionCount;
        public final long loadCount;
        
        public CacheStats(long size, long hitCount, long missCount, double hitRate, long evictionCount, long loadCount) {
            this.size = size;
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.hitRate = hitRate;
            this.evictionCount = evictionCount;
            this.loadCount = loadCount;
        }
        
        @Override
        public String toString() {
            return String.format(
                "CacheStats{size=%d, hits=%d, misses=%d, hitRate=%.2f%%, evictions=%d}",
                size, hitCount, missCount, hitRate * 100, evictionCount
            );
        }
    }
}