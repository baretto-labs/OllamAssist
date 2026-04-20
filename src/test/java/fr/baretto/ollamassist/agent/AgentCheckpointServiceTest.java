package fr.baretto.ollamassist.agent;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.plan.AgentPlan;
import fr.baretto.ollamassist.agent.plan.Phase;
import fr.baretto.ollamassist.agent.plan.Step;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the plan checkpoint persistence service (A-3).
 */
class AgentCheckpointServiceTest {

    @TempDir
    Path tempDir;

    private AgentCheckpointService service;

    private static AgentPlan samplePlan() {
        Step s1 = new Step("FILE_READ", "Read Foo.java", Map.of("path", "Foo.java"));
        Step s2 = new Step("FILE_EDIT", "Edit Foo.java", Map.of("path", "Foo.java", "search", "old", "replace", "new"));
        Phase p1 = new Phase("Read phase", List.of(s1));
        Phase p2 = new Phase("Edit phase", List.of(s2));
        return new AgentPlan("Refactor Foo", "locate then edit", List.of(p1, p2));
    }

    @BeforeEach
    void setUp() {
        Project mockProject = mock(Project.class);
        when(mockProject.getBasePath()).thenReturn(tempDir.toString());
        service = new AgentCheckpointService(mockProject);
    }

    // -------------------------------------------------------------------------
    // Happy path: save, load, clear
    // -------------------------------------------------------------------------

    @Test
    void save_thenLoad_returnsMatchingState() {
        AgentPlan plan = samplePlan();
        service.save("cid-abc", "Refactor Foo", plan, 1);

        AgentCheckpointService.CheckpointState loaded = service.load();

        assertThat(loaded).isNotNull();
        assertThat(loaded.correlationId()).isEqualTo("cid-abc");
        assertThat(loaded.goal()).isEqualTo("Refactor Foo");
        assertThat(loaded.nextPhaseIndex()).isEqualTo(1);
        assertThat(loaded.savedAt()).isNotBlank();
    }

    @Test
    void hasCheckpoint_trueAfterSave() {
        assertThat(service.hasCheckpoint()).isFalse();
        service.save("cid-1", "goal", samplePlan(), 0);
        assertThat(service.hasCheckpoint()).isTrue();
    }

    @Test
    void clear_removesCheckpointFile() {
        service.save("cid-1", "goal", samplePlan(), 0);
        assertThat(service.hasCheckpoint()).isTrue();

        service.clear();

        assertThat(service.hasCheckpoint()).isFalse();
        assertThat(service.load()).isNull();
    }

    @Test
    void clear_calledTwice_doesNotThrow() {
        service.save("cid-1", "goal", samplePlan(), 0);
        service.clear();
        service.clear(); // must not throw
    }

    // -------------------------------------------------------------------------
    // Graceful degradation — no checkpoint / null project
    // -------------------------------------------------------------------------

    @Test
    void load_noCheckpointExists_returnsNull() {
        assertThat(service.load()).isNull();
    }

    @Test
    void nullBasePath_saveDoesNotThrow() {
        Project nullProject = mock(Project.class);
        when(nullProject.getBasePath()).thenReturn(null);
        AgentCheckpointService noPath = new AgentCheckpointService(nullProject);

        noPath.save("cid-1", "goal", samplePlan(), 0); // must not throw
        assertThat(noPath.load()).isNull();
        assertThat(noPath.hasCheckpoint()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Save overwrites existing checkpoint
    // -------------------------------------------------------------------------

    @Test
    void save_twiceSameExecution_latestCheckpointWins() {
        AgentPlan plan = samplePlan();
        service.save("cid-1", "goal", plan, 0);
        service.save("cid-1", "goal", plan, 1); // phase 1 completed

        AgentCheckpointService.CheckpointState loaded = service.load();
        assertThat(loaded).isNotNull();
        assertThat(loaded.nextPhaseIndex()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Corrupt checkpoint file → graceful null
    // -------------------------------------------------------------------------

    @Test
    void corruptCheckpointFile_loadReturnsNull() throws Exception {
        service.save("cid-1", "goal", samplePlan(), 0);
        // Overwrite with garbage
        Path checkpoint = tempDir.resolve(".ollamassist/checkpoint.json");
        java.nio.file.Files.writeString(checkpoint, "NOT_JSON{{{");

        assertThat(service.load()).isNull();
    }
}
