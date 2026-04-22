package fr.baretto.ollamassist.agent;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import fr.baretto.ollamassist.agent.tools.navigation.SearchCodeTool;
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

class SearchCodeToolTest {

    @TempDir
    Path tempDir;

    private SearchCodeTool tool;

    @BeforeEach
    void setUp() {
        Project mockProject = mock(Project.class);
        when(mockProject.getBasePath()).thenReturn(tempDir.toString());
        tool = new SearchCodeTool(mockProject);
    }

    @Test
    void missingQuery_returnsFailure() {
        ToolResult result = tool.execute(Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("query");
    }

    @Test
    void noMatches_returnsSuccessWithMessage() throws IOException {
        Files.writeString(tempDir.resolve("Foo.java"), "class Foo {}");

        ToolResult result = tool.execute(Map.of("query", "interface Bar"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("No matches");
    }

    @Test
    void findsMatchInJavaFile() throws IOException {
        Files.writeString(tempDir.resolve("Service.java"),
                "public interface UserService {\n    void save(User user);\n}");

        ToolResult result = tool.execute(Map.of("query", "UserService"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("Service.java");
        assertThat(result.getOutput()).contains("UserService");
    }

    @Test
    void includesLineNumber() throws IOException {
        Files.writeString(tempDir.resolve("Config.java"),
                "// line 1\n// line 2\npublic class Config {}");

        ToolResult result = tool.execute(Map.of("query", "Config"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains(":3");
    }

    @Test
    void includesContextLines() throws IOException {
        Files.writeString(tempDir.resolve("Foo.java"),
                "before1\nbefore2\nTARGET_LINE\nafter1\nafter2");

        ToolResult result = tool.execute(Map.of("query", "TARGET_LINE"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("before2");
        assertThat(result.getOutput()).contains("after1");
    }

    @Test
    void findsMatchesInSubdirectory() throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("src/main"));
        Files.writeString(sub.resolve("Main.java"), "public class Main { void run() {} }");

        ToolResult result = tool.execute(Map.of("query", "void run"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("Main.java");
    }

    @Test
    void skipsBuildDirectory() throws IOException {
        Path build = Files.createDirectories(tempDir.resolve("build/classes"));
        Files.writeString(build.resolve("Generated.java"), "// SEARCH_TARGET in build");
        Files.writeString(tempDir.resolve("Source.java"), "// source without target");

        ToolResult result = tool.execute(Map.of("query", "SEARCH_TARGET"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).doesNotContain("Generated.java");
    }
}
