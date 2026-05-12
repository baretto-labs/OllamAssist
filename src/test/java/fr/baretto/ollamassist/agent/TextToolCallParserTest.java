package fr.baretto.ollamassist.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TextToolCallParserTest {

    // -------------------------------------------------------------------------
    // parse — happy path
    // -------------------------------------------------------------------------

    @Test
    void parse_singleCall_parametersKey() {
        String text = """
                Je vais créer ce fichier.

                {"name": "writeFile", "parameters": {"path": "src/Foo.java", "content": "class Foo {}"}}
                """;

        List<TextToolCallParser.ParsedToolCall> calls = TextToolCallParser.parse(text);

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).name()).isEqualTo("writeFile");
        assertThat(calls.get(0).args()).containsEntry("path", "src/Foo.java");
        assertThat(calls.get(0).args()).containsEntry("content", "class Foo {}");
    }

    @Test
    void parse_singleCall_argumentsKey() {
        String text = """
                {"name": "readFile", "arguments": {"path": "src/Bar.java"}}
                """;

        List<TextToolCallParser.ParsedToolCall> calls = TextToolCallParser.parse(text);

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).name()).isEqualTo("readFile");
        assertThat(calls.get(0).args()).containsEntry("path", "src/Bar.java");
    }

    @Test
    void parse_multipleCalls_returnsAll() {
        String text = """
                {"name": "searchWorkspace", "parameters": {"query": "FizzBuzz"}}
                Found it. Now I will edit.
                {"name": "editFile", "parameters": {"path": "src/Foo.java", "search": "return 0;", "replace": "return a + b;", "replaceAll": "false"}}
                """;

        List<TextToolCallParser.ParsedToolCall> calls = TextToolCallParser.parse(text);

        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).name()).isEqualTo("searchWorkspace");
        assertThat(calls.get(1).name()).isEqualTo("editFile");
        assertThat(calls.get(1).args())
                .containsEntry("path", "src/Foo.java")
                .containsEntry("search", "return 0;")
                .containsEntry("replace", "return a + b;");
    }

    @Test
    void parse_argsJsonPreservesEscapedNewlines() {
        String text = "{\"name\": \"editFile\", \"parameters\": {\"path\": \"src/X.java\", \"search\": \"\\n}\", \"replace\": \"\\n\\n    public void foo() {}\\n}\"}}";

        List<TextToolCallParser.ParsedToolCall> calls = TextToolCallParser.parse(text);

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).args().get("search")).isEqualTo("\n}");
        assertThat(calls.get(0).args().get("replace")).contains("public void foo()");
    }

    @Test
    void parse_textWithNoToolCall_returnsEmpty() {
        String text = "Voici la réponse finale. Tout est fait.";
        assertThat(TextToolCallParser.parse(text)).isEmpty();
    }

    @Test
    void parse_nullText_returnsEmpty() {
        assertThat(TextToolCallParser.parse(null)).isEmpty();
    }

    @Test
    void parse_blankText_returnsEmpty() {
        assertThat(TextToolCallParser.parse("   ")).isEmpty();
    }

    @Test
    void parse_malformedJson_ignored() {
        String text = "{\"name\": \"readFile\", \"parameters\": {\"path\": }";  // invalid JSON
        assertThat(TextToolCallParser.parse(text)).isEmpty();
    }

    @Test
    void parse_objectWithoutNameKey_ignored() {
        String text = "{\"tool\": \"readFile\", \"parameters\": {\"path\": \"src/Foo.java\"}}";
        assertThat(TextToolCallParser.parse(text)).isEmpty();
    }

    @Test
    void parse_objectWithoutParametersOrArguments_ignored() {
        String text = "{\"name\": \"readFile\"}";
        assertThat(TextToolCallParser.parse(text)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // extractReasoningText
    // -------------------------------------------------------------------------

    @Test
    void extractReasoningText_returnsTextBeforeFirstCall() {
        String fullText = "Je vais créer ce fichier.\n\n{\"name\": \"writeFile\", \"parameters\": {\"path\": \"x\"}}";
        List<TextToolCallParser.ParsedToolCall> calls = TextToolCallParser.parse(fullText);

        String reasoning = TextToolCallParser.extractReasoningText(fullText, calls);

        assertThat(reasoning).isEqualTo("Je vais créer ce fichier.");
    }

    @Test
    void extractReasoningText_callAtStart_returnsEmpty() {
        String fullText = "{\"name\": \"writeFile\", \"parameters\": {\"path\": \"x\"}}";
        List<TextToolCallParser.ParsedToolCall> calls = TextToolCallParser.parse(fullText);

        String reasoning = TextToolCallParser.extractReasoningText(fullText, calls);

        assertThat(reasoning).isEmpty();
    }

    @Test
    void extractReasoningText_emptyCalls_returnsFullText() {
        String fullText = "No tool calls here.";
        String reasoning = TextToolCallParser.extractReasoningText(fullText, List.of());
        assertThat(reasoning).isEqualTo(fullText);
    }

    // -------------------------------------------------------------------------
    // parseArgsJson
    // -------------------------------------------------------------------------

    @Test
    void parseArgsJson_validJson_returnsMap() {
        String json = "{\"path\": \"src/Foo.java\", \"content\": \"class Foo {}\"}";
        Map<String, String> args = TextToolCallParser.parseArgsJson(json);

        assertThat(args).containsEntry("path", "src/Foo.java")
                        .containsEntry("content", "class Foo {}");
    }

    @Test
    void parseArgsJson_nullInput_returnsEmptyMap() {
        assertThat(TextToolCallParser.parseArgsJson(null)).isEmpty();
    }

    @Test
    void parseArgsJson_blankInput_returnsEmptyMap() {
        assertThat(TextToolCallParser.parseArgsJson("  ")).isEmpty();
    }

    @Test
    void parseArgsJson_malformedJson_returnsEmptyMap() {
        assertThat(TextToolCallParser.parseArgsJson("{broken")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findJsonEnd (internal — tested via package access)
    // -------------------------------------------------------------------------

    @Test
    void findJsonEnd_simpleObject_findsEnd() {
        String text = "{\"a\": \"b\"} rest";
        assertThat(TextToolCallParser.findJsonEnd(text, 0)).isEqualTo(9);
    }

    @Test
    void findJsonEnd_nestedObject_findsOuterEnd() {
        String text = "{\"a\": {\"b\": \"c\"}} rest";
        assertThat(TextToolCallParser.findJsonEnd(text, 0)).isEqualTo(16);
    }

    @Test
    void findJsonEnd_closingBraceInString_notCountedAsEnd() {
        String text = "{\"a\": \"}\"}  rest";
        assertThat(TextToolCallParser.findJsonEnd(text, 0)).isEqualTo(9);
    }

    @Test
    void findJsonEnd_unbalanced_returnsMinusOne() {
        String text = "{\"a\": \"b\"";
        assertThat(TextToolCallParser.findJsonEnd(text, 0)).isEqualTo(-1);
    }
}
