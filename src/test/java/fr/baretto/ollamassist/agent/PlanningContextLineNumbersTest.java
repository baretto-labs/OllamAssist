package fr.baretto.ollamassist.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The planner is told that the line numbers in its file context are absolute, and
 * {@code LineEditTool} applies them to the real document. If the context numbers a fragment
 * from 1, every {@code insertAfterLine} lands at the wrong place — silently, since the edit
 * itself succeeds. These tests pin the numbering to the source file.
 */
@DisplayName("Planning context line numbers")
class PlanningContextLineNumbersTest {

    /** A file whose line N contains the marker "LINE_N", so a number can be checked against its content. */
    private static String numberedSourceFile(int lineCount) {
        return IntStream.rangeClosed(1, lineCount)
                .mapToObj(i -> "LINE_" + i + " content")
                .collect(Collectors.joining("\n"));
    }

    @Test
    @DisplayName("numbers a small file from its first line")
    void shouldNumberSmallFileFromLineOne() {
        String fragment = PlanAndExecuteAgentService.extractFragment(numberedSourceFile(10), "LINE_4");

        assertThat(fragment).contains("   1 | LINE_1 content");
        assertThat(fragment).contains("   4 | LINE_4 content");
        assertThat(fragment).contains("  10 | LINE_10 content");
    }

    @Test
    @DisplayName("keeps the source line number of a match deep in a large file")
    void shouldUseAbsoluteNumbersInLargeFile() {
        String fragment = PlanAndExecuteAgentService.extractFragment(numberedSourceFile(400), "LINE_200 ");

        assertThat(fragment).contains(" 200 | LINE_200 content");
        assertThat(fragment).doesNotContain("   1 | LINE_200 content");
    }

    @Test
    @DisplayName("keeps every line of a window aligned with its own number")
    void shouldAlignEveryLineOfTheWindowWithItsNumber() {
        String fragment = PlanAndExecuteAgentService.extractFragment(numberedSourceFile(400), "LINE_200 ");

        fragment.lines()
                .filter(line -> line.contains(" | LINE_"))
                .forEach(line -> {
                    int number = Integer.parseInt(line.substring(0, line.indexOf('|')).trim());
                    assertThat(line).contains("| LINE_" + number + " content");
                });
    }

    @Test
    @DisplayName("marks the lines omitted between two windows")
    void shouldMarkOmittedLinesBetweenWindows() {
        String source = numberedSourceFile(400);

        String fragment = PlanAndExecuteAgentService.extractFragment(source, "LINE_100 ");

        assertThat(fragment).contains("lines 1-79 omitted");
    }

    @Test
    @DisplayName("returns nothing when the keyword is absent from a large file")
    void shouldReturnNothingWhenKeywordAbsent() {
        assertThat(PlanAndExecuteAgentService.extractFragment(numberedSourceFile(400), "ABSENT")).isEmpty();
    }

    @Test
    @DisplayName("renders the planning context with the numbers produced by discovery")
    void shouldRenderPlanningContextWithoutRenumbering() {
        String fragment = PlanAndExecuteAgentService.extractFragment(numberedSourceFile(400), "LINE_200 ");

        String message = PlanAndExecuteAgentService.buildPlanningMessage(
                "add a method",
                new PlanAndExecuteAgentService.DiscoveryResult(
                        java.util.Map.of("src/Foo.java", fragment), java.util.Set.of()),
                java.util.List.of("src/main/java"));

        assertThat(message).contains(" 200 | LINE_200 content");
        assertThat(message).doesNotContain("   1 | LINE_180 content");
    }
}
