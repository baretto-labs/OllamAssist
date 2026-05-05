package fr.baretto.ollamassist.agent.tools.files;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.tools.ToolResult;
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

class ListDirectoryToolTest {

    @TempDir
    Path tempDir;

    private ListDirectoryTool tool;

    @BeforeEach
    void setUp() {
        Project project = mock(Project.class);
        when(project.getBasePath()).thenReturn(tempDir.toString());
        tool = new ListDirectoryTool(project);
    }

    @Test
    void toolId_isListDirectory() {
        assertThat(tool.toolId()).isEqualTo("LIST_DIRECTORY");
    }

    @Test
    void listRoot_returnsFilesAndDirs() throws IOException {
        Files.createDirectory(tempDir.resolve("src"));
        Files.createFile(tempDir.resolve("README.md"));

        ToolResult result = tool.execute(Map.of("path", ""));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("[DIR]  src");
        assertThat(result.getOutput()).contains("[FILE] README.md");
    }

    @Test
    void listSubDir_returnsContents() throws IOException {
        Path sub = Files.createDirectory(tempDir.resolve("sub"));
        Files.createFile(sub.resolve("Foo.java"));

        ToolResult result = tool.execute(Map.of("path", "sub"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("[FILE] Foo.java");
    }

    @Test
    void listNonExistentDir_returnsFailure() {
        ToolResult result = tool.execute(Map.of("path", "does-not-exist"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("not found");
    }

    @Test
    void listFile_returnsFailure() throws IOException {
        Files.createFile(tempDir.resolve("file.txt"));
        ToolResult result = tool.execute(Map.of("path", "file.txt"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("not a directory");
    }

    @Test
    void pathTraversal_isBlocked() {
        ToolResult result = tool.execute(Map.of("path", "../../etc"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Security");
    }

    @Test
    void buildDirs_areExcluded() throws IOException {
        Files.createDirectory(tempDir.resolve("build"));
        Files.createDirectory(tempDir.resolve("src"));

        ToolResult result = tool.execute(Map.of("path", ""));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).doesNotContain("build");
        assertThat(result.getOutput()).contains("[DIR]  src");
    }

    @Test
    void emptyDirectory_returnsEmptyMessage() throws IOException {
        Path empty = Files.createDirectory(tempDir.resolve("empty"));
        ToolResult result = tool.execute(Map.of("path", "empty"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("empty directory");
    }
}
