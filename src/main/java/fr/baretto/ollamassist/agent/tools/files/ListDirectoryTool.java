package fr.baretto.ollamassist.agent.tools.files;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.tools.AgentTool;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Lists the immediate contents of a directory (non-recursive).
 *
 * <p>Params:
 * <ul>
 *   <li>{@code path} — relative path from the project root (required). Use "" or "." for the root.</li>
 * </ul>
 *
 * <p>Each entry is prefixed with "[DIR]" or "[FILE]" and sorted: directories first, then files.
 * Hidden entries (starting with ".") and build artefacts ({@code build}, {@code out}, {@code .git})
 * are excluded to keep the output focused on project sources.
 */
@Slf4j
public final class ListDirectoryTool implements AgentTool {

    private static final int MAX_ENTRIES = 200;

    private final Project project;

    public ListDirectoryTool(Project project) {
        this.project = project;
    }

    @Override
    public String toolId() {
        return "LIST_DIRECTORY";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String pathParam = (String) params.get("path");
        if (pathParam == null || pathParam.isBlank() || ".".equals(pathParam.trim())) {
            pathParam = "";
        }

        Path dirPath;
        try {
            dirPath = pathParam.isEmpty()
                    ? FilePathGuard.resolveConfined(".", project)
                    : FilePathGuard.resolveConfined(pathParam, project);
        } catch (FilePathGuard.PathTraversalException e) {
            log.warn("Path traversal blocked: {}", e.getMessage());
            return ToolResult.failure(e.getMessage());
        } catch (IllegalStateException e) {
            return ToolResult.failure(e.getMessage());
        }

        if (!Files.exists(dirPath)) {
            return ToolResult.failure("Directory not found: " + pathParam);
        }
        if (!Files.isDirectory(dirPath)) {
            return ToolResult.failure("Path is a file, not a directory: " + pathParam);
        }

        List<String> dirs  = new ArrayList<>();
        List<String> files = new ArrayList<>();

        try (Stream<Path> stream = Files.list(dirPath)) {
            stream.forEach(entry -> {
                String name = entry.getFileName().toString();
                if (name.startsWith(".") || "build".equals(name) || "out".equals(name)) return;
                if (Files.isDirectory(entry)) {
                    dirs.add("[DIR]  " + name);
                } else {
                    files.add("[FILE] " + name);
                }
                if (dirs.size() + files.size() >= MAX_ENTRIES) return;
            });
        } catch (IOException e) {
            log.error("Failed to list directory: {}", dirPath, e);
            return ToolResult.failure("Failed to list directory: " + e.getMessage());
        }

        if (dirs.isEmpty() && files.isEmpty()) {
            return ToolResult.success("(empty directory)");
        }

        dirs.sort(String::compareTo);
        files.sort(String::compareTo);

        List<String> all = new ArrayList<>(dirs);
        all.addAll(files);

        String displayPath = pathParam.isEmpty() ? "." : pathParam;
        StringBuilder sb = new StringBuilder("Contents of: ").append(displayPath).append("\n");
        all.forEach(line -> sb.append(line).append("\n"));
        if (all.size() >= MAX_ENTRIES) {
            sb.append("(limited to ").append(MAX_ENTRIES).append(" entries)");
        }
        return ToolResult.success(sb.toString().stripTrailing());
    }
}
