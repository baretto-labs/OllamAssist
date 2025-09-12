package fr.baretto.ollamassist.completion;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.Alarm;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Debounces completion requests to avoid multiple simultaneous API calls.
 * Ensures only the latest request is processed when multiple requests come rapidly.
 */
@Slf4j
public class CompletionDebouncer {
    
    private final ConcurrentHashMap<String, DebounceEntry> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    /**
     * Debounces a completion request. Only executes the latest request after the delay.
     * 
     * @param key Unique key for the request (e.g., editor hashcode)
     * @param delayMs Delay in milliseconds before execution
     * @param task Task to execute after debounce delay
     */
    public void debounce(@NotNull String key, int delayMs, @NotNull Runnable task) {
        int requestId = requestCounter.incrementAndGet();
        log.debug("Debouncing completion request {} for key: {}, delay: {}ms", requestId, key, delayMs);
        
        // Cancel any existing request for this key
        DebounceEntry existingEntry = pendingRequests.remove(key);
        if (existingEntry != null) {
            existingEntry.cancel();
            log.debug("Cancelled previous request {} for key: {}", existingEntry.requestId, key);
        }
        
        Application application = ApplicationManager.getApplication();
        if (application == null) {
            // Running in test environment, use ScheduledExecutorService
            log.debug("Test mode: Scheduling debounced request {} with ScheduledExecutorService", requestId);
            
            var future = scheduler.schedule(() -> {
                pendingRequests.remove(key);
                
                try {
                    log.debug("Executing debounced task {} for key: {}", requestId, key);
                    task.run();
                } catch (Exception e) {
                    log.error("Error executing debounced task for key: " + key, e);
                }
            }, delayMs, TimeUnit.MILLISECONDS);
            
            DebounceEntry newEntry = new DebounceEntry(requestId, null, future);
            pendingRequests.put(key, newEntry);
            
        } else {
            // Running in IntelliJ environment, use Alarm
            log.debug("IntelliJ mode: Scheduling debounced request {} with Alarm", requestId);
            Alarm alarm = new Alarm();
            DebounceEntry newEntry = new DebounceEntry(requestId, alarm, null);
            pendingRequests.put(key, newEntry);
            
            alarm.addRequest(() -> {
                pendingRequests.remove(key);
                
                application.executeOnPooledThread(() -> {
                    try {
                        log.debug("Executing debounced task {} for key: {}", requestId, key);
                        task.run();
                    } catch (Exception e) {
                        log.error("Error executing debounced task for key: " + key, e);
                    }
                });
            }, delayMs);
        }
    }
    
    /**
     * Cancels all pending requests for a specific key.
     */
    public void cancel(@NotNull String key) {
        DebounceEntry entry = pendingRequests.remove(key);
        if (entry != null) {
            entry.cancel();
            log.debug("Cancelled debounced request {} for key: {}", entry.requestId, key);
        }
    }
    
    /**
     * Cancels all pending requests.
     */
    public void cancelAll() {
        pendingRequests.values().forEach(DebounceEntry::cancel);
        pendingRequests.clear();
        log.debug("Cancelled all pending debounced requests");
    }
    
    /**
     * Returns the number of currently pending requests.
     */
    public int getPendingRequestCount() {
        return pendingRequests.size();
    }
    
    /**
     * Checks if there's a pending request for the given key.
     */
    public boolean hasPendingRequest(@NotNull String key) {
        return pendingRequests.containsKey(key);
    }
    
    /**
     * Gets debugging information about pending requests.
     */
    @NotNull
    public String getDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("CompletionDebouncer Stats:\n");
        info.append("- Pending requests: ").append(pendingRequests.size()).append("\n");
        info.append("- Total requests created: ").append(requestCounter.get()).append("\n");
        
        if (!pendingRequests.isEmpty()) {
            info.append("- Pending keys: ");
            pendingRequests.keySet().forEach(key -> 
                info.append(key).append("(").append(pendingRequests.get(key).requestId).append(") ")
            );
        }
        
        return info.toString();
    }
    
    /**
     * Disposes the debouncer and cancels all pending requests.
     */
    public void dispose() {
        cancelAll();
        scheduler.shutdown();
        log.debug("CompletionDebouncer disposed");
    }
    
    /**
     * Internal class to track debounce entries.
     */
    private static class DebounceEntry {
        final int requestId;
        final Alarm alarm;
        final ScheduledFuture<?> future;
        
        DebounceEntry(int requestId, Alarm alarm, ScheduledFuture<?> future) {
            this.requestId = requestId;
            this.alarm = alarm;
            this.future = future;
        }
        
        void cancel() {
            if (alarm != null) {
                alarm.cancelAllRequests();
            }
            if (future != null) {
                future.cancel(false);
            }
        }
    }
}