package fr.baretto.ollamassist.conversation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ConversationRepositoryTest {

    @TempDir
    Path tempDir;

    private ConversationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ConversationRepository(tempDir);
    }

    @AfterEach
    void tearDown() {
        // TempDir is cleaned automatically
    }

    @Test
    void loadAll_returnsEmptyList_whenDirectoryDoesNotExist() {
        Path nonExistent = tempDir.resolve("nonexistent");
        ConversationRepository repo = new ConversationRepository(nonExistent);

        List<Conversation> result = repo.loadAll();

        assertThat(result).isEmpty();
    }

    @Test
    void save_createsJsonFile() {
        Conversation conversation = new Conversation();

        repository.save(conversation);

        Path expectedFile = tempDir.resolve(".ollamassist/conversations/" + conversation.getId() + ".json");
        assertThat(Files.exists(expectedFile)).isTrue();
    }

    @Test
    void saveAndLoad_roundTrip_preservesData() {
        Conversation conversation = new Conversation();
        conversation.addMessage(ConversationMessage.user("Hello"));
        conversation.addMessage(ConversationMessage.assistant("Hi there!"));

        repository.save(conversation);
        List<Conversation> loaded = repository.loadAll();

        assertThat(loaded).hasSize(1);
        Conversation loaded0 = loaded.get(0);
        assertThat(loaded0.getId()).isEqualTo(conversation.getId());
        assertThat(loaded0.getTitle()).isEqualTo(conversation.getTitle());
        assertThat(loaded0.getMessages()).hasSize(2);
        assertThat(loaded0.getMessages().get(0).getRole()).isEqualTo(ConversationMessage.Role.USER);
        assertThat(loaded0.getMessages().get(0).getContent()).isEqualTo("Hello");
        assertThat(loaded0.getMessages().get(1).getRole()).isEqualTo(ConversationMessage.Role.ASSISTANT);
        assertThat(loaded0.getMessages().get(1).getContent()).isEqualTo("Hi there!");
    }

    @Test
    void loadAll_loadsMultipleConversations() {
        Conversation c1 = new Conversation();
        Conversation c2 = new Conversation();
        repository.save(c1);
        repository.save(c2);

        List<Conversation> result = repository.loadAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Conversation::getId)
                .containsExactlyInAnyOrder(c1.getId(), c2.getId());
    }

    @Test
    void delete_removesFile() {
        Conversation conversation = new Conversation();
        repository.save(conversation);
        Path expectedFile = tempDir.resolve(".ollamassist/conversations/" + conversation.getId() + ".json");
        assertThat(Files.exists(expectedFile)).isTrue();

        repository.delete(conversation.getId());

        assertThat(Files.exists(expectedFile)).isFalse();
    }

    @Test
    void delete_nonExistentId_doesNotThrow() {
        assertThatCode(() -> repository.delete("nonexistent-id")).doesNotThrowAnyException();
    }

    @Test
    void save_updatesExistingFile_onSecondSave() {
        Conversation conversation = new Conversation();
        repository.save(conversation);

        conversation.addMessage(ConversationMessage.user("New message"));
        repository.save(conversation);

        List<Conversation> loaded = repository.loadAll();
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getMessages()).hasSize(1);
    }

    @Test
    void loadAll_skipsCorruptedFiles() throws Exception {
        Path conversationsDir = tempDir.resolve(".ollamassist/conversations");
        Files.createDirectories(conversationsDir);
        Files.writeString(conversationsDir.resolve("corrupt.json"), "not valid json {{{");

        Conversation valid = new Conversation();
        repository.save(valid);

        List<Conversation> result = repository.loadAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(valid.getId());
    }
}
