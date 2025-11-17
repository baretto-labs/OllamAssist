package fr.baretto.ollamassist.chat.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallDetectorTest {

    private ToolCallDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ToolCallDetector();
    }

    @Test
    void testDetectBracketCallFormat() {
        String text = "Here's the code:\n[CALL CreateFile('Main.java', 'public class Main {}')]";

        Optional<DetectedToolCall> result = detector.detect(text);

        assertThat(result).isPresent();
        assertThat(result.get().getToolName()).isEqualTo("CreateFile");
        assertThat(result.get().getArguments()).containsKey("path");
        assertThat(result.get().getArguments()).containsKey("content");
    }

    @Test
    void testDetectNoToolCall() {
        String text = "This is a normal response without any tool calls.";

        Optional<DetectedToolCall> result = detector.detect(text);

        assertThat(result).isEmpty();
    }

    @Test
    void testDetectWithMultipleFormats() {
        // Should match the first successful parser (BracketCallParser)
        String text = "[CALL CreateFile('path.java', 'content')]";

        Optional<DetectedToolCall> result = detector.detect(text);

        assertThat(result).isPresent();
        assertThat(result.get().getDetectedBy()).isEqualTo("BracketCallParser");
    }

    @Test
    void testContainsToolCallTrue() {
        String text = "[CALL CreateFile('test.java', 'test')]";

        boolean contains = detector.containsToolCall(text);

        assertThat(contains).isTrue();
    }

    @Test
    void testContainsToolCallFalse() {
        String text = "No tool call here";

        boolean contains = detector.containsToolCall(text);

        assertThat(contains).isFalse();
    }

    @Test
    void testDetectNullInput() {
        Optional<DetectedToolCall> result = detector.detect(null);

        assertThat(result).isEmpty();
    }

    @Test
    void testDetectEmptyInput() {
        Optional<DetectedToolCall> result = detector.detect("");

        assertThat(result).isEmpty();
    }

    @Test
    void testDetectWithCodeBlocks() {
        String text = """
                Here is the code:
                ```java
                public class HelloWorld {
                    public static void main(String[] args) {
                        System.out.println("Hello World");
                    }
                }
                ```
                [CALL CreateFile('src/main/java/HelloWorld.java', 'public class HelloWorld { }')]
                """;

        Optional<DetectedToolCall> result = detector.detect(text);

        assertThat(result).isPresent();
        assertThat(result.get().getToolName()).isEqualTo("CreateFile");
    }
}
