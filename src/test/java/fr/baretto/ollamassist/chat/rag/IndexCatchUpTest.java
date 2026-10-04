package fr.baretto.ollamassist.chat.rag;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IndexCatchUpTest {

    private static final Instant INDEXED_AT = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant BEFORE = INDEXED_AT.minusSeconds(60);
    private static final Instant AFTER = INDEXED_AT.plusSeconds(60);

    @Test
    void aFileThatIsNotIndexedYetIsIndexed() {
        IndexCatchUp plan = IndexCatchUp.plan(Map.of(), Map.of("/p/New.java", BEFORE), Set.of("/p/New.java")::contains);

        assertThat(plan.toIndex()).containsExactly("/p/New.java");
        assertThat(plan.toRemove()).isEmpty();
    }

    @Test
    void aFileModifiedSinceItWasIndexedIsRemovedThenIndexedAgain() {
        IndexCatchUp plan = IndexCatchUp.plan(
                Map.of("/p/Foo.java", INDEXED_AT), Map.of("/p/Foo.java", AFTER), Set.of("/p/Foo.java")::contains);

        assertThat(plan.toRemove()).containsExactly("/p/Foo.java");
        assertThat(plan.toIndex()).containsExactly("/p/Foo.java");
    }

    @Test
    void aFileUnchangedSinceItWasIndexedIsLeftAlone() {
        IndexCatchUp plan = IndexCatchUp.plan(
                Map.of("/p/Foo.java", INDEXED_AT), Map.of("/p/Foo.java", BEFORE), Set.of("/p/Foo.java")::contains);

        assertThat(plan.isEmpty()).isTrue();
    }

    @Test
    void aFileDeletedFromDiskIsRemovedFromTheIndex() {
        IndexCatchUp plan = IndexCatchUp.plan(Map.of("/p/Gone.java", INDEXED_AT), Map.of(), path -> false);

        assertThat(plan.toRemove()).containsExactly("/p/Gone.java");
        assertThat(plan.toIndex()).isEmpty();
    }

    /**
     * File collection stops at the configured maximum, so an indexed file can be missing from it
     * while still on disk. Only a file that no longer exists is removed.
     */
    @Test
    void anIndexedFileStillOnDiskButNotCollectedIsKept() {
        IndexCatchUp plan = IndexCatchUp.plan(
                Map.of("/p/Foo.java", INDEXED_AT), Map.of(), Set.of("/p/Foo.java")::contains);

        assertThat(plan.isEmpty()).isTrue();
    }
}
