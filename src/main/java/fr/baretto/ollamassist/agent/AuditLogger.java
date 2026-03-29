package fr.baretto.ollamassist.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
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
 * <p>This log is written best-effort: failures to write are logged at WARN and never
 * propagate to the caller.
 */
@Service(Service.Level.PROJECT)
@Slf4j
public final class AuditLogger {

    private static final String AUDIT_FILE = ".ollamassist/agent_audit.jsonl";
    private static final int MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB, then rotate

    private final Path auditPath;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuditLogger(@NotNull Project project) {
        String base = project.getBasePath();
        this.auditPath = base != null
                ? Paths.get(base, AUDIT_FILE)
                : null;
    }

    /**
     * Records a tool invocation outcome.
     *
     * @param toolId     tool identifier (e.g. {@code FILE_READ})
     * @param description human-readable step description
     * @param paramKeys  parameter names (values are not recorded)
     * @param success    whether the tool succeeded
     * @param errorSummary first 200 chars of the error message, or null on success
     */
    public void record(String toolId, String description,
                       Iterable<String> paramKeys, boolean success, String errorSummary) {
        if (auditPath == null) return;
        try {
            ensureParentDir();
            rotateIfNeeded();
            Map<String, Object> entry = Map.of(
                    "ts", Instant.now().toString(),
                    "tool", toolId,
                    "step", description != null ? description : "",
                    "params", paramKeys,
                    "ok", success,
                    "err", errorSummary != null ? truncate(errorSummary, 200) : ""
            );
            String line = mapper.writeValueAsString(entry);
            try (Writer w = Files.newBufferedWriter(auditPath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(line);
                w.write('\n');
            }
        } catch (Exception e) {
            log.warn("AuditLogger: failed to write audit record: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------

    private void ensureParentDir() throws IOException {
        Path parent = auditPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    private void rotateIfNeeded() {
        try {
            if (Files.exists(auditPath) && Files.size(auditPath) > MAX_FILE_SIZE_BYTES) {
                Path rotated = auditPath.resolveSibling("agent_audit.jsonl.1");
                Files.move(auditPath, rotated,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            log.debug("AuditLogger: rotation failed: {}", e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
