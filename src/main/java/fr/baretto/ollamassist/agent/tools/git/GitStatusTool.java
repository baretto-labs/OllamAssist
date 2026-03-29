package fr.baretto.ollamassist.agent.tools.git;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.tools.AgentTool;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Returns the output of {@code git status} in the project root.
 * READ_ONLY — no confirmation required.
 */
@Slf4j
public final class GitStatusTool implements AgentTool {

    private static final int TIMEOUT_SECONDS = 30;

    private final Project project;

    public GitStatusTool(Project project) {
        this.project = project;
    }

    @Override
    public String toolId() {
        return "GIT_STATUS";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        return runGit(List.of("git", "status"));
    }

    private ToolResult runGit(List<String> command) {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return ToolResult.failure("Project base path is not available");
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
                    output.append(line).append('\n');
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure("git command timed out");
            }

            int exitCode = process.exitValue();
            String result = output.toString().stripTrailing();
            if (exitCode != 0) {
                return ToolResult.failure("git exited with code " + exitCode + ":\n" + result);
            }
            return ToolResult.success(result.isEmpty() ? "(no output)" : result);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.failure("git command interrupted");
        } catch (Exception e) {
            log.error("GIT_STATUS failed", e);
            return ToolResult.failure("Failed to run git: " + e.getMessage());
        }
    }
}
