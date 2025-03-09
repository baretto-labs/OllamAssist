package fr.baretto.ollamassist.chat.rag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndexRegistryTest {

    private IndexRegistry indexRegistry;
    private Path tempProjectsFile;
    private String originalUserHome;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        originalUserHome = System.getProperty("user.home");

        System.setProperty("user.home", tempDir.toString());

        indexRegistry = new IndexRegistry();
        tempProjectsFile = Path.of(IndexRegistry.OLLAMASSIST_DIR, "indexed_projects.txt");
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.home", originalUserHome);
    }

    @Test
    void isIndexed_shouldReturnFalse_whenNoProjectExists() {
        assertFalse(indexRegistry.isIndexed("unknown_project"));
    }

    @Test
    void isIndexed_shouldReturnFalse_whenProjectHasNoDate() throws IOException {
        Files.write(tempProjectsFile, List.of("no_date_project"), IndexRegistry.CHARSET);

        assertFalse(indexRegistry.isIndexed("no_date_project"));
    }

    @Test
    void isIndexed_shouldReturnFalse_whenDateIsOlderThan7Days() throws IOException {
        String oldProject = "old_project," + LocalDate.now().minusDays(10);
        Files.write(tempProjectsFile, List.of(oldProject), IndexRegistry.CHARSET);

        assertFalse(indexRegistry.isIndexed("old_project"));
    }

    @Test
    void isIndexed_shouldReturnTrue_whenDateIsRecent() throws IOException {
        String recentProject = "recent_project," + LocalDate.now().minusDays(1);
        Files.write(tempProjectsFile, List.of(recentProject), IndexRegistry.CHARSET);

        assertTrue(indexRegistry.isIndexed("recent_project"));
    }

    @Test
    void markAsIndexed_shouldUpdateDate() {
        String projectId = "new_project";

        indexRegistry.markAsIndexed(projectId);

        assertTrue(indexRegistry.isIndexed(projectId));
        assertEquals(
                LocalDate.now(),
                indexRegistry.getIndexedProjects().get(projectId)
        );
    }

    @Test
    void shouldPreventConcurrentIndexation() {
        String projectId = "concurrent_project";

        indexRegistry.markAsCurrentIndexation(projectId);
        assertTrue(indexRegistry.indexationIsProcessing(projectId));

        assertThrows(IllegalStateException.class, () -> {
            if(indexRegistry.indexationIsProcessing(projectId)) {
                throw new IllegalStateException("Indexation déjà en cours");
            }
            indexRegistry.markAsCurrentIndexation(projectId);
        });
    }

    @Test
    void shouldAllowNewIndexationAfterCompletion() {
        String projectId = "sequential_project";

        indexRegistry.markAsCurrentIndexation(projectId);
        assertTrue(indexRegistry.isIndexed(projectId));

        indexRegistry.markAsIndexed(projectId);
        indexRegistry.removeFromCurrentIndexation(projectId);

        assertFalse(indexRegistry.indexationIsProcessing(projectId));
        assertDoesNotThrow(() -> indexRegistry.markAsCurrentIndexation(projectId));
    }

    @Test
    void shouldHandleMultipleProjectsSimultaneously() {
        String project1 = "project_1";
        String project2 = "project_2";

        indexRegistry.markAsCurrentIndexation(project1);
        indexRegistry.markAsCurrentIndexation(project2);

        assertAll(
                () -> assertTrue(indexRegistry.indexationIsProcessing(project1)),
                () -> assertTrue(indexRegistry.indexationIsProcessing(project2)),
                () -> assertFalse(indexRegistry.indexationIsProcessing("unknown_project"))
        );
    }

    @Test
    void shouldMaintainConsistencyAfterFailure() {
        String projectId = "failed_project";

        indexRegistry.markAsCurrentIndexation(projectId);
        assertTrue(indexRegistry.isIndexed(projectId));

        indexRegistry.removeFromCurrentIndexation(projectId);

        assertFalse(indexRegistry.isIndexed(projectId));
    }

    @Test
    void shouldHandleCorruptedFileAndReindex() throws IOException {
        List<String> corruptedContent = List.of(
                "valid_project," + LocalDate.now().minusDays(1),
                "missing_date_project",
                "invalid_date_project,2023-99-99",
                "empty_project,",
                ",2023-10-10"
        );

        Files.write(tempProjectsFile, corruptedContent, IndexRegistry.CHARSET);

        IndexRegistry registry = new IndexRegistry();

        assertAll(
                () -> assertTrue(registry.isIndexed("valid_project")),
                () -> assertFalse(registry.isIndexed("missing_date_project")),
                () -> assertFalse(registry.isIndexed("invalid_date_project")),
                () -> assertFalse(registry.isIndexed("empty_project")),
                () -> assertFalse(registry.isIndexed(""))
        );

        registry.markAsIndexed("missing_date_project");
        registry.markAsIndexed("invalid_date_project");
        registry.markAsIndexed("empty_project");

        List<String> updatedLines = Files.readAllLines(tempProjectsFile, IndexRegistry.CHARSET);
        LocalDate today = LocalDate.now();

        assertAll(
                () -> assertEquals(4, updatedLines.size(), "file should contains 4"),

                () -> assertTrue(updatedLines.contains("valid_project," + today.minusDays(1))),

                () -> assertTrue(updatedLines.stream()
                        .anyMatch(line -> line.startsWith("missing_date_project," + today))),

                () -> assertTrue(updatedLines.stream()
                        .anyMatch(line -> line.startsWith("invalid_date_project," + today))),

                () -> assertTrue(updatedLines.stream()
                        .anyMatch(line -> line.startsWith("empty_project," + today))),

                () -> assertFalse(updatedLines.stream()
                        .anyMatch(line -> line.startsWith(",")), "empty line should be deleted")
        );

        updatedLines.forEach(line -> {
            String[] parts = line.split(",", 2);
            assertDoesNotThrow(
                    () -> LocalDate.parse(parts[1]),
                    "Invalid date in line: " + line
            );
        });
    }
}