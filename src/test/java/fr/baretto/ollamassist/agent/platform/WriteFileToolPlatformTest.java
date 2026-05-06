package fr.baretto.ollamassist.agent.platform;

import fr.baretto.ollamassist.agent.tools.ToolResult;
import fr.baretto.ollamassist.agent.tools.files.WriteFileTool;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Platform tests for {@link WriteFileTool} using a real IntelliJ project context.
 */
public class WriteFileToolPlatformTest extends AgentPlatformTestBase {

    private WriteFileTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new WriteFileTool(getProject());
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    public void testWriteFile_createsFileWithContent() throws Exception {
        ToolResult result = tool.execute(Map.of(
                "path", "src/main/java/com/example/NewService.java",
                "content", "package com.example;\npublic class NewService {}\n"
        ));

        assertThat(result.isSuccess()).isTrue();
        assertThat(readProjectFile("src/main/java/com/example/NewService.java"))
                .contains("public class NewService");
    }

    public void testWriteFile_createsParentDirectories() throws Exception {
        ToolResult result = tool.execute(Map.of(
                "path", "src/main/java/deep/nested/pkg/MyClass.java",
                "content", "package deep.nested.pkg;\npublic class MyClass {}"
        ));

        assertThat(result.isSuccess()).isTrue();
        assertThat(readProjectFile("src/main/java/deep/nested/pkg/MyClass.java"))
                .contains("MyClass");
    }

    public void testWriteFile_userRejects_fileNotCreated() {
        setAutoApprove(false);

        ToolResult result = tool.execute(Map.of(
                "path", "src/ShouldNotExist.java",
                "content", "public class ShouldNotExist {}"
        ));

        assertThat(result.isSuccess()).isFalse();
        // File must NOT exist after rejection
        var file = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(getProject().getBasePath() + "/src/ShouldNotExist.java");
        assertNull("File should not have been created after rejection", file);
    }

    // -------------------------------------------------------------------------
    // Guard: file already exists
    // -------------------------------------------------------------------------

    public void testWriteFile_fileAlreadyExists_returnsFailure() throws Exception {
        myFixture.addFileToProject("src/Existing.java", "existing content");

        ToolResult result = tool.execute(Map.of(
                "path", "src/Existing.java",
                "content", "new content"
        ));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("exists");
        // Original content must be intact
        assertThat(readProjectFile("src/Existing.java")).isEqualTo("existing content");
    }

    // -------------------------------------------------------------------------
    // Security
    // -------------------------------------------------------------------------

    public void testWriteFile_pathTraversal_blocked() {
        ToolResult result = tool.execute(Map.of(
                "path", "../outside-project.txt",
                "content", "malicious"
        ));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage().toLowerCase())
                .containsAnyOf("escapes", "traversal", "confined");
    }

    public void testWriteFile_nullPath_returnsFailure() {
        ToolResult result = tool.execute(Map.of("content", "some content"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("path");
    }
}
