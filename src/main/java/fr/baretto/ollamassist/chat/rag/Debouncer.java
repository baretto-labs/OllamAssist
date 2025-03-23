package fr.baretto.ollamassist.chat.rag;

import java.util.concurrent.*;

public class Debouncer<K> {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ConcurrentHashMap<K, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private final long delay;
    private final TimeUnit unit;

    public Debouncer(long delay, TimeUnit unit) {
        this.delay = delay;
        this.unit = unit;
    }

    public void debounce(K key, Runnable task) {
        tasks.compute(key, (k, existingTask) -> {
            if (existingTask != null) {
                existingTask.cancel(false);
            }
            return scheduler.schedule(() -> {
                try {
                    task.run();
                } finally {
                    tasks.remove(key);
                }
            }, delay, unit);
        });
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
