package fr.baretto.ollamassist.agent.tools.files;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for EditFileTool.findActualSubstring — the core of normalizeWhitespace matching.
 */
class EditFileToolNormalizeTest {

    @Test
    void exactMatch_returnsSubstring() {
        String original = "public void foo() {\n    return;\n}";
        String found = EditFileTool.findActualSubstring(original, "public void foo()");
        assertThat(found).isEqualTo("public void foo()");
    }

    @Test
    void extraSpacesInOriginal_returnsActualSubstring() {
        String original = "public   void   foo()";
        String normalized = "public void foo()";
        String found = EditFileTool.findActualSubstring(original, normalized);
        assertThat(found).isEqualTo("public   void   foo()");
    }

    @Test
    void tabVsSpaceDifference_returnsActualSubstring() {
        String original = "public\tvoid\tfoo()";
        String normalized = "public void foo()";
        String found = EditFileTool.findActualSubstring(original, normalized);
        assertThat(found).isEqualTo("public\tvoid\tfoo()");
    }

    @Test
    void multipleSpaces_correctlyMapped() {
        String original = "int   x   =   5;";
        String normalized = "int x = 5;";
        String found = EditFileTool.findActualSubstring(original, normalized);
        assertThat(found).isEqualTo("int   x   =   5;");
    }

    @Test
    void noMatch_returnsNull() {
        String original = "hello world";
        assertThat(EditFileTool.findActualSubstring(original, "foo bar")).isNull();
    }

    @Test
    void nullNormalized_returnsNull() {
        assertThat(EditFileTool.findActualSubstring("hello", null)).isNull();
    }

    @Test
    void emptyNormalized_returnsNull() {
        assertThat(EditFileTool.findActualSubstring("hello", "")).isNull();
    }

    @Test
    void partialMatchAtEnd_found() {
        String original = "class Foo {\n    void   bar() {}\n}";
        String normalized = "void bar()";
        String found = EditFileTool.findActualSubstring(original, normalized);
        assertThat(found).isEqualTo("void   bar()");
    }
}
