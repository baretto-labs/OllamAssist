package fr.baretto.ollamassist.agent.tools.files;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import fr.baretto.ollamassist.agent.tools.AgentTool;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Edits an existing file using the JetBrains {@link Document} API.
 *
 * <p>Two operations — both language-agnostic, both undoable via Ctrl+Z:
 * <ul>
 *   <li><b>insertAfterLine(line, code)</b> — inserts {@code code} on a new line after line N (1-indexed).</li>
 *   <li><b>replaceLines(startLine, endLine, code)</b> — replaces lines N through M with {@code code}.</li>
 * </ul>
 *
 * <p>No text pattern matching — line numbers are absolute as shown in the planning context.
 */
@Slf4j
public final class LineEditTool implements AgentTool {

    private final Project project;

    public LineEditTool(Project project) {
        this.project = project;
    }

    @Override
    public String toolId() { return "LINE_EDIT"; }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String path      = (String) params.get("path");
        String operation = (String) params.get("operation");
        String code      = (String) params.get("code");

        if (path == null || path.isBlank())
            return ToolResult.failure("Parameter 'path' is required");
        if (operation == null || operation.isBlank())
            return ToolResult.failure("Parameter 'operation' is required (insertAfterLine or replaceLines)");
        if (code == null)
            return ToolResult.failure("Parameter 'code' is required");

        final Path absolutePath;
        final VirtualFile file;
        try {
            Path resolved = FilePathGuard.resolveConfined(path, project);
            file = LocalFileSystem.getInstance().refreshAndFindFileByPath(resolved.toString());
            if (file == null || !file.exists()) {
                String corrected = SourceRootResolver.findInSourceRoots(path, project);
                if (corrected != null) {
                    Path cp = FilePathGuard.resolveConfined(corrected, project);
                    VirtualFile cf = LocalFileSystem.getInstance().refreshAndFindFileByPath(cp.toString());
                    if (cf != null && cf.exists())
                        return executeOnFile(cf, cp.toString(), operation, params, code);
                }
                return ToolResult.failure("File not found: " + path);
            }
            absolutePath = resolved;
        } catch (FilePathGuard.PathTraversalException e) {
            return ToolResult.failure(e.getMessage());
        } catch (IllegalStateException e) {
            return ToolResult.failure(e.getMessage());
        }

        return executeOnFile(file, absolutePath.toString(), operation, params, code);
    }

    private ToolResult executeOnFile(VirtualFile file, String displayPath,
                                     String operation, Map<String, Object> params, String code) {
        String correlationId = (String) params.get("__correlationId");
        AtomicReference<ToolResult> result = new AtomicReference<>();

        WriteCommandAction.runWriteCommandAction(project, "Agent: edit " + displayPath, correlationId, () -> {
            Document doc = FileDocumentManager.getInstance().getDocument(file);
            if (doc == null) {
                result.set(ToolResult.failure("Cannot get document for: " + displayPath));
                return;
            }

            try {
                applyOperation(doc, operation, params, code);
                PsiDocumentManager.getInstance(project).commitDocument(doc);
                FileDocumentManager.getInstance().saveDocument(doc);
                file.refresh(false, false);
                log.info("LineEdit applied ({}) to: {}", operation, displayPath);
                result.set(ToolResult.success("File edited: " + displayPath + " [" + operation + "]"));
            } catch (Exception e) {
                log.error("LineEdit failed for {}: {}", displayPath, e.getMessage(), e);
                result.set(ToolResult.failure("Edit failed: " + e.getMessage()));
            }
        });

        return result.get() != null ? result.get()
                : ToolResult.failure("WriteCommandAction produced no result");
    }

    // -------------------------------------------------------------------------

    private static void applyOperation(Document doc, String operation,
                                       Map<String, Object> params, String code) {
        int lineCount = doc.getLineCount();

        switch (operation) {
            case "insertAfterLine" -> {
                int line = toInt(params.get("line")); // 1-indexed
                if (line < 1 || line > lineCount)
                    throw new IllegalArgumentException(
                            "line " + line + " out of range (file has " + lineCount + " lines)");
                int offset = doc.getLineEndOffset(line - 1);
                // Normalise: remove leading newline from code if we're adding one ourselves
                String insert = "\n" + stripLeadingNewline(code);
                doc.insertString(offset, insert);
            }
            case "replaceLines" -> {
                int start = toInt(params.get("startLine")); // 1-indexed, inclusive
                int end   = toInt(params.get("endLine"));   // 1-indexed, inclusive
                if (start < 1 || end < start || end > lineCount)
                    throw new IllegalArgumentException(
                            "replaceLines range " + start + "-" + end
                            + " invalid (file has " + lineCount + " lines)");
                int startOffset = doc.getLineStartOffset(start - 1);
                int endOffset   = doc.getLineEndOffset(end - 1);
                doc.replaceString(startOffset, endOffset, code.stripTrailing());
            }
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        }
    }

    private static String stripLeadingNewline(String s) {
        if (s.startsWith("\n"))  return s.substring(1);
        if (s.startsWith("\r\n")) return s.substring(2);
        return s;
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) { try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { return 0; } }
        return 0;
    }
}
