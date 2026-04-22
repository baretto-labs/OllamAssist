package fr.baretto.ollamassist.agent;

import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditLoggerTest {

    @TempDir
    Path tempDir;

    private AuditLogger logger;

    @BeforeEach
    void setUp() {
        Project mockProject = mock(Project.class);
        when(mockProject.getBasePath()).thenReturn(tempDir.toString());
        logger = new AuditLogger(mockProject);
    }

    // -------------------------------------------------------------------------
    // Basic write behaviour
    // -------------------------------------------------------------------------

    @Test
    void record_singleEntry_writesJsonlLine() throws Exception {
        logger.record("cid-test", "FILE_READ", "read Foo.java", Set.of("path"), true, null);
        logger.dispose();

        Path auditFile = tempDir.resolve(".ollamassist/agent_audit.jsonl");
        assertThat(auditFile).exists();
        String content = Files.readString(auditFile);
        assertThat(content).contains("FILE_READ");
        assertThat(content).contains("read Foo.java");
        assertThat(content).contains("\"ok\":true");
    }

    @Test
    void record_multipleEntries_allAppearInFile() throws Exception {
        logger.record("cid-test", "FILE_READ",   "read A.java",  Set.of("path"), true, null);
        logger.record("cid-test", "CODE_SEARCH", "search main",  Set.of("query"), true, null);
        logger.record("cid-test", "GIT_STATUS",  "check status", Set.of(), true, null);
        logger.dispose();

        Path auditFile = tempDir.resolve(".ollamassist/agent_audit.jsonl");
        List<String> lines = Files.readAllLines(auditFile);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).contains("FILE_READ");
        assertThat(lines.get(1)).contains("CODE_SEARCH");
        assertThat(lines.get(2)).contains("GIT_STATUS");
    }

    @Test
    void record_immediatelyReadableAfterWrite() throws Exception {
        // Records must be flushed — readable without calling dispose()
        logger.record("cid-test", "FILE_READ", "read Foo.java", Set.of("path"), true, null);

        Path auditFile = tempDir.resolve(".ollamassist/agent_audit.jsonl");
        String content = Files.readString(auditFile);
        assertThat(content).contains("FILE_READ");
    }

    @Test
    void record_failureEntry_includesErrorSummary() throws Exception {
        logger.record("cid-test", "FILE_EDIT", "edit Bar.java", Set.of("path"), false, "File not found");
        logger.dispose();

        Path auditFile = tempDir.resolve(".ollamassist/agent_audit.jsonl");
        String content = Files.readString(auditFile);
        assertThat(content).contains("\"ok\":false");
        assertThat(content).contains("File not found");
    }

    // -------------------------------------------------------------------------
    // Null base path — graceful no-op
    // -------------------------------------------------------------------------

    @Test
    void nullBasePath_recordDoesNotThrow() {
        Project mockProject = mock(Project.class);
        when(mockProject.getBasePath()).thenReturn(null);
        AuditLogger noPathLogger = new AuditLogger(mockProject);

        noPathLogger.record("cid-test", "FILE_READ", "read", Set.of("path"), true, null);
        noPathLogger.dispose(); // must not throw
    }

    // -------------------------------------------------------------------------
    // Q-2: Multi-level rotation (.jsonl → .jsonl.1 → .jsonl.2)
    // -------------------------------------------------------------------------

    @Test
    void rotation_exceedsMaxSize_createsLevel1Backup() throws Exception {
        Path auditFile = tempDir.resolve(".ollamassist/agent_audit.jsonl");
        Files.createDirectories(auditFile.getParent());

        // Pre-seed a file that is already over the 5 MB limit (1 byte of padding)
        // by using a fresh logger that will trigger rotation on the next record.
        byte[] oversized = new byte[AuditLogger.MAX_FILE_SIZE_BYTES + 1];
        Files.write(auditFile, oversized);

        logger.record("cid-test", "FILE_READ", "trigger rotation", Set.of("path"), true, null);
        logger.dispose();

        Path level1 = tempDir.resolve(".ollamassist/agent_audit.jsonl.1");
        assertThat(level1).exists();
        // The oversized content must have been moved to level 1
        assertThat(Files.size(level1)).isGreaterThan(AuditLogger.MAX_FILE_SIZE_BYTES);
        // The current file must be fresh (just the new record)
        String currentContent = Files.readString(auditFile);
        assertThat(currentContent).contains("trigger rotation");
    }

    @Test
    void rotation_twoOverflows_cascadesToLevel2() throws Exception {
        Path auditFile = tempDir.resolve(".ollamassist/agent_audit.jsonl");
        Path level1    = tempDir.resolve(".ollamassist/agent_audit.jsonl.1");
        Path level2    = tempDir.resolve(".ollamassist/agent_audit.jsonl.2");
        Files.createDirectories(auditFile.getParent());

        byte[] oversized = new byte[AuditLogger.MAX_FILE_SIZE_BYTES + 1];

        // First overflow: seeds level1
        Files.write(auditFile, oversized);
        logger.record("cid-1", "FILE_READ", "first overflow", Set.of("path"), true, null);
        logger.dispose();
        assertThat(level1).exists();

        // Second overflow: seeds level2, level1 shifts
        Project mockProject2 = mock(Project.class);
        when(mockProject2.getBasePath()).thenReturn(tempDir.toString());
        AuditLogger logger2 = new AuditLogger(mockProject2);

        // Bloat the current file again to trigger second rotation
        Files.write(auditFile, oversized);
        logger2.record("cid-2", "FILE_READ", "second overflow", Set.of("path"), true, null);
        logger2.dispose();

        assertThat(level2).exists();
        assertThat(level1).exists();
        assertThat(auditFile).exists();
        assertThat(Files.readString(auditFile)).contains("second overflow");
    }

    // -------------------------------------------------------------------------
    // Dispose is idempotent
    // -------------------------------------------------------------------------

    @Test
    void dispose_calledTwice_doesNotThrow() {
        logger.record("cid-test", "\1", "\2", Set.of(), true, null);
        logger.dispose();
        logger.dispose(); // must not throw
    }
}
