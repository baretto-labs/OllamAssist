package fr.baretto.ollamassist.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("LoopDetector removed — ReAct loop replaced by PlanAndExecuteAgentService")
class LoopDetectorTest {

    FunctionCallingAgentService.LoopDetector detector;

    @BeforeEach
    void setUp() {
        detector = new FunctionCallingAgentService.LoopDetector();
    }

    // -------------------------------------------------------------------------
    // isStuck
    // -------------------------------------------------------------------------

    @Test
    void isStuck_belowThreshold_returnsFalse() {
        detector.record("readFile", "src/Foo.java");
        assertThat(detector.isStuck("readFile", "src/Foo.java")).isFalse();
    }

    // File tools: blocked on 3rd call (STUCK_THRESHOLD_FILE = 2)
    @Test
    void isStuck_fileTool_atThreshold_returnsTrue() {
        for (int i = 0; i < FunctionCallingAgentService.LoopDetector.STUCK_THRESHOLD_FILE; i++) {
            detector.record("readFile", "src/Foo.java");
        }
        assertThat(detector.isStuck("readFile", "src/Foo.java")).isTrue();
    }

    @Test
    void isStuck_fileTool_belowThreshold_returnsFalse() {
        detector.record("readFile", "src/Foo.java"); // only once
        assertThat(detector.isStuck("readFile", "src/Foo.java")).isFalse();
    }

    // Search tools: blocked on 2nd call (STUCK_THRESHOLD_SEARCH = 1)
    @Test
    void isStuck_searchTool_firstRepeat_returnsTrue() {
        detector.record("searchKnowledgeBase", "fizzbuzz");
        assertThat(detector.isStuck("searchKnowledgeBase", "fizzbuzz")).isTrue();
    }

    @Test
    void isStuck_searchWeb_firstRepeat_returnsTrue() {
        detector.record("searchWeb", "fizzbuzz algorithm");
        assertThat(detector.isStuck("searchWeb", "fizzbuzz algorithm")).isTrue();
    }

    @Test
    void isStuck_searchWorkspace_firstRepeat_returnsTrue() {
        detector.record("searchWorkspace", "FizzBuzz");
        assertThat(detector.isStuck("searchWorkspace", "FizzBuzz")).isTrue();
    }

    @Test
    void isStuck_differentTool_returnsFalse() {
        for (int i = 0; i < FunctionCallingAgentService.LoopDetector.STUCK_THRESHOLD_FILE; i++) {
            detector.record("readFile", "src/Foo.java");
        }
        assertThat(detector.isStuck("editFile", "src/Foo.java")).isFalse();
    }

    @Test
    void isStuck_differentArg_returnsFalse() {
        for (int i = 0; i < FunctionCallingAgentService.LoopDetector.STUCK_THRESHOLD_FILE; i++) {
            detector.record("readFile", "src/Foo.java");
        }
        assertThat(detector.isStuck("readFile", "src/Bar.java")).isFalse();
    }

    @Test
    void isStuck_caseInsensitiveArg() {
        detector.record("readFile", "src/Foo.java");
        detector.record("readFile", "SRC/FOO.JAVA");
        assertThat(detector.isStuck("readFile", "src/foo.java")).isTrue();
    }

    @Test
    void isStuck_slidingWindowEvictsOldEntries() {
        // Fill window with other calls to push out old readFile records
        detector.record("readFile", "src/Foo.java");
        // Interleave with other tools to evict the first readFile from window
        for (int i = 0; i < FunctionCallingAgentService.LoopDetector.WINDOW; i++) {
            detector.record("searchWorkspace", "FizzBuzz");
        }
        // readFile was evicted from window — no longer stuck
        assertThat(detector.isStuck("readFile", "src/Foo.java")).isFalse();
    }

    // -------------------------------------------------------------------------
    // stuckMessage
    // -------------------------------------------------------------------------

    @Test
    void stuckMessage_containsToolNameAndLoopWord() {
        String msg = detector.stuckMessage("readFile");
        assertThat(msg).containsIgnoringCase("readFile");
        assertThat(msg).containsIgnoringCase("loop");
        assertThat(msg).startsWith("ERROR:");
    }
}
