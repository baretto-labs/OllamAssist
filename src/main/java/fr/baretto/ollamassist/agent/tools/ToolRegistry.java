package fr.baretto.ollamassist.agent.tools;

import java.util.Set;

/**
 * Metadata registry for agent tools.
 *
 * <p>Provides the canonical set of tool IDs and their tier classification.
 * Tool dispatch is handled by LangChain4j's function-calling mechanism via
 * {@code @Tool}-annotated methods in {@link fr.baretto.ollamassist.agent.AgentToolProvider}.
 */
public final class ToolRegistry {

    /** All valid tool IDs registered in the agent. */
    public static final Set<String> KNOWN_TOOL_IDS = Set.of(
            "FILE_READ", "FILE_WRITE", "FILE_EDIT", "FILE_DELETE", "FILE_FIND",
            "FILE_APPEND", "LIST_DIRECTORY",
            "CODE_SEARCH", "RUN_COMMAND", "GIT_STATUS", "GIT_DIFF",
            "OPEN_IN_EDITOR", "GET_CURRENT_FILE", "SEARCH_KNOWLEDGE", "WEB_SEARCH"
    );

    /**
     * Read-only tools: only read state, never modify files, run commands, or open editors.
     * Used for the AUTO approval bypass — read-only tool calls never trigger the
     * {@link fr.baretto.ollamassist.events.FileApprovalNotifier} flow.
     */
    public static final Set<String> READ_ONLY_TOOL_IDS = Set.of(
            "FILE_READ", "FILE_FIND", "LIST_DIRECTORY", "CODE_SEARCH",
            "GIT_STATUS", "GIT_DIFF", "GET_CURRENT_FILE", "SEARCH_KNOWLEDGE", "WEB_SEARCH"
    );

    private ToolRegistry() {}
}
