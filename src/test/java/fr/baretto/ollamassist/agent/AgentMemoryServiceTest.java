package fr.baretto.ollamassist.agent;

import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMemoryServiceTest {

    @TempDir
    Path tempDir;

    private AgentMemoryService service;

    @BeforeEach
    void setUp() {
        Project mockProject = mock(Project.class);
        when(mockProject.getBasePath()).thenReturn(tempDir.toString());
        service = new AgentMemoryService(mockProject);
    }

    // -------------------------------------------------------------------------
    // record + recentContextSummary
    // -------------------------------------------------------------------------

    @Test
    void emptyHistory_summaryIsBlank() {
        assertThat(service.recentContextSummary()).isBlank();
    }

    @Test
    void singleRecord_appearsInSummary() {
        service.record("cid-test", "Fix NPE in Foo.java", "COMPLETED", "All phases succeeded.");

        String summary = service.recentContextSummary();
        assertThat(summary).contains("Fix NPE in Foo.java");
        assertThat(summary).contains("COMPLETED");
    }

    @Test
    void abortedRecord_appearsInSummary() {
        service.record("cid-test", "Delete all logs", "ABORTED", "Too many destructive steps");

        String summary = service.recentContextSummary();
        assertThat(summary).contains("ABORTED");
        assertThat(summary).contains("Delete all logs");
    }

    @Test
    void multipleRecords_mostRecentFirst() {
        service.record("cid-1", "First goal", "COMPLETED", "ok");
        service.record("cid-2", "Second goal", "ABORTED", "failed");

        String summary = service.recentContextSummary();
        int posFirst = summary.indexOf("First goal");
        int posSecond = summary.indexOf("Second goal");
        // Second (most recent) should appear before First
        assertThat(posSecond).isLessThan(posFirst);
    }

    @Test
    void longReason_isTruncatedInRecord() {
        String longReason = "r".repeat(500);
        service.record("cid-test", "goal", "COMPLETED", longReason);

        String summary = service.recentContextSummary();
        // Summary should not contain the full 500-char reason
        assertThat(summary.length()).isLessThan(500 + 200);
    }

    // -------------------------------------------------------------------------
    // Rolling window — MAX_RECORDS cap
    // -------------------------------------------------------------------------

    @Test
    void recordsBeyondMaxCapacity_oldestDropped() {
        int total = AgentMemoryService.MAX_RECORDS + 5;
        for (int i = 0; i < total; i++) {
            service.record("cid-" + i, "goal-" + i, "COMPLETED", "ok");
        }

        String summary = service.recentContextSummary();
        // The 5 oldest (goal-0 to goal-4) must have been evicted
        for (int i = 0; i < 5; i++) {
            assertThat(summary).doesNotContain("goal-" + i + " ");
        }
        // The most recent must still be present
        assertThat(summary).contains("goal-" + (total - 1));
    }

    // -------------------------------------------------------------------------
    // Persistence across instances (same temp dir)
    // -------------------------------------------------------------------------

    @Test
    void recordsPersistAcrossInstances() {
        service.record("cid-test", "Persistent goal", "COMPLETED", "survived restart");

        // Create a new service instance pointing to the same directory
        Project mockProject2 = mock(Project.class);
        when(mockProject2.getBasePath()).thenReturn(tempDir.toString());
        AgentMemoryService service2 = new AgentMemoryService(mockProject2);

        assertThat(service2.recentContextSummary()).contains("Persistent goal");
    }

    // -------------------------------------------------------------------------
    // HMAC integrity — tampered file triggers empty fallback
    // -------------------------------------------------------------------------

    @Test
    void tamperedMemoryFile_triggersEmptyFallback() throws Exception {
        service.record("cid-test", "\1", "\2", "\3");

        // Overwrite the memory file with garbage to simulate tampering
        Path memoryFile = tempDir.resolve(".ollamassist/agent_memory.json");
        java.nio.file.Files.writeString(memoryFile, "[{\"goal\":\"injected\",\"status\":\"COMPLETED\"}]");

        // A new instance should detect HMAC mismatch and return empty history
        Project mockProject2 = mock(Project.class);
        when(mockProject2.getBasePath()).thenReturn(tempDir.toString());
        AgentMemoryService service2 = new AgentMemoryService(mockProject2);

        // Tampering must be detected — injected content should not appear
        assertThat(service2.recentContextSummary()).doesNotContain("injected");
    }

    // -------------------------------------------------------------------------
    // HMAC fail-closed — corrupted key (P1.5)
    // -------------------------------------------------------------------------

    @Test
    void corruptedKeyFile_triggersEmptyFallback() throws Exception {
        service.record("cid-test", "\1", "\2", "\3");

        // Overwrite the key file with garbage — key will be unreadable/invalid
        Path keyFile = tempDir.resolve(".ollamassist/memory.key");
        java.nio.file.Files.writeString(keyFile, "THIS_IS_NOT_HEX_ZZ!!");

        // A new instance must fail-closed: cannot verify → return empty history
        Project mockProject2 = mock(Project.class);
        when(mockProject2.getBasePath()).thenReturn(tempDir.toString());
        AgentMemoryService service2 = new AgentMemoryService(mockProject2);

        assertThat(service2.recentContextSummary()).doesNotContain("sensitive goal");
    }

    @Test
    void hmacFileMissingWhileMemoryFileExists_triggersEmptyFallback() throws Exception {
        service.record("cid-test", "\1", "\2", "\3");

        // Delete only the HMAC file — memory file still exists
        Path hmacFile = tempDir.resolve(".ollamassist/agent_memory.json.hmac");
        java.nio.file.Files.deleteIfExists(hmacFile);

        Project mockProject2 = mock(Project.class);
        when(mockProject2.getBasePath()).thenReturn(tempDir.toString());
        AgentMemoryService service2 = new AgentMemoryService(mockProject2);

        // Memory file without HMAC is treated as tampered — must return empty
        assertThat(service2.recentContextSummary()).doesNotContain("original goal");
    }

    // -------------------------------------------------------------------------
    // Q-3: truncation must not split a UTF-16 surrogate pair
    // -------------------------------------------------------------------------

    @Test
    void truncate_exactCodePointBoundary_doesNotSplitSurrogatePair() {
        // Emoji 🔥 is U+1F525 — encoded as a surrogate pair in Java (2 chars, 1 code point).
        // Truncating to 5 chars with String.substring(0,5) on "ABCD🔥" (length=6, 5 code points)
        // would return "ABCD🔥" correctly since the emoji is at index 4-5.
        // But truncating "AB🔥CD" (length=6, 5 code points) to 4 code points via substring(0,4)
        // would cut mid-surrogate. offsetByCodePoints must be used instead.
        String withEmoji = "AB\uD83D\uDD25CD"; // "AB🔥CD" — length=6, 5 code points
        String result = AgentMemoryService.truncate(withEmoji, 4);
        // Must end cleanly — no broken surrogate, contains "AB🔥"
        assertThat(result).startsWith("AB\uD83D\uDD25"); // AB🔥
        assertThat(result).doesNotContain("D"); // "D" is code point 5, must be cut
        // Result must be a valid string (no broken surrogate pair)
        assertThat(result.chars().allMatch(c -> true)).isTrue(); // no exception = no broken surrogate
    }

    @Test
    void truncate_shortString_returnedUnchanged() {
        assertThat(AgentMemoryService.truncate("hello", 10)).isEqualTo("hello");
    }

    @Test
    void truncate_null_returnsEmpty() {
        assertThat(AgentMemoryService.truncate(null, 10)).isEqualTo("");
    }

    // -------------------------------------------------------------------------
    // Null base path — graceful no-op
    // -------------------------------------------------------------------------

    @Test
    void nullBasePath_recordDoesNotThrow() {
        Project mockProject = mock(Project.class);
        when(mockProject.getBasePath()).thenReturn(null);
        AgentMemoryService noPathService = new AgentMemoryService(mockProject);

        noPathService.record("cid-test", "goal", "COMPLETED", "ok"); // must not throw
        assertThat(noPathService.recentContextSummary()).isBlank();
    }
}
