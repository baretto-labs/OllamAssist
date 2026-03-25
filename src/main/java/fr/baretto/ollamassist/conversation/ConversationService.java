package fr.baretto.ollamassist.conversation;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service(Service.Level.PROJECT)
@Slf4j
public final class ConversationService {

    private final ConversationRepository repository;
    private final List<Conversation> conversations = new ArrayList<>();
    private volatile Conversation activeConversation;

    public ConversationService(@NotNull Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) {
            log.warn("Project base path is null, conversation persistence disabled");
            this.repository = null;
            this.activeConversation = new Conversation();
            conversations.add(activeConversation);
            return;
        }
        Path projectPath = Paths.get(basePath);
        this.repository = new ConversationRepository(projectPath);
        loadAndInitialize();
    }

    private void loadAndInitialize() {
        List<Conversation> loaded = repository.loadAll();
        synchronized (conversations) {
            conversations.addAll(loaded);
            conversations.sort(Comparator.comparingLong(Conversation::getUpdatedAt).reversed());
        }
        if (conversations.isEmpty()) {
            activeConversation = createConversation();
        } else {
            activeConversation = conversations.get(0);
        }
    }

    public Conversation createConversation() {
        Conversation conversation = new Conversation();
        synchronized (conversations) {
            conversations.add(0, conversation);
        }
        persist(conversation);
        return conversation;
    }

    public void setActiveConversation(Conversation conversation) {
        this.activeConversation = conversation;
    }

    public Conversation getActiveConversation() {
        return activeConversation;
    }

    public List<Conversation> getAllConversations() {
        synchronized (conversations) {
            return new ArrayList<>(conversations);
        }
    }

    public synchronized void addMessage(ConversationMessage message) {
        activeConversation.addMessage(message);
        persist(activeConversation);
        synchronized (conversations) {
            conversations.sort(Comparator.comparingLong(Conversation::getUpdatedAt).reversed());
        }
    }

    public void delete(Conversation conversation) {
        synchronized (conversations) {
            conversations.remove(conversation);
        }
        if (repository != null) {
            repository.delete(conversation.getId());
        }
        if (activeConversation == conversation) {
            if (conversations.isEmpty()) {
                activeConversation = createConversation();
            } else {
                activeConversation = conversations.get(0);
            }
        }
    }

    private void persist(Conversation conversation) {
        if (repository != null) {
            repository.save(conversation);
        }
    }
}
