package fr.baretto.ollamassist.chat.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class IndexRefreshPolicyTest {

    private static final String PROJECT = "my-project";

    private IndexRegistry registry;
    private RecordingReporter reporter;
    private int reindexRequests;
    private IndexRefreshPolicy policy;

    @BeforeEach
    void setUp() {
        registry = mock(IndexRegistry.class);
        reporter = new RecordingReporter();
        reindexRequests = 0;
        policy = new IndexRefreshPolicy(PROJECT, registry, reporter, () -> reindexRequests++);
    }

    @Test
    void switchingRagOnDoesNotFlagAnyIndexAsCorrupted() {
        policy.ragSwitchedOn();

        verify(registry, never()).markAllAsCorrupted();
        verify(registry, never()).markAsCorrupted(anyString());
    }

    @Test
    void switchingRagOnTellsTheUserNothing() {
        policy.ragSwitchedOn();

        assertThat(reporter.messages).isEmpty();
    }

    @Test
    void switchingRagOnRequestsIndexing() {
        policy.ragSwitchedOn();

        assertThat(reindexRequests).isEqualTo(1);
    }

    @Test
    void anEmbeddingModelChangeRebuildsTheIndexOfEveryProject() {
        policy.embeddingModelChanged();

        verify(registry).markAllAsCorrupted();
        assertThat(reindexRequests).isEqualTo(1);
    }

    @Test
    void anEmbeddingModelChangeIsReportedAsSuchAndNotAsACorruption() {
        policy.embeddingModelChanged();

        assertThat(reporter.messages)
                .singleElement()
                .satisfies(message -> {
                    assertThat(message).startsWith("INFO ");
                    assertThat(message).contains("Embedding model changed");
                    assertThat(message).doesNotContainIgnoringCase("corrupt");
                });
    }

    @Test
    void aCorruptionFlagsOnlyTheAffectedProject() {
        policy.indexCorrupted();

        verify(registry).markAsCorrupted(PROJECT);
        verify(registry, never()).markAllAsCorrupted();
        assertThat(reindexRequests).isEqualTo(1);
    }

    @Test
    void aCorruptionIsReportedAsAWarning() {
        policy.indexCorrupted();

        assertThat(reporter.messages)
                .singleElement()
                .satisfies(message -> {
                    assertThat(message).startsWith("WARNING ");
                    assertThat(message).containsIgnoringCase("corrupted");
                });
    }

    private static final class RecordingReporter implements IndexStatusReporter {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void info(String message) {
            messages.add("INFO " + message);
        }

        @Override
        public void warning(String message) {
            messages.add("WARNING " + message);
        }
    }
}
