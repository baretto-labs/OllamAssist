package fr.baretto.ollamassist.chat.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import fr.baretto.ollamassist.chat.ui.Context;
import fr.baretto.ollamassist.chat.rag.LuceneEmbeddingStore;
import fr.baretto.ollamassist.chat.rag.ProjectFileListener;
import fr.baretto.ollamassist.events.ConversationNotifier;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import fr.baretto.ollamassist.setting.SettingsListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;


@Slf4j
public final class OllamaService implements Disposable, SettingsListener {

    private final Project project;
    private LuceneEmbeddingStore<TextSegment> embeddingStore;
    private ProjectFileListener projectFileListener;
    @Getter
    private Assistant assistant;
    private MessageBusConnection messageBusConnection;


    public OllamaService(@NotNull Project project) {
        this.project = project;
        initialize();
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
            EmbeddingStoreIngestor.ingest(List.of(Document.from("empty document")), embeddingStore);


            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(15);
            messageBusConnection.subscribe(ConversationNotifier.TOPIC, (ConversationNotifier) chatMemory::clear);

            OllamaStreamingChatModel model = OllamaStreamingChatModel.builder()
                    .temperature(0.3)
                    .topK(40)
                    .topP(0.9)
                    .baseUrl("http://localhost:11434")
                    .modelName(OllamAssistSettings.getInstance().getChatModelName())
                    .build();


            return AiServices.builder(Assistant.class)
                    .streamingChatLanguageModel(model)
                    .chatMemory(chatMemory)
                    .contentRetriever(EmbeddingStoreContentRetriever
                            .builder()
                            .dynamicMaxResults(query -> 3)
                            .dynamicMinScore(query -> {
                                int length = query.text().length();
                                if (length > 100) return 0.85;
                                if (length > 50) return 0.65;
                                return 0.5;
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

    public void forceInit(Context context) {
        projectFileListener.forceLoad(context);
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