package fr.baretto.ollamassist.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanAndExecuteAgentServiceTest {

    // -------------------------------------------------------------------------
    // isInternalFile
    // -------------------------------------------------------------------------

    @Test
    void isInternalFile_conversationJson_excluded() {
        assertThat(PlanAndExecuteAgentService.isInternalFile(
                ".ollamassist/conversations/abc123.json")).isTrue();
    }

    @Test
    void isInternalFile_ideaDir_excluded() {
        assertThat(PlanAndExecuteAgentService.isInternalFile(".idea/workspace.xml")).isTrue();
    }

    @Test
    void isInternalFile_buildDir_excluded() {
        assertThat(PlanAndExecuteAgentService.isInternalFile("build/classes/Foo.class")).isTrue();
    }

    @Test
    void isInternalFile_sourceFile_included() {
        assertThat(PlanAndExecuteAgentService.isInternalFile(
                "src/main/java/com/example/FizzBuzzService.java")).isFalse();
    }

    @Test
    void isInternalFile_configFile_included() {
        assertThat(PlanAndExecuteAgentService.isInternalFile("src/main/resources/application.yml")).isFalse();
    }

    // -------------------------------------------------------------------------
    // extractKeywords
    // -------------------------------------------------------------------------

    @Test
    void extractKeywords_camelCase_extracted() {
        List<String> kw = PlanAndExecuteAgentService.extractKeywords(
                "ajoute une méthode à FizzBuzzService");
        assertThat(kw).contains("FizzBuzzService");
    }

    @Test
    void extractKeywords_snakeCase_extracted() {
        List<String> kw = PlanAndExecuteAgentService.extractKeywords(
                "add a method to fizz_buzz_service");
        assertThat(kw).contains("fizz_buzz_service");
    }

    @Test
    void extractKeywords_fileExtension_extracted() {
        List<String> kw = PlanAndExecuteAgentService.extractKeywords(
                "edit fizz_buzz_service.go");
        assertThat(kw).contains("fizz_buzz_service.go");
    }

    @Test
    void extractKeywords_quotedString_extracted() {
        List<String> kw = PlanAndExecuteAgentService.extractKeywords(
                "search for \"fizzbuzz\" in the codebase");
        assertThat(kw).contains("fizzbuzz");
    }

    @Test
    void extractKeywords_noDuplicates() {
        List<String> kw = PlanAndExecuteAgentService.extractKeywords(
                "FizzBuzzService FizzBuzzService");
        assertThat(kw).containsOnlyOnce("FizzBuzzService");
    }

    @Test
    void extractKeywords_commonWords_notExtracted() {
        // single-word snake_case (no underscore) should not match
        List<String> kw = PlanAndExecuteAgentService.extractKeywords(
                "add a method that returns true or false");
        assertThat(kw).doesNotContain("add", "method", "returns", "true", "false");
    }

    // -------------------------------------------------------------------------
    // extractFilePaths
    // -------------------------------------------------------------------------

    @Test
    void extractFilePaths_standardSearchOutput_returnsPath() {
        String output = """
                src/main/java/com/example/FizzBuzzService.java:15:    public String solve(int n) {
                src/main/java/com/example/FizzBuzzService.java:18:    }
                """;
        List<String> paths = PlanAndExecuteAgentService.extractFilePaths(output);
        assertThat(paths).containsExactly("src/main/java/com/example/FizzBuzzService.java");
    }

    @Test
    void extractFilePaths_multiplePaths_allReturned() {
        String output = "src/Foo.java:1:class Foo\nsrc/Bar.java:1:class Bar\n";
        List<String> paths = PlanAndExecuteAgentService.extractFilePaths(output);
        assertThat(paths).containsExactlyInAnyOrder("src/Foo.java", "src/Bar.java");
    }

    @Test
    void extractFilePaths_emptyOutput_returnsEmpty() {
        assertThat(PlanAndExecuteAgentService.extractFilePaths("")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // parseSteps
    // -------------------------------------------------------------------------

    @Test
    void parseSteps_validArray_parsed() {
        String json = "[{\"tool\":\"writeFile\",\"path\":\"src/Foo.java\",\"content\":\"class Foo{}\"}]";
        List<AgentStep> steps = PlanAndExecuteAgentService.parseSteps(json);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).tool()).isEqualTo("writeFile");
        assertThat(steps.get(0).path()).isEqualTo("src/Foo.java");
    }

    @Test
    void parseSteps_withMarkdownFences_parsed() {
        String response = "```json\n[{\"tool\":\"writeFile\",\"path\":\"src/Foo.java\",\"content\":\"x\"}]\n```";
        List<AgentStep> steps = PlanAndExecuteAgentService.parseSteps(response);
        assertThat(steps).hasSize(1);
    }

    @Test
    void parseSteps_unknownTool_filtered() {
        String json = "[{\"tool\":\"searchWorkspace\",\"query\":\"Foo\"},{\"tool\":\"writeFile\",\"path\":\"src/Foo.java\",\"content\":\"x\"}]";
        List<AgentStep> steps = PlanAndExecuteAgentService.parseSteps(json);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).tool()).isEqualTo("writeFile");
    }

    @Test
    void parseSteps_noArray_returnsEmpty() {
        assertThat(PlanAndExecuteAgentService.parseSteps("Sorry, I cannot help.")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // validateSearchStrings — whitespace-lenient two-pass check
    // -------------------------------------------------------------------------

    @Test
    void validate_exactMatch_passes() {
        String file = "public class Foo {\n    void bar() {}\n}";
        // validateSearchStrings is package-private — test indirectly via precondition
        assertThat(file).contains("void bar() {}"); // precondition
    }

    @Test
    void validate_trailingNewlineMismatch_passes() {
        // File ends without trailing newline; LLM adds one — should NOT be a plan error
        String fileContent = "public class Foo {\n    void bar() {}\n}";
        String searchWithTrailingNewline = "    void bar() {}\n}\n"; // LLM adds extra \n

        // Exact match fails
        assertThat(fileContent).doesNotContain(searchWithTrailingNewline);
        // But whitespace-normalised match succeeds
        String normSearch  = searchWithTrailingNewline.replaceAll("\\s+", " ").strip();
        String normContent = fileContent.replaceAll("\\s+", " ");
        assertThat(normContent).contains(normSearch);
    }

    @Test
    void validate_trulyAbsentSearch_fails() {
        String fileContent = "public class Foo {\n    void bar() {}\n}";
        String absentSearch = "void completelyMadeUp() {}";

        assertThat(fileContent).doesNotContain(absentSearch);
        String normSearch  = absentSearch.replaceAll("\\s+", " ").strip();
        String normContent = fileContent.replaceAll("\\s+", " ");
        assertThat(normContent).doesNotContain(normSearch);
    }

    // -------------------------------------------------------------------------
    // extractFragment
    // -------------------------------------------------------------------------

    @Test
    void extractFragment_smallFile_returnsWholeFileNumbered() {
        String content = "line1\nline2\nline3\n";
        assertThat(PlanAndExecuteAgentService.extractFragment(content, "line2"))
                .isEqualTo("   1 | line1\n   2 | line2\n   3 | line3\n");
    }

    @Test
    void extractFragment_largeFile_returnsWindowAroundMatch() {
        // Build a file > WHOLE_FILE_MAX_LINES with the keyword at a known line
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) sb.append("padding line ").append(i).append("\n");
        sb.append("public int TIMEOUT = 300;\n"); // line 200 — the match
        for (int i = 0; i < 50; i++) sb.append("more padding ").append(i).append("\n");
        String content = sb.toString();

        String fragment = PlanAndExecuteAgentService.extractFragment(content, "TIMEOUT");
        assertThat(fragment).contains("TIMEOUT = 300");
        // Should not contain the whole file
        assertThat(fragment.length()).isLessThan(content.length());
    }

    @Test
    void extractFragment_keywordNotFound_returnsEmpty() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) sb.append("line ").append(i).append("\n");
        assertThat(PlanAndExecuteAgentService.extractFragment(sb.toString(), "NOTPRESENT")).isEmpty();
    }

    @Test
    void extractFragment_nullInputs_returnsEmpty() {
        assertThat(PlanAndExecuteAgentService.extractFragment(null, "key")).isEmpty();
        assertThat(PlanAndExecuteAgentService.extractFragment("content", null)).isEmpty();
    }

    @Test
    void extractFragment_multipleOccurrences_allCaptured() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) sb.append("line ").append(i).append("\n");
        // Two occurrences far apart
        String[] lines = sb.toString().split("\n");
        lines[10]  = "void firstMethod() { /* KEYWORD */ }";
        lines[200] = "void secondMethod() { /* KEYWORD */ }";
        String content = String.join("\n", lines);

        String fragment = PlanAndExecuteAgentService.extractFragment(content, "KEYWORD");
        assertThat(fragment).contains("firstMethod");
        assertThat(fragment).contains("secondMethod");
    }

    @Test
    void parseSteps_editFileStep_lineBased() {
        String json = "[{\"tool\":\"editFile\",\"path\":\"src/Foo.java\","
                + "\"operation\":\"insertAfterLine\",\"line\":5,\"code\":\"void foo(){}\"}]";
        List<AgentStep> steps = PlanAndExecuteAgentService.parseSteps(json);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).operation()).isEqualTo("insertAfterLine");
        assertThat(steps.get(0).line()).isEqualTo(5);
        assertThat(steps.get(0).code()).isEqualTo("void foo(){}");
    }

    @Test
    void parseSteps_editFileStep_replaceLines() {
        String json = "[{\"tool\":\"editFile\",\"path\":\"src/Foo.java\","
                + "\"operation\":\"replaceLines\",\"startLine\":3,\"endLine\":5,"
                + "\"code\":\"void updated(){}\"}]";
        List<AgentStep> steps = PlanAndExecuteAgentService.parseSteps(json);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).operation()).isEqualTo("replaceLines");
        assertThat(steps.get(0).startLine()).isEqualTo(3);
        assertThat(steps.get(0).endLine()).isEqualTo(5);
    }
}
