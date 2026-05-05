package fr.baretto.ollamassist.agent;

import com.intellij.openapi.project.Project;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import fr.baretto.ollamassist.agent.tools.ToolRateLimiter;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import fr.baretto.ollamassist.agent.tools.files.EditFileTool;
import fr.baretto.ollamassist.agent.tools.files.WriteFileTool;
import fr.baretto.ollamassist.agent.tools.navigation.SearchCodeTool;
import fr.baretto.ollamassist.agent.tools.rag.SearchKnowledgeBaseTool;
import fr.baretto.ollamassist.agent.tools.web.WebSearchAgentTool;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Exposes agent tools to LangChain4j's function-calling mechanism via {@code @Tool} annotations.
 *
 * <p>This class is a pure adapter: each method delegates to an existing {@code AgentTool}
 * implementation that contains all business logic and security guards (FilePathGuard,
 * PromptSanitizer, SecretDetector, ToolRateLimiter).
 *
 * <p>Per AGENT_ARCH.md Rule 2: methods accept only {@code String} parameters, return
 * {@code String}, and never propagate checked exceptions to the LLM layer.
 */
@Slf4j
public class AgentToolProvider {

    private final EditFileTool editFileTool;
    private final WriteFileTool writeFileTool;
    private final WebSearchAgentTool webSearchTool;
    private final SearchCodeTool searchCodeTool;
    private final SearchKnowledgeBaseTool searchKnowledgeTool;
    private final ToolRateLimiter rateLimiter;
    private volatile boolean aborted = false;

    public AgentToolProvider(Project project, ToolRateLimiter rateLimiter) {
        this.editFileTool = new EditFileTool(project);
        this.writeFileTool = new WriteFileTool(project);
        this.webSearchTool = new WebSearchAgentTool();
        this.searchCodeTool = new SearchCodeTool(project);
        this.searchKnowledgeTool = new SearchKnowledgeBaseTool(project);
        this.rateLimiter = rateLimiter;
    }

    // --- Test-only constructor ---

    @org.jetbrains.annotations.TestOnly
    AgentToolProvider(EditFileTool editFileTool,
                      WriteFileTool writeFileTool,
                      WebSearchAgentTool webSearchTool,
                      SearchCodeTool searchCodeTool,
                      SearchKnowledgeBaseTool searchKnowledgeTool,
                      ToolRateLimiter rateLimiter) {
        this.editFileTool = editFileTool;
        this.writeFileTool = writeFileTool;
        this.webSearchTool = webSearchTool;
        this.searchCodeTool = searchCodeTool;
        this.searchKnowledgeTool = searchKnowledgeTool;
        this.rateLimiter = rateLimiter;
    }

    /** Called by FunctionCallingAgentService when MAX_TOOL_CALLS is reached. */
    public void abort() {
        this.aborted = true;
    }

    /** Called at the start of each execution to reset the abort flag. */
    public void resetAbort() {
        this.aborted = false;
    }

    // -------------------------------------------------------------------------
    // Tools
    // -------------------------------------------------------------------------

    private static final String ABORTED_MSG =
            "ERROR: Agent execution stopped — maximum tool calls per execution reached. " +
            "Please summarize what you have accomplished so far and provide a final answer.";

    private String checkAborted() {
        return aborted ? ABORTED_MSG : null;
    }

