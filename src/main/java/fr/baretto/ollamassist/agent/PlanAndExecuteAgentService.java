package fr.baretto.ollamassist.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import fr.baretto.ollamassist.agent.tools.ToolApprovalHelper;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import fr.baretto.ollamassist.agent.tools.files.AppendFileTool;
import fr.baretto.ollamassist.agent.tools.files.DeleteFileTool;
import fr.baretto.ollamassist.agent.tools.files.LineEditTool;
import fr.baretto.ollamassist.agent.tools.files.ReadFileTool;
import fr.baretto.ollamassist.agent.tools.files.SourceRootResolver;
import fr.baretto.ollamassist.agent.tools.files.WriteFileTool;
import fr.baretto.ollamassist.agent.tools.navigation.SearchCodeTool;
import fr.baretto.ollamassist.auth.AuthenticationHelper;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import fr.baretto.ollamassist.setting.OllamaSettings;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Plan-then-Execute agent service.
 *
 * <p>Three deterministic phases per execution:
 * <ol>
 *   <li><b>Discover</b> — extracts keywords from the goal, searches the workspace, reads
 *       the most relevant files (max {@value #MAX_DISCOVERY_FILES}, {@value #MAX_CONTEXT_CHARS} chars total).</li>
 *   <li><b>Plan</b>  — single LLM call that receives the goal + discovered file contents and
 *       produces a JSON array of concrete steps (writeFile / editFile only).</li>
 *   <li><b>Execute</b> — Java applies each step deterministically using the existing tool
 *       implementations. Any failure stops execution immediately.</li>
 * </ol>
 *
 * <p>No open-ended loop. No loop detection needed. The model decides WHAT to do once,
 * and Java executes it reliably.
 */
@Service(Service.Level.PROJECT)
@Slf4j
public final class PlanAndExecuteAgentService implements Disposable {

    static final int MAX_DISCOVERY_FILES  = 4;
    static final int MAX_CONTEXT_CHARS    = 20_000;
    static final int FRAGMENT_CONTEXT_LINES = 20;   // lines before/after the match
    static final int WHOLE_FILE_MAX_LINES   = 150;  // show full file below this threshold

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MSG_CANCELLED  = "Agent execution cancelled.\n";
    private static final String ELLIPSIS_LINE  = "...\n";
    private static final String TOOL_READ_FILE = "readFile";
    private static final String TOOL_EDIT_FILE = "editFile";

    private static final String SYSTEM_PROMPT = """
            You are an expert software engineer assistant embedded in a JetBrains IDE.

            Analyse the goal and choose ONE of the two output formats below — never mix them.

            ── FORMAT A — Conversational / informational ──────────────────────────────────
            Use this when the goal is a question, an explanation request, or anything that \
            does NOT require creating or modifying files.
            Output a JSON array with a single "answer" step:
              [{"tool":"answer","content":"your response here"}]

            ── FORMAT B — File actions ────────────────────────────────────────────────────
            Use this when the goal requires creating or modifying project files.
            Output a JSON array where each step is one of:

            Create a NEW file (does not exist yet):
              {"tool":"writeFile","path":"src/main/java/...","content":"..."}

            Insert code after a specific line in an EXISTING file:
              {"tool":"editFile","path":"...","operation":"insertAfterLine","line":N,"code":"..."}
              → Inserts "code" on a new line immediately after line N (1-indexed as shown).
              → To add a method at the end of a class whose last line is N: use line N-1.

            Replace a range of lines in an EXISTING file:
              {"tool":"editFile","path":"...","operation":"replaceLines","startLine":N,"endLine":M,"code":"..."}
              → Replaces lines N through M (inclusive) with "code".

            Delete an EXISTING file permanently:
              {"tool":"deleteFile","path":"src/main/java/..."}
              → Use only when the goal explicitly requires removing a file.

            Append content to the end of an EXISTING file without touching the rest:
              {"tool":"appendFile","path":"src/main/java/...","content":"..."}
              → Use to add entries (e.g. log lines, config blocks) without replacing anything.

            Rules for editFile:
            - Line numbers are 1-indexed exactly as shown in the file context below.
            - "code" must NOT repeat surrounding braces that already exist in the file.
            - Indentation must match the surrounding code.
            - For inserting a new method inside a class, insert BEFORE the final closing \
              brace of the class — i.e., insertAfterLine on the line just before the last "}" \
              that closes the class.

            Rules for writeFile:
            - Only when the file does NOT appear in the provided file context.
            - Include the complete, syntactically valid file content.

            Rules for paths:
            - Always use the full path relative to the project root.
            - Use the provided source roots as path prefixes.
            - Never invent a path.

            Output ONLY the JSON array. No explanation, no markdown fences, no extra text.
            """;

    private final Project project;
    private final ReadFileTool   readFileTool;
    private final WriteFileTool  writeFileTool;
    private final LineEditTool   lineEditTool;
    private final DeleteFileTool deleteFileTool;
    private final AppendFileTool appendFileTool;
    private final SearchCodeTool searchCodeTool;
    private volatile boolean disposed = false;
    private volatile boolean executionCancelled = false;

    @org.jetbrains.annotations.Nullable
    private final StreamingChatModel overrideModel;

    public PlanAndExecuteAgentService(@NotNull Project project) {
        this.project        = project;
        this.readFileTool   = new ReadFileTool(project);
        this.writeFileTool  = new WriteFileTool(project);
        this.lineEditTool   = new LineEditTool(project);
        this.deleteFileTool = new DeleteFileTool(project);
        this.appendFileTool = new AppendFileTool(project);
        this.searchCodeTool = new SearchCodeTool(project);
        this.overrideModel  = null;
    }

    @TestOnly
    PlanAndExecuteAgentService(Project project,
                               ReadFileTool readFileTool,
                               WriteFileTool writeFileTool,
                               LineEditTool lineEditTool,
                               SearchCodeTool searchCodeTool,
                               StreamingChatModel overrideModel) {
        this.project        = project;
        this.readFileTool   = readFileTool;
        this.writeFileTool  = writeFileTool;
        this.lineEditTool   = lineEditTool;
        this.deleteFileTool = new DeleteFileTool(project);
        this.appendFileTool = new AppendFileTool(project);
        this.searchCodeTool = searchCodeTool;
        this.overrideModel  = overrideModel;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void execute(@NotNull String goal, @NotNull AgentStreamHandler handler) {
        execute(goal, List.of(), handler);
    }

    /**
     * Starts a plan-and-execute run. Files in {@code userContext} are injected at the top of
     * the discovery result so the planner always sees them, regardless of keyword matching.
     */
    public void execute(@NotNull String goal,
                        @NotNull List<java.io.File> userContext,
                        @NotNull AgentStreamHandler handler) {
        if (disposed) {
            handler.onError(new IllegalStateException("PlanAndExecuteAgentService has been disposed"));
            return;
        }
        executionCancelled = false;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                executeInternal(goal, userContext, handler);
            } catch (Exception e) {
                log.error("Plan-and-execute agent failed: {}", e.getMessage(), e);
                handler.onError(e);
            }
        });
    }

    /** Cancels the current execution as soon as the active phase completes. */
    public void cancel() {
        executionCancelled = true;
    }

    // -------------------------------------------------------------------------
    // Phase orchestration
    // -------------------------------------------------------------------------

    private void executeInternal(String goal, List<java.io.File> userContext, AgentStreamHandler handler) {
        // Phase 1 — Discover
        handler.onToken("Discovering relevant files...\n");
        DiscoveryResult discovery = discoverContext(goal, userContext, handler);

        if (executionCancelled) {
            handler.onToken(MSG_CANCELLED);
            handler.onComplete();
            return;
        }

        // Phase 2 — Plan
        handler.onToken("Generating plan...\n");
        List<AgentStep> steps = generatePlan(goal, discovery, handler);
        if (steps.isEmpty()) {
            handler.onComplete();
            return;
        }

        // Conversational answer — display and exit without approval
        if (steps.size() == 1 && "answer".equals(steps.get(0).tool())) {
            String answer = steps.get(0).content();
            if (answer != null && !answer.isBlank()) {
                handler.onToken(answer + "\n");
            }
            handler.onComplete();
            return;
        }

        if (executionCancelled) {
            handler.onToken(MSG_CANCELLED);
            handler.onComplete();
            return;
        }

        // Show what will happen
        String summary = buildSummary(steps);
        handler.onToken("\nPlan: " + summary + "\n");

        // Request one approval for the whole plan (not per step)
        if (!requestPlanApproval(steps, handler)) return;

        // Phase 3 — Execute
        executeSteps(steps, handler);
    }

    /**
     * Asks the user to approve the full plan before any file is touched.
     * In AUTO mode, approval is immediate.
     * In MANUAL mode, shows a single approval dialog for the whole plan —
     * no per-step dialogs, so the user is never surprised mid-execution.
     *
     * @return {@code true} if execution should proceed, {@code false} if cancelled
     */
    private boolean requestPlanApproval(List<AgentStep> steps, AgentStreamHandler handler) {
        if (fr.baretto.ollamassist.setting.OllamaSettings.getInstance().isAgentFileApprovalAuto()) {
            return true;
        }

        // Build a human-readable summary of every step
        StringBuilder details = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            AgentStep s = steps.get(i);
            details.append("Step ").append(i + 1).append(": ").append(s.tool())
                   .append(" → ").append(s.path() != null ? s.path() : "?");
            if (s.operation() != null) details.append(" [").append(s.operation()).append("]");
            if (s.line() != 0) details.append(" after line ").append(s.line());
            if (s.startLine() != 0) details.append(" lines ").append(s.startLine()).append("–").append(s.endLine());
            details.append("\n");
            if (s.code() != null) {
                details.append(s.code()).append("\n");
            }
            if (s.content() != null) {
                details.append(s.content()).append("\n");
            }
            details.append("\n");
        }

        handler.onToken("Waiting for your approval (see below)...\n");

        var decision = new ToolApprovalHelper(project)
                .requestApproval("Execute plan?", "agent-plan", details.toString().stripTrailing());

        if (!decision.approved()) {
            String reason = decision.rejectionReason() != null ? ": " + decision.rejectionReason() : "";
            handler.onToken("Plan cancelled" + reason + "\n");
            handler.onComplete();
            return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Phase 1 — Discover
    // -------------------------------------------------------------------------

    private DiscoveryResult discoverContext(String goal,
                                             List<java.io.File> userContext,
                                             AgentStreamHandler handler) {
        Map<String, String> fileContents = new LinkedHashMap<>();
        Set<String> truncatedPaths = new HashSet<>();
        int[] totalChars = {0};
        Set<String> visitedPaths = new HashSet<>();

        // Inject user-selected files first — guaranteed to appear in the planning context
        String basePath = project.getBasePath();
        for (java.io.File file : userContext) {
            if (totalChars[0] >= MAX_CONTEXT_CHARS) break;
            try {
                String content = java.nio.file.Files.readString(file.toPath());
                String relPath = (basePath != null && file.getAbsolutePath().startsWith(basePath + java.io.File.separator))
                        ? file.getAbsolutePath().substring(basePath.length() + 1)
                        : file.getAbsolutePath();
                if (isInternalFile(relPath)) continue;
                handler.onToolCall(TOOL_READ_FILE, "{\"path\":\"" + escapeJson(relPath) + "\"}");
                int remaining = MAX_CONTEXT_CHARS - totalChars[0];
                String trimmed = content.length() > remaining ? content.substring(0, remaining) : content;
                if (content.length() > remaining) truncatedPaths.add(relPath);
                fileContents.put(relPath, trimmed);
                visitedPaths.add(relPath);
                totalChars[0] += trimmed.length();
            } catch (Exception e) {
                log.debug("Could not read user-selected file {}: {}", file, e.getMessage());
            }
        }

        List<String> keywords = extractKeywords(goal);

        for (String keyword : keywords) {
            if (fileContents.size() >= MAX_DISCOVERY_FILES || totalChars[0] >= MAX_CONTEXT_CHARS) break;

            handler.onToolCall("searchWorkspace", "{\"query\":\"" + escapeJson(keyword) + "\"}");

            // Primary: JetBrains word index — finds the keyword in any code, config, or string
            List<CodeFragment> fragments = findFragmentsWithPsi(keyword, visitedPaths);

            // Fallback: text-based grep (covers files not indexed by PSI)
            if (fragments.isEmpty()) {
                fragments = findFragmentsWithTextSearch(keyword, visitedPaths);
            }

            for (CodeFragment fragment : fragments) {
                if (fileContents.size() >= MAX_DISCOVERY_FILES || totalChars[0] >= MAX_CONTEXT_CHARS) break;
                visitedPaths.add(fragment.relativePath());

                handler.onToolCall(TOOL_READ_FILE, "{\"path\":\"" + escapeJson(fragment.relativePath()) + "\"}");

                int remaining = MAX_CONTEXT_CHARS - totalChars[0];
                String content = fragment.content().length() > remaining
                        ? fragment.content().substring(0, remaining)
                        : fragment.content();

                if (content.length() < fragment.content().length()) {
                    truncatedPaths.add(fragment.relativePath());
                }

                // Merge if another keyword already added a fragment from this file
                fileContents.merge(fragment.relativePath(), content,
                        (existing, newFrag) -> existing + "\n...\n" + newFrag);
                totalChars[0] += content.length();
            }
        }

        return new DiscoveryResult(fileContents, truncatedPaths);
    }

    /**
     * Uses JetBrains' word index ({@link PsiSearchHelper}) to find all project files
     * that contain {@code keyword} as a word — in source code, string literals, comments,
     * or configuration files — and extracts a relevant code fragment around each match.
     *
     * <p>This is the preferred search strategy: it is instant (index-backed), exact
     * (word-boundary aware), and language-agnostic (covers every file type JetBrains indexes).
     */
    private List<CodeFragment> findFragmentsWithPsi(String keyword, Set<String> alreadyVisited) {
        String basePath = project.getBasePath();
        if (basePath == null) return List.of();

        try {
            return ReadAction.compute(() -> {
                PsiSearchHelper helper = PsiSearchHelper.getInstance(project);
                GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

                Map<String, CodeFragment> result = new LinkedHashMap<>();

                helper.processAllFilesWithWord(keyword, scope, psiFile -> {
                            if (result.size() >= MAX_DISCOVERY_FILES) return false;

                            VirtualFile vf = psiFile.getVirtualFile();
                            if (vf == null) return true;

                            String abs = vf.getPath();
                            String rel = abs.startsWith(basePath + "/") ? abs.substring(basePath.length() + 1)
                                    : abs.startsWith(basePath)          ? abs.substring(basePath.length())
                                    : abs;

                            if (isInternalFile(rel)) return true;
                            if (alreadyVisited.contains(rel) || result.containsKey(rel)) return true;

                            String fragment = extractFragment(psiFile.getText(), keyword);
                            if (!fragment.isBlank()) {
                                result.put(rel, new CodeFragment(rel, fragment));
                            }
                            return true;
                        }, false /* case-insensitive for broader matches */);

                return new ArrayList<>(result.values());
            });
        } catch (Exception e) {
            log.debug("PSI search unavailable for '{}': {}", keyword, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fallback text search via {@link SearchCodeTool} (grep-based).
     * Used when the PSI index has no results (e.g., file type not indexed, project not yet indexed).
     */
    private List<CodeFragment> findFragmentsWithTextSearch(String keyword, Set<String> alreadyVisited) {
        ToolResult searchResult = searchCodeTool.execute(Map.of("query", keyword));
        if (!searchResult.isSuccess()) return List.of();

        List<CodeFragment> result = new ArrayList<>();
        for (String path : extractFilePaths(searchResult.getOutput())) {
            if (alreadyVisited.contains(path)) continue;
            ToolResult read = readFileTool.execute(Map.of("path", path));
            if (!read.isSuccess()) continue;
            String fragment = extractFragment(read.getOutput(), keyword);
            if (!fragment.isBlank()) {
                result.add(new CodeFragment(path, fragment));
            }
        }
        return result;
    }

    /**
     * Extracts a relevant code fragment around occurrences of {@code keyword} in {@code fileContent}.
     *
     * <ul>
     *   <li>Files ≤ {@value #WHOLE_FILE_MAX_LINES} lines: returned in full.</li>
     *   <li>Larger files: up to 3 non-overlapping windows of ±{@value #FRAGMENT_CONTEXT_LINES}
     *       lines around each occurrence, separated by {@code ...}.</li>
     * </ul>
     */
    static String extractFragment(String fileContent, String keyword) {
        if (fileContent == null || fileContent.isBlank() || keyword == null) return "";

        String[] lines = fileContent.split("\n", -1);
        if (lines.length <= WHOLE_FILE_MAX_LINES) return fileContent;

        String lower = fileContent.toLowerCase();
        String lowerKw = keyword.toLowerCase();

        // Collect up to 3 distinct match line numbers (non-overlapping windows)
        List<Integer> matchLines = new ArrayList<>();
        int searchFrom = 0;
        while (searchFrom < fileContent.length() && matchLines.size() < 3) {
            int idx = lower.indexOf(lowerKw, searchFrom);
            if (idx < 0) break;
            int lineNum = (int) fileContent.substring(0, idx).chars().filter(c -> c == '\n').count();
            boolean overlaps = !matchLines.isEmpty()
                    && lineNum - matchLines.get(matchLines.size() - 1) <= FRAGMENT_CONTEXT_LINES * 2;
            if (!overlaps) matchLines.add(lineNum);
            searchFrom = idx + lowerKw.length();
        }

        if (matchLines.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (int matchLine : matchLines) {
            int start = Math.max(0, matchLine - FRAGMENT_CONTEXT_LINES);
            int end   = Math.min(lines.length - 1, matchLine + FRAGMENT_CONTEXT_LINES);

            if (!sb.isEmpty()) sb.append("\n...\n");
            if (start > 0) sb.append(ELLIPSIS_LINE);
            for (int i = start; i <= end; i++) sb.append(lines[i]).append("\n");
            if (end < lines.length - 1) sb.append(ELLIPSIS_LINE);
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Phase 2 — Plan (single LLM call)
    // -------------------------------------------------------------------------

    private List<AgentStep> generatePlan(String goal, DiscoveryResult discovery,
                                          AgentStreamHandler handler) {
        String userMessage = buildPlanningMessage(goal, discovery.fileContents(), discovery.truncatedPaths());

        List<ChatMessage> messages = List.of(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from(userMessage)
        );

        StringBuilder buffer    = new StringBuilder();
        CountDownLatch latch    = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        StreamingChatModel model = overrideModel != null ? overrideModel : buildModel();

        withPluginClassLoader(() -> {
            model.chat(ChatRequest.builder().messages(messages).build(),
                    new StreamingChatResponseHandler() {
                        @Override public void onPartialResponse(String token) { buffer.append(token); }
                        @Override public void onCompleteResponse(ChatResponse r) { latch.countDown(); }
                        @Override public void onError(Throwable e) { error.set(e); latch.countDown(); }
                    });
            return null;
        });

        try {
            if (!latch.await(5, TimeUnit.MINUTES)) {
                handler.onToken("Planning timed out.\n");
                return List.of();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }

        if (error.get() != null) {
            log.error("Planning LLM call failed", error.get());
            handler.onToken("Planning failed: " + error.get().getMessage() + "\n");
            return List.of();
        }

        List<AgentStep> steps = parseSteps(buffer.toString());
        if (steps.isEmpty()) {
            log.warn("PlanAndExecute: could not parse plan from LLM response: {}", buffer);
        }
        return steps;
    }

    private String buildPlanningMessage(String goal, Map<String, String> fileContents,
                                         Set<String> truncatedPaths) {
        StringBuilder sb = new StringBuilder();
        sb.append("Goal: ").append(goal).append("\n\n");

        List<String> roots = SourceRootResolver.sourceRootRelativePaths(project);
        if (!roots.isEmpty()) {
            sb.append("Source roots: ").append(String.join(", ", roots)).append("\n\n");
        }

        if (!fileContents.isEmpty()) {
            sb.append("Project files (line numbers are 1-indexed and absolute):\n\n");
            for (Map.Entry<String, String> e : fileContents.entrySet()) {
                boolean truncated = truncatedPaths.contains(e.getKey());
                String[] lines = e.getValue().split("\n", -1);
                sb.append("=== ").append(e.getKey())
                  .append(truncated ? " [truncated — only first " + lines.length + " lines shown]" : "")
                  .append(" ===\n");
                for (int i = 0; i < lines.length; i++) {
                    sb.append(String.format("%4d | %s%n", i + 1, lines[i]));
                }
                sb.append("\n");
            }
        }

        sb.append("Produce the JSON array of steps.");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Execute
    // -------------------------------------------------------------------------

    private void executeSteps(List<AgentStep> steps, AgentStreamHandler handler) {
        for (int i = 0; i < steps.size(); i++) {
            AgentStep step = steps.get(i);
            if (disposed || executionCancelled) {
                handler.onToken(MSG_CANCELLED);
                handler.onComplete();
                return;
            }
            handler.onProgress(i + 1, steps.size());

            // For editFile, verify the path exists before showing the indicator.
            // If the LLM guessed a wrong path, search by filename and correct it.
            AgentStep resolved = TOOL_EDIT_FILE.equals(step.tool())
                    ? resolveEditFilePath(step, handler)
                    : step;

            if (resolved == null) {
                handler.onComplete();
                return;
            }

            handler.onToolCall(resolved.tool(), resolved.toArgsJson());
            ToolResult result = dispatchStep(resolved);

            if (!result.isSuccess()) {
                // Syntax errors are self-correctable: re-ask the LLM to fix only this step.
                if (isSyntaxError(result) && !disposed) {
                    handler.onToken("Syntax error — fixing...\n");
                    AgentStep fixed = fixSyntaxError(resolved, result.getErrorMessage());
                    if (fixed != null) {
                        handler.onToolCall(fixed.tool(), fixed.toArgsJson());
                        ToolResult fixResult = dispatchStep(fixed);
                        if (fixResult.isSuccess()) continue;
                        result = fixResult; // fall through to failure
                    }
                }

                String msg = "Step failed (" + resolved.tool() + " " + resolved.primaryArg() + "): "
                        + result.getErrorMessage();
                log.warn("PlanAndExecute: {}", msg);
                handler.onToken("\n" + msg + "\n");
                handler.onComplete();
                return;
            }
        }

        handler.onToken("Done.\n");
        handler.onComplete();
    }

    private static boolean isSyntaxError(ToolResult result) {
        String msg = result.getErrorMessage();
        return msg != null && (msg.contains("Syntax error") || msg.contains("';' expected")
                || msg.contains("illegal start of expression"));
    }

    /**
     * Sends a targeted LLM call to fix the {@code replace} code in a failing editFile step.
     * Provides the current file content and the exact syntax error so the model can correct
     * only the broken fragment — without re-running discovery or full re-planning.
     *
     * @return a corrected {@link AgentStep}, or {@code null} if the fix call fails
     */
    private AgentStep fixSyntaxError(AgentStep step, String errorMessage) {
        if (step.path() == null) return null;

        ToolResult read = readFileTool.execute(Map.of("path", step.path()));
        String fileContext = read.isSuccess() ? read.getOutput() : "(file not readable)";

        String[] numbered = addLineNumbers(fileContext);
        String prompt = """
                A code edit produced an error. Fix the 'code' value so the resulting \
                file is syntactically valid.

                File: %s
                Current content (line numbers are 1-indexed):
                %s

                Failing step:
                  operation: %s
                  line/startLine: %s
                  code: %s

                Error: %s

                Output a single corrected JSON object — no explanation, no markdown:
                {"tool":"editFile","path":"%s","operation":"%s",...,"code":"..."}
                """.formatted(
                step.path(),
                truncate(String.join("\n", numbered), 8_000),
                step.operation(),
                step.line() != 0 ? step.line() : (step.startLine() + "-" + step.endLine()),
                step.code(),
                errorMessage,
                step.path(),
                step.operation() != null ? step.operation() : "insertAfterLine");

        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are an expert software engineer fixing a code generation error."),
                UserMessage.from(prompt));

        StringBuilder buffer = new StringBuilder();
        CountDownLatch latch  = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        StreamingChatModel model = overrideModel != null ? overrideModel : buildModel();
        withPluginClassLoader(() -> {
            model.chat(ChatRequest.builder().messages(messages).build(),
                    new StreamingChatResponseHandler() {
                        @Override public void onPartialResponse(String t) { buffer.append(t); }
                        @Override public void onCompleteResponse(ChatResponse r) { latch.countDown(); }
                        @Override public void onError(Throwable e) { error.set(e); latch.countDown(); }
                    });
            return null;
        });

        try {
            if (!latch.await(3, TimeUnit.MINUTES)) { return null; }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        if (error.get() != null) {
            log.warn("PlanAndExecute: syntax-fix LLM call failed: {}", error.get().getMessage());
            return null;
        }

        List<AgentStep> fixed = parseSteps("[" + buffer.toString().trim() + "]");
        if (fixed.isEmpty()) {
            // Try without wrapping brackets (model may have returned an array already)
            fixed = parseSteps(buffer.toString());
        }
        return fixed.isEmpty() ? null : fixed.get(0);
    }

    /**
     * Ensures the path in an editFile step points to an existing file.
     * If the file is not found at the planned path, searches the workspace by filename
     * and corrects the path — and validates that the search string exists in the actual content.
     *
     * @return the (possibly corrected) step, or {@code null} if the file cannot be found
     */
    private AgentStep resolveEditFilePath(AgentStep step, AgentStreamHandler handler) {
        if (step.path() == null) return step;

        ToolResult check = readFileTool.execute(Map.of("path", step.path()));
        if (check.isSuccess()) {
            return step;
        }

        // File not found at planned path — search by filename
        String fileName = step.path().contains("/")
                ? step.path().substring(step.path().lastIndexOf('/') + 1)
                : step.path();

        log.debug("PlanAndExecute: '{}' not found, searching by filename '{}'", step.path(), fileName);
        handler.onToken("File not found at planned path, searching for " + fileName + "...\n");

        ToolResult searchResult = searchCodeTool.execute(Map.of("query", fileName));
        if (!searchResult.isSuccess()) {
            handler.onToken("Cannot locate file: " + fileName + "\n");
            return null;
        }

        List<String> found = extractFilePaths(searchResult.getOutput());
        if (found.isEmpty()) {
            handler.onToken("Cannot locate file: " + fileName + "\n");
            return null;
        }

        String correctedPath = found.get(0);
        handler.onToken("Found at: " + correctedPath + "\n");

        return new AgentStep(step.tool(), correctedPath, step.content(),
                step.operation(), step.line(), step.startLine(), step.endLine(),
                step.code(), step.query());
    }

    /**
     * Returns true for plugin-internal files that must never be passed to the LLM as source context:
     * conversation history, Lucene index data, plugin state, build artefacts, VCS metadata.
     */
    static boolean isInternalFile(String relativePath) {
        if (relativePath == null) return true;
        String p = relativePath.replace('\\', '/');
        return p.startsWith(".ollamassist/")
                || p.startsWith(".idea/")
                || p.startsWith(".git/")
                || p.startsWith("build/")
                || p.startsWith("target/")
                || p.startsWith("node_modules/")
                || p.startsWith(".gradle/")
                || p.endsWith(".class")
                || p.endsWith(".jar");
    }

    private ToolResult dispatchStep(AgentStep step) {
        return switch (step.tool()) {
            case "writeFile"    -> writeFileTool.execute(step.toParams());
            case TOOL_EDIT_FILE -> lineEditTool.execute(step.toParams());
            case TOOL_READ_FILE -> readFileTool.execute(step.toParams());
            case "deleteFile"   -> deleteFileTool.execute(step.toParams());
            case "appendFile"   -> appendFileTool.execute(step.toParams());
            default             -> ToolResult.failure("Unknown tool in plan: " + step.tool());
        };
    }

    // -------------------------------------------------------------------------
    // Keyword extraction
    // -------------------------------------------------------------------------

    static List<String> extractKeywords(String goal) {
        List<String> keywords = new ArrayList<>();

        // CamelCase identifiers (Java, Kotlin, TypeScript, C#, …)
        Matcher camel = Pattern.compile("\\b[A-Z][a-zA-Z0-9]{2,}\\b").matcher(goal);
        while (camel.find()) keywords.add(camel.group());

        // snake_case compound names (Go, Python, Rust, PHP, Ruby, …)
        // Requires at least one underscore so common words are not matched.
        Matcher snake = Pattern.compile("\\b[a-z][a-z0-9]+(?:_[a-z0-9]+)+\\b").matcher(goal);
        while (snake.find()) keywords.add(snake.group());

        // File names with extensions
        Matcher file = Pattern.compile("\\b\\S+\\.(?:java|kt|go|ts|js|py|rs|php|cs|rb)\\b").matcher(goal);
        while (file.find()) keywords.add(file.group());

        // Quoted strings
        Matcher quoted = Pattern.compile("\"([^\"]{2,})\"").matcher(goal);
        while (quoted.find()) keywords.add(quoted.group(1));

        return keywords.stream().distinct().toList();
    }

    // -------------------------------------------------------------------------
    // File path extraction from searchWorkspace output
    // -------------------------------------------------------------------------

    static List<String> extractFilePaths(String searchOutput) {
        return searchOutput.lines()
                .map(line -> {
                    int colon = line.indexOf(':');
                    return colon > 0 ? line.substring(0, colon) : null;
                })
                .filter(Objects::nonNull)
                .filter(p -> p.contains("/") || p.contains("\\"))
                .filter(p -> !p.startsWith(" ") && !p.startsWith("\t"))
                .distinct()
                .limit(3)
                .toList();
    }

    // -------------------------------------------------------------------------
    // JSON plan parsing
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static List<AgentStep> parseSteps(String response) {
        int start = response.indexOf('[');
        int end   = response.lastIndexOf(']');
        if (start < 0 || end <= start) {
            log.warn("PlanAndExecute: no JSON array in response");
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(
                    response.substring(start, end + 1),
                    new TypeReference<>() {});
            return raw.stream()
                    .map(AgentStep::fromMap)
                    .filter(Objects::nonNull)
                    .filter(s -> s.tool().equals("writeFile") || s.tool().equals(TOOL_EDIT_FILE)
                              || s.tool().equals("deleteFile") || s.tool().equals("appendFile")
                              || s.tool().equals("answer"))
                    .toList();
        } catch (Exception e) {
            log.warn("PlanAndExecute: failed to parse plan JSON: {}", e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String buildSummary(List<AgentStep> steps) {
        return steps.stream()
                .map(s -> {
                    String target = s.path() != null ? s.path() : (s.query() != null ? s.query() : "?");
                    String name   = target.contains("/")
                            ? target.substring(target.lastIndexOf('/') + 1)
                            : target;
                    String op = s.operation() != null ? " [" + s.operation() + "]" : "";
                    return s.tool() + " " + name + op;
                })
                .collect(Collectors.joining(", "));
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        int head = (int) (max * 0.6);
        int tail = max - head;
        return s.substring(0, head)
                + "\n… [" + (s.length() - max) + " chars omitted] …\n"
                + s.substring(s.length() - tail);
    }

    private static String[] addLineNumbers(String content) {
        String[] lines = content.split("\n", -1);
        String[] result = new String[lines.length];
        for (int i = 0; i < lines.length; i++) {
            result[i] = String.format("%4d | %s", i + 1, lines[i]);
        }
        return result;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private StreamingChatModel buildModel() {
        OllamaStreamingChatModel.OllamaStreamingChatModelBuilder builder =
                OllamaStreamingChatModel.builder()
                        .baseUrl(OllamAssistSettings.getInstance().getChatOllamaUrl())
                        .modelName(OllamaSettings.getInstance().getAgentModelName())
                        .temperature(0.2)
                        .timeout(Duration.ofMinutes(5));

        Map<String, String> authHeaders = AuthenticationHelper.authHeaders();
        if (!authHeaders.isEmpty()) {
            builder.customHeaders(authHeaders);
        }
        return builder.build();
    }

    private <T> T withPluginClassLoader(java.util.concurrent.Callable<T> callable) {
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(PlanAndExecuteAgentService.class.getClassLoader());
        try {
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    record DiscoveryResult(Map<String, String> fileContents, Set<String> truncatedPaths) {}

    /** A relevant code fragment extracted from a project file. */
    record CodeFragment(String relativePath, String content) {}
}
