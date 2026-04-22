package fr.baretto.ollamassist.agent.tools.files;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.tools.AgentTool;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public final class FindFilesTool implements AgentTool {

    private static final int MAX_RESULTS = 100;

    private final Project project;

    public FindFilesTool(Project project) {
        this.project = project;
    }

    @Override
    public String toolId() {
        return "FILE_FIND";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String pattern = (String) params.get("pattern");
        if (pattern == null || pattern.isBlank()) {
            return ToolResult.failure("Parameter 'pattern' is required (e.g. '**/*.java')");
        }

        String base = project.getBasePath();
        if (base == null) {
            return ToolResult.failure("Project base path is not available");
        }

        Path root = Paths.get(base);
        PathMatcher matcher = root.getFileSystem().getPathMatcher("glob:" + pattern);
        // Secondary matcher against filename only — handles root-level files when pattern is e.g. **/*.java
        String filePart = pattern.contains("/") ? pattern.substring(pattern.lastIndexOf('/') + 1) : pattern;
        PathMatcher filenameMatcher = root.getFileSystem().getPathMatcher("glob:" + filePart);
        List<String> matches = new ArrayList<>();

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path relative = root.relativize(file);
                    if (matcher.matches(relative) || filenameMatcher.matches(file.getFileName())) {
                        matches.add(relative.toString());
                        if (matches.size() >= MAX_RESULTS) {
                            return FileVisitResult.TERMINATE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (name.equals(".git") || name.equals("build") || name.equals("out") || name.equals(".ollamassist")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("File walk failed for pattern: {}", pattern, e);
            return ToolResult.failure("File search failed: " + e.getMessage());
        }

        if (matches.isEmpty()) {
            return ToolResult.success(
                    "No files found matching pattern: " + pattern
                    + "\nHint: FILE_FIND searches by filename/path pattern. "
                    + "If you are looking for a class, method, or string inside files, use CODE_SEARCH instead.");
        }

        String output = String.join("\n", matches);
        if (matches.size() == MAX_RESULTS) {
            output += "\n(limited to " + MAX_RESULTS + " results)";
        }
        return ToolResult.success(output);
    }
}
