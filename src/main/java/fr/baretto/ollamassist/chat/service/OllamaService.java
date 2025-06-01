package fr.baretto.ollamassist.chat.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
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
import fr.baretto.ollamassist.chat.rag.DocumentIndexingPipeline;
import fr.baretto.ollamassist.chat.rag.DocumentIngestFactory;
import fr.baretto.ollamassist.chat.rag.LuceneEmbeddingStore;
import fr.baretto.ollamassist.chat.rag.ProjectFileListener;
import fr.baretto.ollamassist.events.ChatModelModifiedNotifier;
import fr.baretto.ollamassist.events.ConversationNotifier;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import fr.baretto.ollamassist.setting.SettingsListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;


@Slf4j
public final class OllamaService implements Disposable, SettingsListener {

    private final Project project;
    private ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(15);
    private LuceneEmbeddingStore<TextSegment> embeddingStore;
    private ProjectFileListener projectFileListener;
    @Getter
    private Assistant assistant;
    private MessageBusConnection messageBusConnection;
    private DocumentIndexingPipeline documentIndexingPipeline;


    public OllamaService(@NotNull Project project) {
        this.project = project;
        this.documentIndexingPipeline = project.getService(DocumentIndexingPipeline.class);
        initialize();

        messageBusConnection.subscribe(ConversationNotifier.TOPIC, (ConversationNotifier) chatMemory::clear);
        project.getMessageBus().connect().subscribe(ChatModelModifiedNotifier.TOPIC, new ChatModelModifiedNotifier() {
            @Override
            public void onChatModelModified() {
                new Task.Backgroundable(project, "Reload chat model") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        assistant = initAssistant();
                    }
                }.queue();
            }
        });


    }

    private void initialize() {
        this.embeddingStore = project.getService(LuceneEmbeddingStore.class);
        this.projectFileListener = new ProjectFileListener(project, embeddingStore);
        this.messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
        this.messageBusConnection.setDefaultHandler(() -> {
        });
        this.messageBusConnection.subscribe(SettingsListener.TOPIC, this);
        this.assistant = initAssistant();
    }

    private Assistant initAssistant() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(OllamaService.class.getClassLoader());

            documentIndexingPipeline.processSingleDocument(Document.from("empty doc"));


            OllamaStreamingChatModel model = OllamaStreamingChatModel.builder()
                    .temperature(0.2)
                    .topK(70)
                    .baseUrl(OllamAssistSettings.getInstance().getOllamaUrl())
                    .modelName(OllamAssistSettings.getInstance().getChatModelName())
                    .timeout(OllamAssistSettings.getInstance().getTimeoutDuration())
                    .build();


            return AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(chatMemory)
                    .contentRetriever(EmbeddingStoreContentRetriever
                            .builder()
                            .embeddingModel(DocumentIngestFactory.createEmbeddingModel())
                            .dynamicMaxResults(query -> 3)
                            .dynamicMinScore(query -> {
                                return 0.85;
                            })
                            .embeddingStore(embeddingStore)
                            .build())
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
    public void settingsChanged(OllamAssistSettings.State newState) {
        this.assistant = initAssistant();
    }
}