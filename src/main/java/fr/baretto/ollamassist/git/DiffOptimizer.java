package fr.baretto.ollamassist.git;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DiffOptimizer {

    public static String filterRelevantLines(String rawDiff) {
        return rawDiff.lines()
                .filter(line -> line.startsWith("+") || line.startsWith("-"))
                .map(line -> {
                    if (line.startsWith("---") || line.startsWith("+++")) return null;
                    if (line.startsWith("@@")) return null;
                    return line.replaceAll("\\s+$", "");
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }


    public static String smartFilter(String rawDiff) {
        List<String> lines = rawDiff.lines().toList();
        StringBuilder cleaned = new StringBuilder();
        boolean inHunk = false;

        for (String line : lines) {
            if (line.startsWith("@@")) {
                inHunk = true;
                continue;
            }

            if (inHunk && (line.startsWith("+") || line.startsWith("-"))) {
                cleaned.append(line).append("\n");
            }

            if (line.startsWith("diff --git")) {
                inHunk = false;
            }
        }

        return cleaned.toString();
    }
}
