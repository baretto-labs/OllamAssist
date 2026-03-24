package fr.baretto.ollamassist.conversation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationServiceTest {

    @TempDir
    Path tempDir;

    private ConversationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ConversationRepository(tempDir);
    }

    // --- Conversation domain tests ---

    @Test
    void newConversation_hasDefaultTitle() {
        Conversation conversation = new Conversation();

        assertThat(conversation.getTitle()).isEqualTo("New conversation");
    }

    @Test
    void addMessage_firstUserMessage_generatesTitleFromContent() {
        Conversation conversation = new Conversation();

        conversation.addMessage(ConversationMessage.user("How do I use Java streams?"));

        assertThat(conversation.getTitle()).isEqualTo("How do I use Java streams?");
    }

    @Test
    void addMessage_firstUserMessage_truncatesTitleAt60Chars() {
        Conversation conversation = new Conversation();
        String longMessage = "A".repeat(80);

        conversation.addMessage(ConversationMessage.user(longMessage));

        assertThat(conversation.getTitle()).endsWith("...");
        assertThat(conversation.getTitle().length()).isLessThanOrEqualTo(63);
    }

    @Test
    void addMessage_secondUserMessage_doesNotChangeTitle() {
        Conversation conversation = new Conversation();
        conversation.addMessage(ConversationMessage.user("First message"));
        String titleAfterFirst = conversation.getTitle();

        conversation.addMessage(ConversationMessage.user("Second message"));

        assertThat(conversation.getTitle()).isEqualTo(titleAfterFirst);
    }

    @Test
    void addMessage_assistantFirst_doesNotGenerateTitle() {
        Conversation conversation = new Conversation();

        conversation.addMessage(ConversationMessage.assistant("Hello!"));

        assertThat(conversation.getTitle()).isEqualTo("New conversation");
    }

    @Test
    void addMessage_updatesTimestamp() throws InterruptedException {
        Conversation conversation = new Conversation();
        long before = conversation.getUpdatedAt();
        Thread.sleep(5);

        conversation.addMessage(ConversationMessage.user("Hello"));

        assertThat(conversation.getUpdatedAt()).isGreaterThan(before);
    }

    @Test
    void getMessages_returnsUnmodifiableView() {
        Conversation conversation = new Conversation();
        conversation.addMessage(ConversationMessage.user("Hello"));

        List<ConversationMessage> messages = conversation.getMessages();

        assertThat(messages).hasSize(1);
        assertThat(messages).satisfies(list ->
                assertThat(list).isUnmodifiable()
        );
    }

    // --- Repository integration ---

    @Test
    void saveAndReload_conversationWithMessages_preservesAll() {
        Conversation conversation = new Conversation();
        conversation.addMessage(ConversationMessage.user("Question?"));
        conversation.addMessage(ConversationMessage.assistant("Answer!"));
        repository.save(conversation);

        List<Conversation> loaded = repository.loadAll();

        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getMessages())
                .extracting(ConversationMessage::getContent)
                .containsExactly("Question?", "Answer!");
    }

    @Test
    void saveAndReload_preservesMessageRoles() {
        Conversation conversation = new Conversation();
        conversation.addMessage(ConversationMessage.user("User msg"));
        conversation.addMessage(ConversationMessage.assistant("AI msg"));
        repository.save(conversation);

        Conversation loaded = repository.loadAll().get(0);

        assertThat(loaded.getMessages().get(0).getRole()).isEqualTo(ConversationMessage.Role.USER);
        assertThat(loaded.getMessages().get(1).getRole()).isEqualTo(ConversationMessage.Role.ASSISTANT);
    }

    @Test
    void saveAndReload_preservesTimestamps() {
        Conversation conversation = new Conversation();
        ConversationMessage msg = ConversationMessage.user("test");
        conversation.addMessage(msg);
        repository.save(conversation);

        Conversation loaded = repository.loadAll().get(0);

        assertThat(loaded.getMessages().get(0).getTimestamp()).isEqualTo(msg.getTimestamp());
        assertThat(loaded.getCreatedAt()).isEqualTo(conversation.getCreatedAt());
    }

    @Test
    void delete_afterSave_conversationIsGone() {
        Conversation conversation = new Conversation();
        repository.save(conversation);
        assertThat(repository.loadAll()).hasSize(1);

        repository.delete(conversation.getId());

        assertThat(repository.loadAll()).isEmpty();
    }

    // --- ConversationMessage factory methods ---

    @Test
    void conversationMessage_user_hasCorrectRole() {
        ConversationMessage msg = ConversationMessage.user("hi");

        assertThat(msg.getRole()).isEqualTo(ConversationMessage.Role.USER);
        assertThat(msg.getContent()).isEqualTo("hi");
        assertThat(msg.getTimestamp()).isPositive();
    }

    @Test
    void conversationMessage_assistant_hasCorrectRole() {
        ConversationMessage msg = ConversationMessage.assistant("reply");

        assertThat(msg.getRole()).isEqualTo(ConversationMessage.Role.ASSISTANT);
        assertThat(msg.getContent()).isEqualTo("reply");
    }

    @Test
    void toString_returnsTitle() {
        Conversation conversation = new Conversation();
        conversation.addMessage(ConversationMessage.user("My title"));

        assertThat(conversation.toString()).isEqualTo("My title");
    }
}
