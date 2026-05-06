package fr.baretto.ollamassist.agent.platform;

import fr.baretto.ollamassist.agent.tools.ToolResult;
import fr.baretto.ollamassist.agent.tools.files.EditFileTool;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Platform tests for {@link EditFileTool} using a real IntelliJ project context.
 *
 * <p>Covers: successful edit, user rejection, path traversal, missing file,
 * search string not found.
 */
public class EditFileToolPlatformTest extends AgentPlatformTestBase {

    private EditFileTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new EditFileTool(getProject());
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    public void testEditFile_replacesFirstOccurrence() throws Exception {
        myFixture.addFileToProject("src/Foo.java",
                "public class Foo { void oldMethod() {} void oldMethod2() {} }");

        ToolResult result = tool.execute(Map.of(
                "path", "src/Foo.java",
                "search", "void oldMethod()",
                "replace", "void newMethod()",
                "replaceAll", false
        ));

        assertThat(result.isSuccess()).isTrue();
        String content = readProjectFile("src/Foo.java");
        assertThat(content)
                .contains("void newMethod()")
                .contains("void oldMethod2()"); // second occurrence untouched
    }

    public void testEditFile_replaceAll_replacesEveryOccurrence() throws Exception {
        myFixture.addFileToProject("src/Bar.java",
                "// TODO fix\npublic class Bar { // TODO fix\n}");

        ToolResult result = tool.execute(Map.of(
                "path", "src/Bar.java",
                "search", "// TODO fix",
                "replace", "// DONE",
                "replaceAll", true
        ));

        assertThat(result.isSuccess()).isTrue();
        assertThat(readProjectFile("src/Bar.java"))
                .doesNotContain("TODO")
                .contains("// DONE");
    }

    // -------------------------------------------------------------------------
    // Approval flow
    // -------------------------------------------------------------------------

    public void testEditFile_userApproves_fileModified() throws Exception {
        myFixture.addFileToProject("src/Approved.java", "original content");
        setAutoApprove(true);

        ToolResult result = tool.execute(Map.of(
                "path", "src/Approved.java",
                "search", "original",
                "replace", "modified",
                "replaceAll", false
        ));

        assertThat(result.isSuccess()).isTrue();
        assertThat(readProjectFile("src/Approved.java")).contains("modified");
    }

    public void testEditFile_userRejects_fileUnchanged() throws Exception {
        myFixture.addFileToProject("src/Rejected.java", "original content");
        setAutoApprove(false);

        ToolResult result = tool.execute(Map.of(
                "path", "src/Rejected.java",
                "search", "original",
                "replace", "modified",
                "replaceAll", false
        ));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("reject");
        assertThat(readProjectFile("src/Rejected.java")).isEqualTo("original content");
    }

    // -------------------------------------------------------------------------
    // Security
    // -------------------------------------------------------------------------

    public void testEditFile_pathTraversal_blocked() {
        ToolResult result = tool.execute(Map.of(
                "path", "../../etc/passwd",
                "search", "root",
                "replace", "hacked",
                "replaceAll", false
        ));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage().toLowerCase())
                .containsAnyOf("escapes", "traversal", "confined");
    }

    public void testEditFile_nullPath_returnsFailure() {
        ToolResult result = tool.execute(Map.of(
                "search", "x", "replace", "y", "replaceAll", false
        ));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("path");
    }

    public void testEditFile_fileNotFound_returnsFailure() {
        ToolResult result = tool.execute(Map.of(
                "path", "src/DoesNotExist.java",
                "search", "x", "replace", "y", "replaceAll", false
        ));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("not found");
    }

    public void testEditFile_searchStringNotFound_includesFileContentInError() throws Exception {
        myFixture.addFileToProject("src/Baz.java", "actual content here");

        ToolResult result = tool.execute(Map.of(
                "path", "src/Baz.java",
                "search", "this text does not exist in the file",
                "replace", "anything",
                "replaceAll", false
        ));

        assertThat(result.isSuccess()).isFalse();
        // Error must include the actual file content so the LLM can self-correct
        assertThat(result.getErrorMessage()).contains("actual content here");
    }
}
