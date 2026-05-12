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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public final class EditFileTool implements AgentTool {

    private final Project project;
    private final ToolApprovalHelper approvalHelper;
    private final PsiSyntaxValidator syntaxValidator;

    public EditFileTool(Project project) {
        this(project, PsiSyntaxValidator.forProject(project));
    }

    @org.jetbrains.annotations.TestOnly
    EditFileTool(Project project, PsiSyntaxValidator syntaxValidator) {
        this.project = project;
        this.approvalHelper = new ToolApprovalHelper(project);
        this.syntaxValidator = syntaxValidator;
    }

    @Override
    public String toolId() {
        return "FILE_EDIT";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String path = (String) params.get("path");
        String search = (String) params.get("search");
        String replace = (String) params.get("replace");
        boolean replaceAll = Boolean.TRUE.equals(params.get("replaceAll"));
        boolean normalizeWhitespace = Boolean.TRUE.equals(params.get("normalizeWhitespace"));

        if (path == null || path.isBlank()) {
            return ToolResult.failure("Parameter 'path' is required");
        }
        if (path.contains("<<")) {
            return ToolResult.failure(
                    "Parameter 'path' contains an unresolved placeholder: '" + path
                    + "'. Use <<var.NAME>> and ensure the step declaring outputVar:\"NAME\" precedes this one.");
        }
        if (search == null) {
            return ToolResult.failure("Parameter 'search' is required");
        }
        if (replace == null) {
            return ToolResult.failure("Parameter 'replace' is required");
        }
        String secretLabel = SecretDetector.detect(replace);
        if (secretLabel != null) {
            log.warn("Blocked FILE_EDIT to '{}': possible secret in replace param ({})", path, secretLabel);
            return ToolResult.failure(
                    "File edit blocked: the 'replace' content appears to contain a secret (" + secretLabel + "). "
                    + "Remove the secret before editing, or add '// ollamassist-nocheck' on the line if it is a test placeholder.");
        }

        final VirtualFile file;
        try {
            Path resolved = FilePathGuard.resolveConfined(path, project);
            VirtualFile found = LocalFileSystem.getInstance().refreshAndFindFileByPath(resolved.toString());

            if (found == null || !found.exists()) {
                // Deterministic fallback: if the LLM gave a bare package path (without source-root
                // prefix), search all source roots before giving up.
                String corrected = SourceRootResolver.findInSourceRoots(path, project);
                if (corrected != null) {
                    try {
                        Path correctedPath = FilePathGuard.resolveConfined(corrected, project);
                        VirtualFile correctedFile = LocalFileSystem.getInstance()
                                .refreshAndFindFileByPath(correctedPath.toString());
                        if (correctedFile != null && correctedFile.exists()) {
                            resolved = correctedPath;
                            found = correctedFile;
                        }
                    } catch (FilePathGuard.PathTraversalException | IllegalStateException ignored) {
                        // keep original
                    }
                }
            }
            if (found == null || !found.exists()) {
                return ToolResult.failure("File not found: " + path);
            }
            file = found;
        } catch (FilePathGuard.PathTraversalException e) {
            log.warn("Path traversal attempt blocked: {}", e.getMessage());
            return ToolResult.failure(e.getMessage());
        } catch (IllegalStateException e) {
            return ToolResult.failure(e.getMessage());
        }

        try {
            byte[] rawBytes = file.contentsToByteArray();
            // Detect encoding so edits on Latin-1/ISO-8859-1 files don't corrupt content (Q-1).
            Charset charset = detectCharset(rawBytes);
            String original = new String(rawBytes, charset);

            // Effective search string after optional whitespace normalisation.
            // When normalizeWhitespace=true, collapse all whitespace runs to a single space
            // in both the file content and the search param before comparing.
            // The replacement is always applied to the ORIGINAL content to preserve formatting.
            String effectiveSearch = search;
            String effectiveOriginal = original;
            if (normalizeWhitespace) {
                effectiveSearch  = search.replaceAll("\\s+", " ").strip();
                effectiveOriginal = original.replaceAll("\\s+", " ");
            }

            if (!effectiveOriginal.contains(effectiveSearch)) {
                // Auto-retry with whitespace normalisation before giving up.
                // Models often produce search strings with minor whitespace differences
                // (tabs vs spaces, trailing spaces, CRLF vs LF) even after reading the file.
                if (!normalizeWhitespace) {
                    String normSearch   = search.replaceAll("\\s+", " ").strip();
                    String normOriginal = original.replaceAll("\\s+", " ");
                    if (normOriginal.contains(normSearch)) {
                        // Whitespace mismatch — map back to actual substring and continue
                        String actualSearch = findActualSubstring(original, normSearch);
                        if (actualSearch != null) {
                            effectiveSearch  = actualSearch;
                            effectiveOriginal = original;
                            log.debug("EditFileTool: whitespace-normalised fallback matched for '{}'", path);
                            // fall through to the replacement logic below
                        } else {
                            effectiveSearch  = normSearch;
                            effectiveOriginal = normOriginal;
                        }
                    } else {
                        String preview = original.length() > 3000
                                ? original.substring(0, 1800) + "\n...[truncated]...\n"
                                  + original.substring(original.length() - 1200)
                                : original;
                        return ToolResult.failure(
                                "Search string not found in file: " + path + "\n"
                                + "The 'search' param must match the file content exactly. "
                                + "Whitespace-normalised retry also failed.\n"
                                + "Actual file content:\n" + preview);
                    }
                } else {
                    String preview = original.length() > 3000
                            ? original.substring(0, 1800) + "\n...[truncated]...\n"
                              + original.substring(original.length() - 1200)
                            : original;
                    return ToolResult.failure(
                            "Search string not found in file: " + path + "\n"
                            + "The 'search' param must match the file content exactly "
                            + "(normalizeWhitespace=true was applied but still no match).\n"
                            + "Actual file content:\n" + preview);
                }
            }

            // When normalizeWhitespace was requested, map the normalised match back to the actual
            // substring in original. When !normalizeWhitespace, effectiveSearch is already correct:
            // either the original search string (direct match) or the actual substring resolved by
            // the auto-whitespace-fallback block above.
            if (normalizeWhitespace) {
                String actualSearch = findActualSubstring(original, effectiveSearch);
                if (actualSearch != null) {
                    effectiveSearch = actualSearch;
                } else {
                    effectiveSearch = search;
                    effectiveOriginal = original;
                }
            }

            search = effectiveSearch;
            if (!original.contains(search)) {
                return ToolResult.failure("Search string could not be mapped back to original content after whitespace normalisation.");
            }

            int occurrences = countOccurrences(original, search);
            String modified = replaceAll
                    ? original.replace(search, replace)
                    : replaceFirstOccurrence(original, search, replace);

            Optional<String> syntaxError = syntaxValidator.validate(file.getName(), modified);
            if (syntaxError.isPresent()) {
                return ToolResult.failure(
                        "Syntax error in the proposed edit — file not modified.\n"
                        + "Error: " + syntaxError.get() + "\n"
                        + "Fix the 'search' / 'replace' params so the resulting file is syntactically valid.");
            }

            final String finalModified = modified;

            String diff = buildDiff(path, search, replace, occurrences, replaceAll);
            var decision = approvalHelper.requestApproval(
                    "Edit file?",
                    path,
                    diff
            );
            if (!decision.approved()) {
                return ToolResult.failure(decision.toRejectionMessage("User rejected file edit: " + path));
            }

            final Charset writeCharset = charset;
            String groupId = (String) params.get("__correlationId");
            AtomicReference<ToolResult> result = new AtomicReference<>();
            WriteCommandAction.runWriteCommandAction(project, "Agent: edit " + path, groupId, () -> {
                try {
                    file.setBinaryContent(finalModified.getBytes(writeCharset));
                    file.refresh(false, false);
                    log.info("File edited: {}", path);
                    result.set(ToolResult.success("File edited: " + path));
                } catch (IOException e) {
                    log.error("Failed to write edited file: {}", path, e);
                    result.set(ToolResult.failure("Failed to save edit: " + e.getMessage()));
                }
            });
            return result.get() != null ? result.get() : ToolResult.failure("Write command action produced no result");
        } catch (IOException e) {
            log.error("Failed to read file for editing: {}", path, e);
            return ToolResult.failure("Failed to read file: " + e.getMessage());
        } catch (Exception e) {
            log.error("WriteCommandAction failed for: {}", path, e);
            return ToolResult.failure("Write action failed: " + e.getMessage());
        }
    }

    static String buildDiff(String path, String search, String replace, int occurrences, boolean replaceAll) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(path).append("\n");
        sb.append("+++ ").append(path).append("\n");
        if (occurrences > 1) {
            String note = replaceAll
                    ? "!! " + occurrences + " occurrences — ALL will be replaced"
                    : "## " + occurrences + " occurrences — only FIRST replaced (replaceAll=false)";
            sb.append(note).append("\n");
        }
        sb.append("@@ search → replace @@\n");
        // Prefix each line of the search block with "- " and each line of replace with "+ "
        for (String line : truncateText(search, 800).split("\n", -1)) {
            sb.append("- ").append(line).append("\n");
        }
        sb.append("\\ No newline indicator\n");
        for (String line : truncateText(replace, 800).split("\n", -1)) {
            sb.append("+ ").append(line).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private static String truncateText(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        int head = maxChars * 6 / 10;
        int tail = maxChars - head;
        return text.substring(0, head)
                + "\n... [" + (text.length() - maxChars) + " chars omitted] ...\n"
                + text.substring(text.length() - tail);
    }

    /**
     * Finds the actual substring in {@code original} that corresponds to {@code normalizedSearch}
     * (already whitespace-collapsed). Scans {@code original} character by character, skipping
     * whitespace runs when the normalised pattern expects a single space.
     * Returns {@code null} if no match is found.
     */
    static String findActualSubstring(String original, String normalizedSearch) {
        if (normalizedSearch == null || normalizedSearch.isEmpty()) return null;
        String[] tokens = normalizedSearch.split(" ", -1);
        for (int start = 0; start < original.length(); start++) {
            int pos = start;
            boolean matched = true;
            for (int ti = 0; ti < tokens.length && matched; ti++) {
                String token = tokens[ti];
                if (ti > 0) {
                    // Skip at least one whitespace between tokens
                    if (pos >= original.length() || !Character.isWhitespace(original.charAt(pos))) { matched = false; break; }
                    while (pos < original.length() && Character.isWhitespace(original.charAt(pos))) pos++;
                }
                if (pos + token.length() > original.length()) { matched = false; break; }
                if (!original.startsWith(token, pos)) { matched = false; break; }
                pos += token.length();
            }
            if (matched) return original.substring(start, pos);
        }
        return null;
    }

    private static String replaceFirstOccurrence(String original, String search, String replace) {
        int idx = original.indexOf(search);
        if (idx < 0) return original;
        return original.substring(0, idx) + replace + original.substring(idx + search.length());
    }

    private static int countOccurrences(String text, String search) {
        if (search.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(search, idx)) != -1) {
            count++;
            idx += search.length();
        }
        return count;
    }

    /**
     * Returns the charset to use for reading and writing {@code bytes}.
     *
     * <p>Detection order:
     * <ol>
     *   <li>Strict UTF-8 decode — succeeds for UTF-8 with or without BOM.</li>
     *   <li>Windows-1252 — when bytes 0x80–0x9F are present. ISO-8859-1 maps those
     *       bytes to control characters, silently corrupting € " " etc. on round-trip.</li>
     *   <li>ISO-8859-1 — safe fallback for plain Latin-1 files.</li>
     * </ol>
     */
    static Charset detectCharset(byte[] bytes) {
        java.nio.charset.CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        try {
            utf8.decode(java.nio.ByteBuffer.wrap(bytes));
            return StandardCharsets.UTF_8;
        } catch (java.nio.charset.CharacterCodingException e) {
            // Windows-1252 defines printable characters for bytes 0x80–0x9F (€, ", " etc.)
            // whereas ISO-8859-1 leaves them as C1 control codes. Using ISO-8859-1 for a
            // Windows-1252 file maps those bytes to the wrong Unicode code points, so a
            // search-replace on e.g. "€" would fail with "Search string not found" (Q-1).
            for (byte b : bytes) {
                int u = b & 0xFF;
                if (u >= 0x80 && u <= 0x9F) {
                    return Charset.forName("windows-1252");
                }
            }
            return StandardCharsets.ISO_8859_1;
        }
    }
}
