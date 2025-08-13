package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.project.Project;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.codehaus.plexus.util.StringUtils;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.baretto.ollamassist.chat.rag.RAGConstants.DEFAULT_EMBEDDING_MODEL;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DocumentIngestFactory {


    public static EmbeddingStoreIngestor create(EmbeddingStore<TextSegment> store, Project project) {
        EmbeddingModel embeddingModel;
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(DocumentIngestFactory.class.getClassLoader());
        try {
            embeddingModel = createEmbeddingModel();
            return EmbeddingStoreIngestor
                    .builder()
                    .embeddingStore(store)
                    .embeddingModel(embeddingModel)
                    .build();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    public static EmbeddingModel createEmbeddingModel() {
        EmbeddingModel embeddingModel;
        if (StringUtils.equalsIgnoreCase(DEFAULT_EMBEDDING_MODEL, OllamAssistSettings.getInstance().getEmbeddingModelName())
                || org.apache.commons.lang3.StringUtils.isBlank(OllamAssistSettings.getInstance().getEmbeddingModelName())) {
            embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel(createExecutor());
        } else {
            OllamaEmbeddingModel.OllamaEmbeddingModelBuilder builder = new OllamaEmbeddingModel.OllamaEmbeddingModelBuilder();
            embeddingModel = builder.baseUrl(OllamAssistSettings.getInstance().getOllamaUrl())
                    .modelName(OllamAssistSettings.getInstance().getEmbeddingModelName())
                    .timeout(OllamAssistSettings.getInstance().getTimeoutDuration())
                    .build();
        }
        return embeddingModel;
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
