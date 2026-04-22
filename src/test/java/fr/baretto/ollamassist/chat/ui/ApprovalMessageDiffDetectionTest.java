package fr.baretto.ollamassist.chat.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ApprovalMessage.isDiffContent().
 *
 * The method distinguishes buildDiff() output (--- BEFORE / +++ AFTER markers)
 * from raw source code so the correct syntax highlighting mode is applied.
 */
class ApprovalMessageDiffDetectionTest {

    // -------------------------------------------------------------------------
    // Diff content — must be detected
    // -------------------------------------------------------------------------

    @Test
    void beforeMarker_isDiff() {
        String content = "File: src/Foo.java\n\n--- BEFORE:\npublic void process() {}\n+++ AFTER:\npublic void process(Context ctx) {}";
        assertThat(ApprovalMessage.isDiffContent(content)).isTrue();
    }

    @Test
    void afterMarker_alone_isDiff() {
        String content = "+++ AFTER:\npublic void process(Context ctx) {}";
        assertThat(ApprovalMessage.isDiffContent(content)).isTrue();
    }

    @Test
    void beforeMarker_alone_isDiff() {
        String content = "--- BEFORE:\nsome old code";
        assertThat(ApprovalMessage.isDiffContent(content)).isTrue();
    }

    @Test
    void realBuildDiffOutput_isDiff() {
        // Simulates what EditFileTool.buildDiff() actually produces
        String content = "File: src/main/java/fr/baretto/ollamassist/agent/AgentOrchestrator.java\n"
                + "\n--- BEFORE:\n"
                + "private static final int MAX_PHASES = 5;\n"
                + "\n+++ AFTER:\n"
                + "private static final int MAX_PHASES = 10;";
        assertThat(ApprovalMessage.isDiffContent(content)).isTrue();
    }

    @Test
    void diffWithWarning_isDiff() {
        // buildDiff includes a warning line when multiple occurrences exist
        String content = "File: src/Foo.java\nWARNING: 3 occurrences will ALL be replaced\n\n--- BEFORE:\nold\n+++ AFTER:\nnew";
        assertThat(ApprovalMessage.isDiffContent(content)).isTrue();
    }

    // -------------------------------------------------------------------------
    // Source code — must NOT be detected as diff
    // -------------------------------------------------------------------------

    @Test
    void plainJavaClass_notDiff() {
        String content = "public class OrderService {\n    private final Repository repo;\n    void save(Order o) { repo.save(o); }\n}";
        assertThat(ApprovalMessage.isDiffContent(content)).isFalse();
    }

    @Test
    void shellCommand_notDiff() {
        String content = "./gradlew clean build --quiet";
        assertThat(ApprovalMessage.isDiffContent(content)).isFalse();
    }

    @Test
    void emptyString_notDiff() {
        assertThat(ApprovalMessage.isDiffContent("")).isFalse();
    }

    @Test
    void configFile_notDiff() {
        String content = "server.port=8080\nspring.datasource.url=jdbc:h2:mem:testdb";
        assertThat(ApprovalMessage.isDiffContent(content)).isFalse();
    }

    @Test
    void fileWithDashesInComment_notDiff() {
        // A file that contains dashes but NOT the exact diff markers
        String content = "// --- some comment ---\npublic class Foo {}";
        assertThat(ApprovalMessage.isDiffContent(content)).isFalse();
    }

    @Test
    void fileWithPlusPlusPlusInCode_notDiff() {
        // "+++ AFTER:" appears only as a complete marker — a C++ comment won't match
        String content = "// +++ performance improvement\npublic void run() {}";
        assertThat(ApprovalMessage.isDiffContent(content)).isFalse();
    }
}
