package fr.baretto.ollamassist.core.session;

/**
 * Types of sessions supported by the session manager.
 */
public enum SessionType {

    /**
     * Interactive chat session with conversation history.
     */
    CHAT,

    /**
     * Code completion session with context tracking.
     */
    COMPLETION,

    /**
     * Agent mode session for multi-step autonomous tasks.
     */
    AGENT
}