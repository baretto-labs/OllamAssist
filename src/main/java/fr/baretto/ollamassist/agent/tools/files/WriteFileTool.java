package fr.baretto.ollamassist.agent.tools.files;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import fr.baretto.ollamassist.agent.tools.AgentTool;
import fr.baretto.ollamassist.agent.tools.SecretDetector;
import fr.baretto.ollamassist.agent.tools.ToolApprovalHelper;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public final class WriteFileTool implements AgentTool {

    private final Project project;
    private final ToolApprovalHelper approvalHelper;
    private final PsiSyntaxValidator syntaxValidator;

    public WriteFileTool(Project project) {
        this(project, PsiSyntaxValidator.forProject(project));
    }

    @org.jetbrains.annotations.TestOnly
    WriteFileTool(Project project, PsiSyntaxValidator syntaxValidator) {
        this.project = project;
        this.approvalHelper = new ToolApprovalHelper(project);
        this.syntaxValidator = syntaxValidator;
    }

    @Override
    public String toolId() {
        return "FILE_WRITE";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String path = (String) params.get("path");
        String content = (String) params.get("content");

        if (path == null || path.isBlank()) {
            return ToolResult.failure("Parameter 'path' is required");
        }
        if (path.contains("<<")) {
            return ToolResult.failure(
                    "Parameter 'path' contains an unresolved placeholder: '" + path
                    + "'. Use <<var.NAME>> and ensure the step declaring outputVar:\"NAME\" precedes this one.");
        }
        if (content == null) {
            content = "";
        }

        // Deterministic fallback: if the LLM omitted the source-root prefix for a JVM source
        // file (e.g. "com/example/Foo.java" instead of "src/main/java/com/example/Foo.java"),
        // correct it automatically before resolving. The prompt is the primary guard; this
        // is the safety net.
        final String resolvedPath = SourceRootResolver.correctWritePath(path, content, project);
        final String finalContent = content;

        final Path absolutePath;
        try {
            absolutePath = FilePathGuard.resolveConfined(resolvedPath, project);
        } catch (FilePathGuard.PathTraversalException e) {
            log.warn("Path traversal attempt blocked: {}", e.getMessage());
            return ToolResult.failure(e.getMessage());
        } catch (IllegalStateException e) {
            return ToolResult.failure(e.getMessage());
        }
        VirtualFile existing = LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath.toString());
        if (existing != null && existing.exists()) {
            return ToolResult.failure("File already exists: " + resolvedPath + ". Use FILE_EDIT to modify it.");
        }

        String secretLabel = SecretDetector.detect(finalContent);
        if (secretLabel != null) {
            log.warn("Blocked FILE_WRITE to '{}': possible secret in content ({})", resolvedPath, secretLabel);
            return ToolResult.failure(
                    "File write blocked: the proposed content appears to contain a secret (" + secretLabel + "). "
                    + "Remove the secret before writing, or add '// ollamassist-nocheck' on the line if it is a test placeholder.");
        }

        String fileName = absolutePath.getFileName().toString();
        Optional<String> syntaxError = syntaxValidator.validate(fileName, finalContent);
        if (syntaxError.isPresent()) {
            return ToolResult.failure(
                    "Syntax error in the proposed content — file not created.\n"
                    + "Error: " + syntaxError.get() + "\n"
                    + "Fix the 'content' param so the file is syntactically valid.");
        }

        var decision = approvalHelper.requestApproval(
                "Create file?",
                resolvedPath,
                finalContent
        );
        if (!decision.approved()) {
            return ToolResult.failure(decision.toRejectionMessage("User rejected file creation: " + resolvedPath));
        }

        String groupId = (String) params.get("__correlationId");
        AtomicReference<ToolResult> result = new AtomicReference<>();
        try {
            WriteCommandAction.runWriteCommandAction(project, "Agent: create " + resolvedPath, groupId, () -> {
                try {
                    VirtualFile parentDir = getOrCreateParent(absolutePath.getParent());
                    if (parentDir == null) {
                        result.set(ToolResult.failure("Could not create parent directory for: " + resolvedPath));
                        return;
                    }
                    VirtualFile newFile = parentDir.createChildData(this, absolutePath.getFileName().toString());
                    newFile.setBinaryContent(finalContent.getBytes(StandardCharsets.UTF_8));
                    newFile.refresh(false, false);
                    log.info("File created: {}", resolvedPath);
                    result.set(ToolResult.success("File created: " + resolvedPath));
                } catch (IOException e) {
                    log.error("Failed to write file: {}", resolvedPath, e);
                    result.set(ToolResult.failure("Failed to write file: " + e.getMessage()));
                }
            });
        } catch (Exception e) {
            log.error("WriteCommandAction failed for: {}", resolvedPath, e);
            return ToolResult.failure("Write action failed: " + e.getMessage());
        }
        return result.get() != null ? result.get() : ToolResult.failure("Write command action produced no result");
    }

    private VirtualFile getOrCreateParent(Path parentPath) throws IOException {
        VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByPath(parentPath.toString());
        if (dir != null && dir.exists()) {
            return dir;
        }
        // Recurse to ensure grandparent exists
        if (parentPath.getParent() != null) {
            VirtualFile grandParent = getOrCreateParent(parentPath.getParent());
            if (grandParent != null) {
                return grandParent.createChildDirectory(this, parentPath.getFileName().toString());
            }
        }
        return null;
    }

}
