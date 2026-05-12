package fr.baretto.ollamassist.agent;

/**
 * Receives streaming events from a {@link FunctionCallingAgentService} execution.
 *
 * <p>Callbacks are invoked on a background thread — UI updates must be dispatched
 * to the EDT via {@code ApplicationManager.getApplication().invokeLater(...)}.
 */
public interface AgentStreamHandler {

    /** A reasoning or answer token streamed from the LLM. */
    void onToken(String token);

    /** A tool call is about to execute (name + JSON arguments from the LLM). */
    void onToolCall(String toolName, String arguments);

    /** The tool returned a result (success or error string). */
    void onToolResult(String toolName, String result);

    /** Execution finished normally. */
    void onComplete();

    /** Execution failed with an error. */
    void onError(Throwable error);

    /**
     * Optional progress update during the execute phase.
     * @param current 1-based index of the step just started
     * @param total   total number of steps in the plan
     */
    default void onProgress(int current, int total) {}
}
