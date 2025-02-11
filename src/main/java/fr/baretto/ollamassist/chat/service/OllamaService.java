package fr.baretto.ollamassist.chat.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.messages.MessageBusConnection;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import fr.baretto.ollamassist.chat.ui.Context;
import fr.baretto.ollamassist.chat.rag.LuceneEmbeddingStore;
import fr.baretto.ollamassist.chat.rag.StoreFileListener;
import fr.baretto.ollamassist.events.ConversationNotifier;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import fr.baretto.ollamassist.setting.SettingsListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class OllamaService {

    private final LuceneEmbeddingStore<TextSegment> embeddingStore;
    private final StoreFileListener storeFileListener;
    @Getter
    private Assistant assistant;


    public OllamaService() {
        this.embeddingStore = ApplicationManager.getApplication().getService(LuceneEmbeddingStore.class);
        this.storeFileListener = new StoreFileListener(embeddingStore);

        ApplicationManager.getApplication().getMessageBus()
                .connect()
                .subscribe(SettingsListener.TOPIC, (SettingsListener) this::reloadModel);
        assistant = init();
    }

    private void reloadModel(OllamAssistSettings.State newState) {
        assistant = init();
    }

    private Assistant init() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(OllamaService.class.getClassLoader());

        VirtualFileManager.getInstance().addVirtualFileListener(storeFileListener);


        EmbeddingStoreIngestor.ingest(List.of(Document.from("empty document")), embeddingStore);
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(15);


        MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus()
                .connect();
        connection.subscribe(ConversationNotifier.TOPIC, (ConversationNotifier) chatMemory::clear);

        OllamaStreamingChatModel model = OllamaStreamingChatModel
                .builder()
                .temperature(0.6)
                .baseUrl("http://localhost:11434")
                .modelName(OllamAssistSettings.getInstance().getChatModelName())
                .build();

        Assistant createdAssistant = AiServices.builder(Assistant.class)
                .streamingChatLanguageModel(model)
                .chatMemory(chatMemory)
                .contentRetriever(EmbeddingStoreContentRetriever.from(embeddingStore))
                .build();

        Thread.currentThread().setContextClassLoader(classLoader);
        return createdAssistant;
    }

    public void init(Context context) {
        storeFileListener.load(context);
    }

    public void forceInit(Context context) {
        storeFileListener.forceLoad(context);
    }



}

