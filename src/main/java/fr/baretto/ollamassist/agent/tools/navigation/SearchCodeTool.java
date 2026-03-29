package fr.baretto.ollamassist.agent.tools.navigation;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.tools.AgentTool;
import fr.baretto.ollamassist.agent.tools.SecretDetector;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public final class SearchCodeTool implements AgentTool {

    private static final int MAX_MATCHES = 50;
    private static final int CONTEXT_LINES = 2;

    private final Project project;

    public SearchCodeTool(Project project) {
        this.project = project;
    }

    @Override
    public String toolId() {
        return "CODE_SEARCH";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String query = (String) params.get("query");
        if (query == null || query.isBlank()) {
            return ToolResult.failure("Parameter 'query' is required");
        }

        String base = project.getBasePath();
        if (base == null) {
            return ToolResult.failure("Project base path is not available");
        }

        Path root = Paths.get(base);
        List<String> results = new ArrayList<>();

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (name.equals(".git") || name.equals("build") || name.equals("out") || name.equals(".ollamassist")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (results.size() >= MAX_MATCHES) {
                        return FileVisitResult.TERMINATE;
                    }
                    String fileName = file.getFileName().toString();
                    if (!isTextFile(fileName)) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        String content = readWithFallback(file);
                        String[] lines = content.split("\n");
                        Path relative = root.relativize(file);
                        for (int i = 0; i < lines.length && results.size() < MAX_MATCHES; i++) {
                            if (lines[i].contains(query)) {
                                String snippet = formatMatch(relative.toString(), i + 1, lines, i);
                                if (SecretDetector.detect(snippet) != null) {
                                    log.debug("Suppressed CODE_SEARCH match at {}:{} — possible secret", relative, i + 1);
                                } else {
                                    results.add(snippet);
                                }
                            }
                        }
                    } catch (IOException e) {
                        // skip binary or unreadable files
                        log.debug("Skipping unreadable file {}: {}", file, e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Code search failed for query: {}", query, e);
            return ToolResult.failure("Code search failed: " + e.getMessage());
        }

        if (results.isEmpty()) {
            return ToolResult.success("No matches found for: " + query);
        }

        String output = String.join("\n---\n", results);
        if (results.size() == MAX_MATCHES) {
            output += "\n(limited to " + MAX_MATCHES + " matches)";
        }
        return ToolResult.success(output);
    }

    /**
     * Reads a file as UTF-8; falls back to ISO-8859-1 if the file contains bytes
     * that are not valid UTF-8 (common in legacy projects).
     */
    private static String readWithFallback(Path file) throws IOException {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (java.nio.charset.MalformedInputException e) {
            return Files.readString(file, StandardCharsets.ISO_8859_1);
        }
    }

    private String formatMatch(String filePath, int lineNumber, String[] lines, int matchIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append(filePath).append(":").append(lineNumber).append("\n");
        int start = Math.max(0, matchIndex - CONTEXT_LINES);
        int end = Math.min(lines.length - 1, matchIndex + CONTEXT_LINES);
        for (int i = start; i <= end; i++) {
            String prefix = i == matchIndex ? "> " : "  ";
            sb.append(prefix).append(lines[i]);
            if (i < end) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private boolean isTextFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".xml")
                || lower.endsWith(".gradle") || lower.endsWith(".kts") || lower.endsWith(".properties")
                || lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".yml")
                || lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".toml")
                || lower.endsWith(".html") || lower.endsWith(".css") || lower.endsWith(".js")
                || lower.endsWith(".ts") || lower.endsWith(".py") || lower.endsWith(".rb")
                || lower.endsWith(".go") || lower.endsWith(".rs");
    }
}
