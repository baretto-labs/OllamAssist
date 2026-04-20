package fr.baretto.ollamassist.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * Persists a rolling window of agent execution records to
 * {@code .ollamassist/agent_memory.json} inside the project root.
 *
 * <p>Used to inject recent execution context into the PlannerAgent prompt
 * so that the planner can avoid redundant work and learn from past failures.
 *
 * <h2>Integrity protection (S2)</h2>
 * <p>The memory file is signed with HMAC-SHA256 using a per-project key stored in
 * {@code .ollamassist/memory.key}. On load the signature is verified: a mismatch
 * triggers a warning and an empty-memory fallback to prevent memory poisoning
 * (MITRE ATLAS — indirect prompt injection via persisted context).
 *
 * <p>If the key file does not exist it is generated once. The key file and memory
 * file should be added to {@code .gitignore} to avoid sharing execution history.
 */
@Service(Service.Level.PROJECT)
@Slf4j
public final class AgentMemoryService {

    static final int MAX_RECORDS = 15;
    private static final String MEMORY_FILE = ".ollamassist/agent_memory.json";
    private static final String HMAC_FILE   = ".ollamassist/agent_memory.json.hmac";
    private static final String KEY_FILE    = ".ollamassist/memory.key";
    private static final String HMAC_ALGO   = "HmacSHA256";

    private final Path memoryPath;
    private final Path hmacPath;
    private final Path keyPath;
    private final ObjectMapper mapper;

