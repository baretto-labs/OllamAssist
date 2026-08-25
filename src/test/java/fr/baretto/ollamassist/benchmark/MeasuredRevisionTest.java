package fr.baretto.ollamassist.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MeasuredRevisionTest {

    @Test
    void shouldReturnTheShortCommitOfTheWorkingTree() {
        String revision = MeasuredRevision.of(Path.of("."));

        assertThat(revision).matches("[0-9a-f]{7,40}(-dirty)?");
    }

    @Test
    void shouldMarkTheRevisionDirtyWhenTheWorkingTreeHasUncommittedChanges() {
        String revision = MeasuredRevision.of(Path.of("."));

        assertThat(revision).doesNotContain(" ");
    }

    @Test
    void shouldReturnUnknownWhenTheDirectoryIsNotAGitRepository(@TempDir Path notARepo) {
        assertThat(MeasuredRevision.of(notARepo)).isEqualTo("unknown");
    }

    @Test
    void shouldReturnUnknownWhenTheDirectoryDoesNotExist() {
        assertThat(MeasuredRevision.of(Path.of("/nowhere/at/all"))).isEqualTo("unknown");
    }
}
