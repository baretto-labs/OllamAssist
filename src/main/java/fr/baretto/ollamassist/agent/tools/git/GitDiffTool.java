package fr.baretto.ollamassist.agent.tools.git;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.tools.AgentTool;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Returns the output of {@code git diff [args]} in the project root.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code args} — optional extra arguments (e.g. {@code "HEAD~1"}, {@code "--staged"})</li>
 *   <li>{@code path} — optional path to limit the diff to a specific file</li>
 * </ul>
 *
 * READ_ONLY — no confirmation required.
 */
@Slf4j
public final class GitDiffTool implements AgentTool {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_CHARS = 16_000;

    private final Project project;

    public GitDiffTool(Project project) {
        this.project = project;
    }

    @Override
    public String toolId() {
        return "GIT_DIFF";
    }

    /**
     * Whitelist of safe git-diff arguments.
     * ProcessBuilder does not invoke a shell, but git itself interprets certain flag patterns
     * (e.g. {@code --format=}, ref expressions). Restricting to known-safe values prevents
     * an agent from injecting unexpected git behaviour.
     */
    private static final java.util.Set<String> ALLOWED_DIFF_FLAGS = Set.of(
            "--cached", "--staged", "--stat", "--name-only", "--name-status",
            "--no-color", "--color=never", "--diff-filter=M", "--diff-filter=A",
            "--diff-filter=D", "ORIG_HEAD", "MERGE_HEAD"
    );

    private static boolean isAllowedGitDiffArg(String arg) {
        if (ALLOWED_DIFF_FLAGS.contains(arg)) return true;
        // Allow HEAD, HEAD~1, HEAD~2, … HEAD~99
        return arg.equals("HEAD") || arg.matches("HEAD~[1-9][0-9]?");
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return ToolResult.failure("Project base path is not available");
        }

        List<String> command = new ArrayList<>(List.of("git", "diff"));

        String args = (String) params.get("args");
        if (args != null && !args.isBlank()) {
            for (String arg : args.trim().split("\\s+")) {
                if (!isAllowedGitDiffArg(arg)) {
                    return ToolResult.failure("Git diff argument not allowed: '" + arg
                            + "'. Allowed: --cached/--staged/--stat/--name-only/--name-status"
                            + "/--no-color/HEAD/HEAD~N/ORIG_HEAD/MERGE_HEAD");
                }
                command.add(arg);
            }
        }

        String path = (String) params.get("path");
        if (path != null && !path.isBlank()) {
            command.add("--");
            command.add(path.trim());
        }

        try {
            Process process = new ProcessBuilder(command)
                    .directory(new File(basePath))
                    .redirectErrorStream(true)
                    .start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < MAX_OUTPUT_CHARS) {
                        output.append(line).append('\n');
                    }
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure("git diff timed out");
            }

            int exitCode = process.exitValue();
            String result = output.toString().stripTrailing();
            if (output.length() >= MAX_OUTPUT_CHARS) {
                result += "\n[diff truncated]";
            }
            if (exitCode != 0) {
                return ToolResult.failure("git diff exited with code " + exitCode + ":\n" + result);
            }
            return ToolResult.success(result.isEmpty() ? "(no changes)" : result);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.failure("git diff interrupted");
        } catch (Exception e) {
            log.error("GIT_DIFF failed", e);
            return ToolResult.failure("Failed to run git diff: " + e.getMessage());
        }
    }
}
