package fr.baretto.ollamassist.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;

/**
 * Appends one JSONL record per tool invocation to {@code .ollamassist/agent_audit.jsonl}.
 *
 * <p>Each record captures: timestamp, toolId, step description, resolved params (keys only —
 * values are omitted to avoid logging secrets), and the outcome (success/failure).
 *
 * <p>The writer is kept open across calls (one open per service lifetime) and flushed after
 * each record. This avoids the overhead of opening and closing the file on every tool dispatch.
 *
 * <p>This log is written best-effort: failures to write are logged at WARN and never
 * propagate to the caller.
 */
@Service(Service.Level.PROJECT)
@Slf4j
public final class AuditLogger implements Disposable {

    private static final String AUDIT_FILE = ".ollamassist/agent_audit.jsonl";
    static final int MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB per file
    /** Number of backup files to keep (.jsonl.1, .jsonl.2, …). Total history = (levels+1) × 5 MB. */
    private static final int ROTATION_LEVELS = 2;

    private final Path auditPath;
    private final ObjectMapper mapper = new ObjectMapper();
    private BufferedWriter writer;

    public AuditLogger(@NotNull Project project) {
        String base = project.getBasePath();
        this.auditPath = base != null
                ? Paths.get(base, AUDIT_FILE)
                : null;
    }

    /**
     * Records a tool invocation outcome.
     *
     * @param correlationId execution correlation ID linking audit entries to a memory record
     * @param toolId        tool identifier (e.g. {@code FILE_READ})
     * @param description   human-readable step description
     * @param paramKeys     parameter names (values are not recorded)
     * @param success       whether the tool succeeded
     * @param errorSummary  first 200 chars of the error message, or null on success
     */
    public synchronized void record(String correlationId, String toolId, String description,
                                    Iterable<String> paramKeys, boolean success, String errorSummary) {
        if (auditPath == null) return;
        try {
            ensureParentDir();
            rotateIfNeeded();
            Map<String, Object> entry = Map.of(
                    "ts",      Instant.now().toString(),
                    "cid",     correlationId != null ? correlationId : "",
                    "tool",    toolId,
                    "step",    description != null ? description : "",
                    "params",  paramKeys,
                    "ok",      success,
                    "err",     errorSummary != null ? truncate(errorSummary, 200) : ""
            );
            String line = mapper.writeValueAsString(entry);
            ensureWriter();
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (Exception e) {
            log.warn("AuditLogger: failed to write audit record: {}", e.getMessage());
        }
    }

    @Override
    public synchronized void dispose() {
        closeWriter();
    }

    // -------------------------------------------------------------------------

    private void ensureWriter() throws IOException {
        if (writer == null) {
            writer = Files.newBufferedWriter(auditPath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    private void closeWriter() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                log.debug("AuditLogger: error closing writer: {}", e.getMessage());
            } finally {
                writer = null;
            }
        }
    }

    private void ensureParentDir() throws IOException {
        Path parent = auditPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    /**
     * Rotates the audit log when it exceeds {@value #MAX_FILE_SIZE_BYTES} bytes.
     *
     * <p>Uses a cascade strategy: the oldest backup is deleted, then each level is
     * shifted up by one, and the current file becomes {@code .jsonl.1} (Q-2):
     * <pre>
     *   .jsonl.2 (oldest) → deleted
     *   .jsonl.1           → renamed to .jsonl.2
     *   .jsonl             → renamed to .jsonl.1
     *   (new writes go to a fresh .jsonl)
     * </pre>
     * Total retained history: ({@value #ROTATION_LEVELS} + 1) × {@value #MAX_FILE_SIZE_BYTES} bytes.
     */
    private void rotateIfNeeded() {
        try {
            if (Files.exists(auditPath) && Files.size(auditPath) > MAX_FILE_SIZE_BYTES) {
                closeWriter(); // flush + close before any rename
                // Cascade from oldest to newest
                for (int level = ROTATION_LEVELS; level >= 1; level--) {
                    Path older = auditPath.resolveSibling("agent_audit.jsonl." + level);
                    Path newer = level == 1
                            ? auditPath
                            : auditPath.resolveSibling("agent_audit.jsonl." + (level - 1));
                    if (level == ROTATION_LEVELS) {
                        Files.deleteIfExists(older); // drop the oldest backup
                    }
                    if (Files.exists(newer)) {
                        Files.move(newer, older, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                // writer will be re-opened on next ensureWriter() call (fresh file)
            }
        } catch (Exception e) {
            log.debug("AuditLogger: rotation failed: {}", e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        // Keep the LAST max chars — error root causes are typically at the end of a message
        // (e.g. "java.io.IOException: ... Caused by: Permission denied: /path/to/file").
        // Head-truncation strips exactly those details.
        return "..." + s.substring(s.length() - max + 3);
    }
}
