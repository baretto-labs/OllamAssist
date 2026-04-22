package fr.baretto.ollamassist.agent;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import fr.baretto.ollamassist.agent.tools.files.FindFilesTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindFilesToolTest {

    @TempDir
    Path tempDir;

    private FindFilesTool tool;

    @BeforeEach
    void setUp() {
        Project mockProject = mock(Project.class);
        when(mockProject.getBasePath()).thenReturn(tempDir.toString());
        tool = new FindFilesTool(mockProject);
    }

    @Test
    void missingPattern_returnsFailure() {
        ToolResult result = tool.execute(Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("pattern");
    }

    @Test
    void blankPattern_returnsFailure() {
        ToolResult result = tool.execute(Map.of("pattern", "  "));

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void noMatchingFiles_returnsSuccessWithEmptyMessage() {
        ToolResult result = tool.execute(Map.of("pattern", "**/*.nonexistent"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("No files found");
    }

    @Test
    void findsJavaFiles() throws IOException {
        Files.writeString(tempDir.resolve("Foo.java"), "class Foo {}");
        Files.writeString(tempDir.resolve("Bar.java"), "class Bar {}");
        Files.writeString(tempDir.resolve("readme.txt"), "ignore");

        ToolResult result = tool.execute(Map.of("pattern", "**/*.java"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("Foo.java").contains("Bar.java");
        assertThat(result.getOutput()).doesNotContain("readme.txt");
    }

    @Test
    void findsFilesInSubdirectories() throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("src/main"));
        Files.writeString(sub.resolve("Main.java"), "class Main {}");

        ToolResult result = tool.execute(Map.of("pattern", "**/*.java"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("Main.java");
    }

    @Test
    void skipsBuildDirectory() throws IOException {
        Path build = Files.createDirectories(tempDir.resolve("build/classes"));
        Files.writeString(build.resolve("Compiled.java"), "// generated");
        Files.writeString(tempDir.resolve("Source.java"), "class Source {}");

        ToolResult result = tool.execute(Map.of("pattern", "**/*.java"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("Source.java");
        assertThat(result.getOutput()).doesNotContain("Compiled.java");
    }
}
