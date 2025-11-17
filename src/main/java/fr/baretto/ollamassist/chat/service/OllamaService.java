package fr.baretto.ollamassist.chat.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import fr.baretto.ollamassist.auth.AuthenticationHelper;
import fr.baretto.ollamassist.chat.rag.*;
import fr.baretto.ollamassist.chat.tools.FileCreator;
import fr.baretto.ollamassist.events.ChatModelModifiedNotifier;
import fr.baretto.ollamassist.events.ConversationNotifier;
import fr.baretto.ollamassist.setting.ActionsSettings;
import fr.baretto.ollamassist.setting.ModelListener;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;


@Service(Service.Level.PROJECT)
@Slf4j
public final class OllamaService implements Disposable, ModelListener {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BASIC_AUTH_FORMAT = "Basic %s";

    private final Project project;
    private final ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(25);
    private LuceneEmbeddingStore<TextSegment> embeddingStore;
    private ProjectFileListener projectFileListener;
    @Getter
    private Assistant assistant;
    private MessageBusConnection messageBusConnection;
    private final DocumentIndexingPipeline documentIndexingPipeline;


    public OllamaService(@NotNull Project project) {
        this.project = project;
        this.documentIndexingPipeline = project.getService(DocumentIndexingPipeline.class);
        initialize();

        messageBusConnection.subscribe(ConversationNotifier.TOPIC, (ConversationNotifier) chatMemory::clear);
        project.getMessageBus().connect().subscribe(ChatModelModifiedNotifier.TOPIC,
                (ChatModelModifiedNotifier) () -> new Task.Backgroundable(project, "Reload chat model") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        assistant = initAssistant();
                    }
                }.queue());


    }

    private void initialize() {
        this.embeddingStore = project.getService(LuceneEmbeddingStore.class);
        this.projectFileListener = new ProjectFileListener(project, embeddingStore);
        this.messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
        this.messageBusConnection.setDefaultHandler(() -> {
        });
        this.messageBusConnection.subscribe(ModelListener.TOPIC, this);
        this.assistant = initAssistant();
    }

    private Assistant initAssistant() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(OllamaService.class.getClassLoader());

            documentIndexingPipeline.processSingleDocument(Document.from("empty doc"));


            OllamaStreamingChatModel.OllamaStreamingChatModelBuilder builder = OllamaStreamingChatModel.builder()
                    .temperature(0.7)
                    .topK(50)
                    .topP(0.85)
                    .baseUrl(OllamAssistSettings.getInstance().getChatOllamaUrl())
                    .modelName(OllamAssistSettings.getInstance().getChatModelName())
                    .timeout(OllamAssistSettings.getInstance().getTimeoutDuration());

            // Add authentication if configured
            if (AuthenticationHelper.isAuthenticationConfigured()) {
                Map<String, String> customHeaders = new HashMap<>();
                customHeaders.put(AUTHORIZATION_HEADER, String.format(BASIC_AUTH_FORMAT, AuthenticationHelper.createBasicAuthHeader()));
                builder.customHeaders(customHeaders);
            }

            OllamaStreamingChatModel model = builder.build();

            var aiServicesBuilder = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(chatMemory);

            // Only add tools if enabled in settings
            if (ActionsSettings.getInstance().isToolsEnabled()) {
                log.info("Tools are enabled - adding FileCreator tool");
                aiServicesBuilder.tools(new FileCreator(project));
            } else {
                log.info("Tools are disabled in settings");
            }

            return aiServicesBuilder
                    .contentRetriever(new ContextRetriever(
                            EmbeddingStoreContentRetriever
                                    .builder()
                                    .embeddingModel(DocumentIngestFactory.createEmbeddingModel())
                                    .dynamicMaxResults(query -> 2)
                                    .dynamicMinScore(query -> 0.80)
                                    .embeddingStore(embeddingStore)
                                    .build(),
                            project))
                    .build();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    public void init() {
        projectFileListener.load();
    }

    @Override
    public void dispose() {
        projectFileListener.dispose();
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }
        if (embeddingStore != null) {
            embeddingStore.close();
        }
    }

    @Override
    public void reloadModel() {
        this.assistant = initAssistant();
    }
}