    public AgentMemoryService(@NotNull Project project) {
        String base = project.getBasePath();
        if (base != null) {
            this.memoryPath = Paths.get(base, MEMORY_FILE);
            this.hmacPath   = Paths.get(base, HMAC_FILE);
            this.keyPath    = Paths.get(base, KEY_FILE);
        } else {
            this.memoryPath = null;
            this.hmacPath   = null;
            this.keyPath    = null;
        }
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Records a completed or aborted execution.
     *
     * @param correlationId execution correlation ID linking this record to audit log entries
     * @param goal          the user's original goal
     * @param status        {@code "COMPLETED"} or {@code "ABORTED"}
     * @param reason        brief outcome summary (Critic reasoning or error message)
     */
    public synchronized void record(String correlationId, String goal, String status, String reason) {
        if (memoryPath == null) return;
        List<ExecutionRecord> records = load();
        records.add(0, new ExecutionRecord(Instant.now().toString(), correlationId, goal, status, truncate(reason, 200)));
        if (records.size() > MAX_RECORDS) {
            records = records.subList(0, MAX_RECORDS);
        }
        save(records);
    }

    /**
     * Deletes the agent memory file and its HMAC signature.
     * Safe to call at any time; idempotent if no memory exists.
     */
    public synchronized void clearMemory() {
        if (memoryPath == null) return;
        try {
            Files.deleteIfExists(memoryPath);
            Files.deleteIfExists(hmacPath);
            log.info("Agent memory cleared");
        } catch (IOException e) {
            log.warn("Failed to clear agent memory: {}", e.getMessage());
        }
    }

    /**
     * Returns a human-readable summary of recent executions suitable for injection
     * into a planner prompt (max ~600 chars).
     *
     * <p>The returned string is prefixed so the planner can identify it as
     * contextual history (not trusted instructions).
     *
     * @return empty string if no history exists
     */
    public String recentContextSummary() {
        if (memoryPath == null) return "";
        List<ExecutionRecord> records = load();
        if (records.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("Recent agent executions (most recent first):\n");
        for (ExecutionRecord r : records) {
            sb.append("- [").append(r.status).append("] ")
                    .append(truncate(r.goal, 80))
                    .append(" (").append(r.timestamp.substring(0, 10)).append(")\n");
            if (r.reason != null && !r.reason.isBlank()) {
                sb.append("  Outcome: ").append(r.reason).append("\n");
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Persistence + HMAC signing
    // -------------------------------------------------------------------------

    private List<ExecutionRecord> load() {
        if (memoryPath == null || !Files.exists(memoryPath)) return new ArrayList<>();
        try {
            byte[] content = Files.readAllBytes(memoryPath);
            if (!verifyHmac(content)) {
                log.warn("Agent memory HMAC mismatch — memory file may have been tampered with. Starting with empty history.");
                return new ArrayList<>();
            }
            ExecutionRecord[] records = mapper.readValue(content, ExecutionRecord[].class);
            List<ExecutionRecord> list = new ArrayList<>();
            Collections.addAll(list, records);
            return list;
        } catch (IOException e) {
            log.debug("Could not load agent memory (will start fresh): {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void save(List<ExecutionRecord> records) {
        if (memoryPath == null) return;
        try {
            Files.createDirectories(memoryPath.getParent());
            byte[] content = mapper.writeValueAsBytes(records);
            Files.write(memoryPath, content);
            writeHmac(content);
        } catch (IOException e) {
            log.warn("Could not persist agent memory: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // HMAC helpers
    // -------------------------------------------------------------------------

    private byte[] loadOrGenerateKey() {
        if (keyPath == null) return null;
        try {
            if (Files.exists(keyPath)) {
                String hex = Files.readString(keyPath, StandardCharsets.UTF_8).strip();
                return HexFormat.of().parseHex(hex);
            }
            // Generate a new 256-bit key
            byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            Files.createDirectories(keyPath.getParent());
            Files.writeString(keyPath, HexFormat.of().formatHex(key), StandardCharsets.UTF_8);
            return key;
        } catch (Exception e) {
            log.debug("Could not load/generate memory HMAC key: {}", e.getMessage());
            return null;
        }
    }

    private boolean verifyHmac(byte[] content) {
        if (hmacPath == null) return true; // no path configured — in-memory only, no verification needed
        if (!Files.exists(hmacPath)) {
            // HMAC file absent while memory file exists — treat as tampered (fail-closed)
            log.warn("Agent memory HMAC file missing — rejecting memory file to prevent injection.");
            return false;
        }
        try {
            byte[] key = loadOrGenerateKey();
            if (key == null) {
                // Cannot read or generate key — fail-closed
                log.warn("Agent memory HMAC key unavailable — rejecting memory file.");
                return false;
            }
            String storedHex = Files.readString(hmacPath, StandardCharsets.UTF_8).strip();
            String computedHex = computeHmac(content, key);
            return computedHex.equals(storedHex);
        } catch (Exception e) {
            log.warn("HMAC verification error — rejecting memory file: {}", e.getMessage());
            return false; // fail-closed in degraded key state
        }
    }

    private void writeHmac(byte[] content) {
        if (hmacPath == null) return;
        try {
            byte[] key = loadOrGenerateKey();
            if (key == null) return;
            String hex = computeHmac(content, key);
            Files.writeString(hmacPath, hex, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("Could not write HMAC for agent memory: {}", e.getMessage());
        }
    }

    private static String computeHmac(byte[] data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(key, HMAC_ALGO));
        return HexFormat.of().formatHex(mac.doFinal(data));
    }

    // -------------------------------------------------------------------------

    /**
     * Truncates {@code s} to at most {@code maxChars} Unicode code points.
     *
     * <p>Using {@code codePointCount} / {@code offsetByCodePoints} instead of
     * {@code String.length()} / {@code substring} prevents splitting a UTF-16
     * surrogate pair at the boundary (Q-3).
     */
    static String truncate(String s, int maxChars) {
        if (s == null) return "";
        if (s.codePointCount(0, s.length()) <= maxChars) return s;
        int byteOffset = s.offsetByCodePoints(0, maxChars);
        return s.substring(0, byteOffset) + "…";
    }

    // -------------------------------------------------------------------------
    // Record type
    // -------------------------------------------------------------------------

    /**
     * Immutable record of a single agent execution stored in the memory file.
     *
     * <p>Uses a Java record for immutability. Jackson serialises records natively
     * (since Jackson 2.12). The {@code @JsonIgnoreProperties} annotation ensures that
     * any extra fields added in a future version of the memory format are silently ignored
     * rather than causing a deserialisation failure.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExecutionRecord(
            @com.fasterxml.jackson.annotation.JsonProperty("timestamp")  String timestamp,
            @com.fasterxml.jackson.annotation.JsonProperty("correlationId") String correlationId,
            @com.fasterxml.jackson.annotation.JsonProperty("goal")       String goal,
            @com.fasterxml.jackson.annotation.JsonProperty("status")     String status,
            @com.fasterxml.jackson.annotation.JsonProperty("reason")     String reason
    ) {
        /** No-arg constructor required by Jackson for deserialisation. */
        public ExecutionRecord() {
            this(null, null, null, null, null);
        }
    }
}
