package fr.baretto.ollamassist.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.plan.AgentPlan;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * Serialises in-progress agent execution state to disk so that a crashed or
 * interrupted execution can be resumed without replanning from scratch (A-3).
 *
 * <p>Checkpoint lifecycle:
 * <ol>
 *   <li>Call {@link #save} after each phase completes successfully.</li>
 *   <li>On startup / retry, call {@link #load} to restore the saved state.</li>
 *   <li>Call {@link #clear} when the execution finishes (COMPLETED or ABORTED).</li>
 * </ol>
 *
 * <p>Stored at {@code .ollamassist/checkpoint.json}. The file is intentionally
 * <em>not</em> HMAC-signed — a stale or corrupt checkpoint degrades gracefully
 * to "no checkpoint" rather than silently running a modified plan.
 */
@Service(Service.Level.PROJECT)
@Slf4j
public final class AgentCheckpointService {

    private static final String CHECKPOINT_FILE = ".ollamassist/checkpoint.json";

    private final Path checkpointPath;
    private final ObjectMapper mapper;

    public AgentCheckpointService(@NotNull Project project) {
        String base = project.getBasePath();
        this.checkpointPath = base != null ? Paths.get(base, CHECKPOINT_FILE) : null;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Saves the current execution state. Call after each successful phase.
     *
     * @param correlationId  execution correlation ID
     * @param goal           original user goal
     * @param plan           the full (possibly adapted) plan
     * @param nextPhaseIndex index of the next phase to execute (0-based)
     */
    public synchronized void save(String correlationId, String goal,
                                  AgentPlan plan, int nextPhaseIndex) {
        if (checkpointPath == null) return;
        try {
            Files.createDirectories(checkpointPath.getParent());
            CheckpointState state = new CheckpointState(
                    correlationId, goal, plan, nextPhaseIndex, Instant.now().toString());
            Files.writeString(checkpointPath, mapper.writeValueAsString(state), StandardCharsets.UTF_8);
            log.debug("AgentCheckpointService: saved checkpoint (phase {}/{})",
                    nextPhaseIndex, plan.getPhases().size());
        } catch (Exception e) {
            log.warn("AgentCheckpointService: failed to save checkpoint: {}", e.getMessage());
        }
    }

    /**
     * Loads the last saved checkpoint, or {@code null} if none exists or it is unreadable.
     */
    @Nullable
    public synchronized CheckpointState load() {
        if (checkpointPath == null || !Files.exists(checkpointPath)) return null;
        try {
            String json = Files.readString(checkpointPath, StandardCharsets.UTF_8);
            return mapper.readValue(json, CheckpointState.class);
        } catch (Exception e) {
            log.warn("AgentCheckpointService: failed to load checkpoint (will ignore): {}", e.getMessage());
            return null;
        }
    }

    /**
     * Deletes the checkpoint file. Must be called when execution finishes.
     */
    public synchronized void clear() {
        if (checkpointPath == null) return;
        try {
            Files.deleteIfExists(checkpointPath);
        } catch (Exception e) {
            log.warn("AgentCheckpointService: failed to clear checkpoint: {}", e.getMessage());
        }
    }

    /** Returns {@code true} if a checkpoint file currently exists. */
    public boolean hasCheckpoint() {
        return checkpointPath != null && Files.exists(checkpointPath);
    }

    // -------------------------------------------------------------------------
    // Value type
    // -------------------------------------------------------------------------

    /**
     * Immutable snapshot of agent execution state at the end of a completed phase.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckpointState(
            @com.fasterxml.jackson.annotation.JsonProperty("correlationId") String correlationId,
            @com.fasterxml.jackson.annotation.JsonProperty("goal")          String goal,
            @com.fasterxml.jackson.annotation.JsonProperty("plan")          AgentPlan plan,
            @com.fasterxml.jackson.annotation.JsonProperty("nextPhaseIndex") int nextPhaseIndex,
            @com.fasterxml.jackson.annotation.JsonProperty("savedAt")       String savedAt
    ) {
        public CheckpointState() {
            this(null, null, null, 0, null);
        }
    }
}
