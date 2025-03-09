package fr.baretto.ollamassist.chat.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DocumentIngestorFactory {


    public static EmbeddingStoreIngestor create(EmbeddingStore<TextSegment> store) {
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel(createExecutor());
        return EmbeddingStoreIngestor.builder().embeddingStore(store).embeddingModel(embeddingModel).build();
    }


    private static Executor createExecutor() {
        int threadPoolSize = 2;
        int queueSize = 10000;

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threadPoolSize,
                threadPoolSize,
                30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                new CustomThreadFactory("embedding-model"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        executor.allowCoreThreadTimeOut(true);
        return executor;
    }


    private static class CustomThreadFactory implements ThreadFactory {

        private final String threadNamePrefix;
        private final AtomicInteger threadCounter = new AtomicInteger(1);
        private final boolean lowPriority;

        public CustomThreadFactory(String threadNamePrefix) {
            this(threadNamePrefix, false);
        }

        public CustomThreadFactory(String threadNamePrefix, boolean lowPriority) {
            this.threadNamePrefix = threadNamePrefix;
            this.lowPriority = lowPriority;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);

            thread.setName(threadNamePrefix + "-" + threadCounter.getAndIncrement());

            // Optionnel : low priority in order to limit the CPU
            if (lowPriority) {
                thread.setPriority(Thread.MIN_PRIORITY); // = 1
            }
            thread.setDaemon(true);

            return thread;
        }
    }
}
