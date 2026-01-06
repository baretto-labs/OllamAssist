package fr.baretto.ollamassist.chat.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import fr.baretto.ollamassist.auth.AuthenticationHelper;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.plexus.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.baretto.ollamassist.chat.rag.RAGConstants.DEFAULT_EMBEDDING_MODEL;

@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DocumentIngestFactory {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BASIC_AUTH_FORMAT = "Basic %s";
    private static final String FALLBACK_EMBEDDING_MODEL = "nomic-embed-text";

    public static EmbeddingStoreIngestor create(EmbeddingStore<TextSegment> store) {
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
            // Try to use local DJL model first
            try {
                embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel(createExecutor());
                log.info("Using local BgeSmallEnV15Quantized embedding model");
            } catch (UnsatisfiedLinkError | ExceptionInInitializerError e) {
                // Fallback to Ollama if native libraries fail to load
                // See issue #145: https://github.com/baretto-labs/OllamAssist/issues/145
                log.warn("Local DJL embedding model failed to load (native library error). " +
                        "Falling back to Ollama model: {}. Error: {}", FALLBACK_EMBEDDING_MODEL, e.getMessage());
                embeddingModel = createOllamaEmbeddingModel(FALLBACK_EMBEDDING_MODEL);
            }
        } else {
            embeddingModel = createOllamaEmbeddingModel(OllamAssistSettings.getInstance().getEmbeddingModelName());
        }
        return embeddingModel;
    }

    /**
     * Creates an Ollama embedding model with configured settings.
     *
     * @param modelName Name of the Ollama model to use
     * @return Configured OllamaEmbeddingModel
     */
    private static OllamaEmbeddingModel createOllamaEmbeddingModel(String modelName) {
        OllamaEmbeddingModel.OllamaEmbeddingModelBuilder builder = new OllamaEmbeddingModel.OllamaEmbeddingModelBuilder();
        builder.baseUrl(OllamAssistSettings.getInstance().getEmbeddingOllamaUrl())
                .modelName(modelName)
                .timeout(OllamAssistSettings.getInstance().getTimeoutDuration());

        // Add authentication if configured
        if (AuthenticationHelper.isAuthenticationConfigured()) {
            Map<String, String> customHeaders = new HashMap<>();
            customHeaders.put(AUTHORIZATION_HEADER, String.format(BASIC_AUTH_FORMAT, AuthenticationHelper.createBasicAuthHeader()));
            builder.customHeaders(customHeaders);
        }

        return builder.build();
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

        private static final String THREAD_NAME_FORMAT = "%s-%d";

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);

            thread.setName(String.format(THREAD_NAME_FORMAT, threadNamePrefix, threadCounter.getAndIncrement()));

            // Optionnel : low priority in order to limit the CPU
            if (lowPriority) {
                thread.setPriority(Thread.MIN_PRIORITY); // = 1
            }
            thread.setDaemon(true);

            return thread;
        }
    }
}
