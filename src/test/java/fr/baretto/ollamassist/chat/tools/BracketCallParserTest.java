package fr.baretto.ollamassist.chat.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BracketCallParserTest {

    private BracketCallParser parser;

    @BeforeEach
    void setUp() {
        parser = new BracketCallParser();
    }

    @Test
    void testParseValidBracketCallWithSingleQuotes() {
        String text = "[CALL CreateFile('src/main/java/HelloWorld.java', 'public class HelloWorld {}')]";

        Optional<DetectedToolCall> result = parser.parse(text);

        assertThat(result).isPresent();
        assertThat(result.get().getToolName()).isEqualTo("CreateFile");
        assertThat(result.get().getArguments()).containsEntry("path", "src/main/java/HelloWorld.java");
        assertThat(result.get().getArguments()).containsEntry("content", "public class HelloWorld {}");
        assertThat(result.get().getDetectedBy()).isEqualTo("BracketCallParser");
    }

    @Test
    void testParseValidBracketCallWithDoubleQuotes() {
        String text = "[CALL CreateFile(\"src/test/Test.java\", \"test content\")]";

        Optional<DetectedToolCall> result = parser.parse(text);

        assertThat(result).isPresent();
        assertThat(result.get().getToolName()).isEqualTo("CreateFile");
        assertThat(result.get().getArguments()).containsEntry("path", "src/test/Test.java");
        assertThat(result.get().getArguments()).containsEntry("content", "test content");
    }

    @Test
    void testParseMultilineContent() {
        String text = "[CALL CreateFile('Main.java', 'public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello\");\n    }\n}')]";

        Optional<DetectedToolCall> result = parser.parse(text);

        assertThat(result).isPresent();
        assertThat(result.get().getArguments()).containsKey("content");
        assertThat(result.get().getArguments().get("content")).contains("public class Main");
    }

    @Test
    void testParseWithNormalWhitespace() {
        String text = "[CALL CreateFile('path.java', 'content')]";

        Optional<DetectedToolCall> result = parser.parse(text);

        assertThat(result).isPresent();
        assertThat(result.get().getToolName()).isEqualTo("CreateFile");
    }

    @Test
    void testParseInvalidFormat() {
        String text = "CreateFile('path.java', 'content')";

        Optional<DetectedToolCall> result = parser.parse(text);

        assertThat(result).isEmpty();
    }

    @Test
    void testParseNonCreateFileTool() {
        String text = "[CALL OtherTool('arg1', 'arg2')]";

        Optional<DetectedToolCall> result = parser.parse(text);

        assertThat(result).isEmpty(); // Only CreateFile is supported
    }

    @Test
    void testParseMissingArguments() {
        String text = "[CALL CreateFile('only-one-arg')]";

        Optional<DetectedToolCall> result = parser.parse(text);

        assertThat(result).isEmpty(); // CreateFile requires 2 arguments
    }

    @Test
    void testParseNullInput() {
        Optional<DetectedToolCall> result = parser.parse(null);

        assertThat(result).isEmpty();
    }

    @Test
    void testParseEmptyInput() {
        Optional<DetectedToolCall> result = parser.parse("");

        assertThat(result).isEmpty();
    }

    @Test
    void testParseCallInLongerText() {
        String text = "Here is the code:\n```java\npublic class Test {}\n```\n[CALL CreateFile('Test.java', 'public class Test {}')]\nFile created!";

        Optional<DetectedToolCall> result = parser.parse(text);

        assertThat(result).isPresent();
        assertThat(result.get().getToolName()).isEqualTo("CreateFile");
        assertThat(result.get().getOriginalText()).contains("[CALL CreateFile");
    }

    @Test
    void testGetParserName() {
        assertThat(parser.getParserName()).isEqualTo("BracketCallParser");
    }
}
