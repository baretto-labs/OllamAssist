package fr.baretto.ollamassist.agent.tools.terminal;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.tools.AgentTool;
import fr.baretto.ollamassist.agent.tools.ToolApprovalHelper;
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
 * Executes a shell command on the host OS.
 *
 * <p>Security tiers:
 * <ul>
 *   <li>READ_ONLY  — executes without confirmation.</li>
 *   <li>MUTATING   — requests user confirmation before execution.</li>
 *   <li>DESTRUCTIVE — always blocked; returns a failure immediately.</li>
 * </ul>
 *
 * <p>Params:
 * <ul>
 *   <li>{@code command}    — the shell command string (required)</li>
 *   <li>{@code workingDir} — working directory path (optional; defaults to project root)</li>
 * </ul>
 */
@Slf4j
public final class RunCommandTool implements AgentTool {

    private int timeoutSeconds() {
        try {
            return fr.baretto.ollamassist.setting.OllamaSettings.getInstance().getRunCommandTimeoutSeconds();
        } catch (Exception e) {
            return 60;
        }
    }
    private static final int MAX_OUTPUT_CHARS = 8_000;

    private final Project project;
    private final ToolApprovalHelper approvalHelper;

    public RunCommandTool(Project project) {
        this.project = project;
        this.approvalHelper = new ToolApprovalHelper(project);
    }

    /** Package-private constructor for unit tests — allows injecting a mock approval helper. */
    RunCommandTool(Project project, ToolApprovalHelper approvalHelper) {
        this.project = project;
        this.approvalHelper = approvalHelper;
    }

    @Override
    public String toolId() {
        return "RUN_COMMAND";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String command = (String) params.get("command");
        if (command == null || command.isBlank()) {
            return ToolResult.failure("Parameter 'command' is required");
        }

        String workingDirParam = (String) params.get("workingDir");
        File workingDir;
        try {
            workingDir = resolveWorkingDir(workingDirParam);
        } catch (WorkingDirEscapeException e) {
            return ToolResult.failure(e.getMessage());
        }

        CommandTier tier = CommandClassifier.classify(command);
        log.debug("RUN_COMMAND tier={} command={}", tier, command);

        if (tier == CommandTier.DESTRUCTIVE) {
            return ToolResult.failure(
                    "Command blocked (DESTRUCTIVE tier): " + command
                            + ". Destructive commands are never executed by the agent.");
        }

        if (tier == CommandTier.MUTATING) {
            boolean approved = approvalHelper.requestApproval(
                    "Run command?",
                    workingDir.getAbsolutePath(),
                    command
            );
            if (!approved) {
                return ToolResult.failure("User rejected command execution: " + command);
            }
        }

        return runProcess(command, workingDir);
    }

    private ToolResult runProcess(String command, File workingDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.directory(workingDir);
            pb.redirectErrorStream(true);

            // Use shell to allow pipes, redirections, etc.
            String shell = System.getProperty("os.name", "").toLowerCase().contains("win") ? "cmd.exe" : "sh";
            String shellFlag = shell.equals("cmd.exe") ? "/c" : "-c";
            pb.command(List.of(shell, shellFlag, command));

            Process process = pb.start();

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

            int timeout = timeoutSeconds();
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure("Command timed out after " + timeout + "s: " + command);
            }

            int exitCode = process.exitValue();
            String result = output.toString().stripTrailing();
            if (output.length() >= MAX_OUTPUT_CHARS) {
                result += "\n[output truncated]";
            }

            if (exitCode != 0) {
                return ToolResult.failure("Command exited with code " + exitCode + ":\n" + result);
            }

            log.debug("RUN_COMMAND success: {}", command);
            return ToolResult.success(result.isEmpty() ? "(no output)" : result);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.failure("Command interrupted: " + command);
        } catch (Exception e) {
            log.error("RUN_COMMAND failed: {}", command, e);
            return ToolResult.failure("Failed to run command: " + e.getMessage());
        }
    }

    /**
     * Thrown when the requested working directory resolves outside the project root.
     * Callers convert this to a {@link ToolResult#failure} so the agent knows the
     * path was rejected rather than silently running in the wrong directory.
     */
    static final class WorkingDirEscapeException extends Exception {
        WorkingDirEscapeException(String message) { super(message); }
    }

    /**
     * Resolves and validates the working directory.
     *
     * @throws WorkingDirEscapeException if the path resolves outside the project root
     *                                   (path traversal or non-existent directory)
     */
    private File resolveWorkingDir(String workingDirParam) throws WorkingDirEscapeException {
        String base = project.getBasePath();
        File projectRoot = base != null ? new File(base) : new File(".");

        if (workingDirParam == null || workingDirParam.isBlank()) {
            return projectRoot;
        }

        try {
            File candidate = new File(workingDirParam);
            if (!candidate.isAbsolute()) {
                candidate = new File(projectRoot, workingDirParam);
            }
            // Resolve symlinks and normalize ".." / "." segments to prevent symlink escapes
            java.nio.file.Path resolved = candidate.toPath().toRealPath();
            java.nio.file.Path root = projectRoot.toPath().toRealPath();

            // Reject paths that escape the project root (even via symlinks) — fail explicitly
            // rather than silently falling back to projectRoot. A silent fallback would cause the
            // agent to think it ran in the requested subdirectory when it actually ran elsewhere.
            if (!resolved.startsWith(root)) {
                throw new WorkingDirEscapeException(
                        "workingDir '" + workingDirParam + "' resolves outside the project root and cannot be used. "
                        + "Use a path relative to the project root (e.g. 'src/main/java').");
            }

            File resolvedFile = resolved.toFile();
            if (resolvedFile.isDirectory()) {
                return resolvedFile;
            }
            // Path exists but is not a directory
            throw new WorkingDirEscapeException(
                    "workingDir '" + workingDirParam + "' exists but is not a directory.");

        } catch (WorkingDirEscapeException e) {
            throw e;
        } catch (java.nio.file.NoSuchFileException e) {
            throw new WorkingDirEscapeException(
                    "workingDir '" + workingDirParam + "' does not exist. "
                    + "Create the directory first or omit workingDir to use the project root.");
        } catch (Exception e) {
            log.warn("Failed to resolve workingDir '{}': {}", workingDirParam, e.getMessage());
            throw new WorkingDirEscapeException(
                    "Cannot resolve workingDir '" + workingDirParam + "': " + e.getMessage());
        }
    }
}