    @Tool("Edit an existing file by replacing a specific text fragment. " +
          "The 'search' string must exactly match the content currently in the file (whitespace included). " +
          "Use replaceAll=true to replace all occurrences; default replaces only the first. " +
          "Returns an error if the file does not exist, the search string is not found, or the user rejects the change.")
    public String editFile(
            @P("File path relative to the project root, e.g. src/main/java/com/example/Foo.java") String path,
            @P("Exact text to search for in the file — must match content character-for-character") String search,
            @P("Text to replace the found content with") String replace,
            @P("If true, replace all occurrences; if false (default), replace only the first") String replaceAll) {
        String abortMsg = checkAborted();
        if (abortMsg != null) return abortMsg;
        if (!rateLimiter.tryAcquire("FILE_EDIT")) {
            return "ERROR: FILE_EDIT rate limit reached for this execution. Too many file edits.";
        }
        boolean doReplaceAll = "true".equalsIgnoreCase(replaceAll);
        ToolResult result = editFileTool.execute(Map.of(
                "path", path != null ? path : "",
                "search", search != null ? search : "",
                "replace", replace != null ? replace : "",
                "replaceAll", doReplaceAll
        ));
        return toObservation(result);
    }

    @Tool("Create a new file at the given path with the given content. " +
          "Returns an error if the file already exists (use editFile to modify existing files), " +
          "if the path escapes the project root, or if the user rejects the creation.")
    public String writeFile(
            @P("File path relative to the project root, e.g. src/main/java/com/example/Bar.java") String path,
            @P("Full content to write into the new file") String content) {
        String abortMsg = checkAborted();
        if (abortMsg != null) return abortMsg;
        if (!rateLimiter.tryAcquire("FILE_WRITE")) {
            return "ERROR: FILE_WRITE rate limit reached for this execution. Too many file creations.";
        }
        ToolResult result = writeFileTool.execute(Map.of(
                "path", path != null ? path : "",
                "content", content != null ? content : ""
        ));
        return toObservation(result);
    }

    @Tool("Search the internet using DuckDuckGo and return the top results. " +
          "Use this when the user's goal requires up-to-date information, library documentation, " +
          "or knowledge not available in the project. " +
          "Returns an error if web search is disabled in settings.")
    public String searchWeb(
            @P("Search query, e.g. 'LangChain4j AiServices tool use Java example'") String query) {
        String abortMsg = checkAborted();
        if (abortMsg != null) return abortMsg;
        if (!rateLimiter.tryAcquire("WEB_SEARCH")) {
            return "ERROR: WEB_SEARCH rate limit reached for this execution.";
        }
        ToolResult result = webSearchTool.execute(Map.of("query", query != null ? query : ""));
        return toObservation(result);
    }

    @Tool("Search the project workspace for files containing a specific keyword or code fragment. " +
          "Returns matching lines with file path and line number context. " +
          "Use this for exact-string searches. For semantic/concept search, use searchKnowledgeBase instead.")
    public String searchWorkspace(
            @P("Keyword or code fragment to search for, e.g. 'OllamaService' or 'getService'") String query) {
        String abortMsg = checkAborted();
        if (abortMsg != null) return abortMsg;
        if (!rateLimiter.tryAcquire("CODE_SEARCH")) {
            return "ERROR: CODE_SEARCH rate limit reached for this execution.";
        }
        ToolResult result = searchCodeTool.execute(Map.of("query", query != null ? query : ""));
        return toObservation(result);
    }

    @Tool("Search the project knowledge base using semantic (vector) search via the Lucene index. " +
          "Returns relevant code segments ranked by relevance to the query concept. " +
          "Use this when you need to find code related to a concept rather than an exact keyword.")
    public String searchKnowledgeBase(
            @P("Concept or description to search for, e.g. 'authentication middleware' or 'file indexing pipeline'") String query) {
        String abortMsg = checkAborted();
        if (abortMsg != null) return abortMsg;
        if (!rateLimiter.tryAcquire("SEARCH_KNOWLEDGE")) {
            return "ERROR: SEARCH_KNOWLEDGE rate limit reached for this execution.";
        }
        ToolResult result = searchKnowledgeTool.execute(Map.of("query", query != null ? query : ""));
        return toObservation(result);
    }

    // -------------------------------------------------------------------------

    private static String toObservation(ToolResult result) {
        if (result.isSuccess()) {
            return result.getOutput().isBlank() ? "(empty result)" : result.getOutput();
        }
        return "ERROR: " + result.getErrorMessage();
    }
}
