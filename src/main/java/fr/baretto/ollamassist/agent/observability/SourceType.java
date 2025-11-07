package fr.baretto.ollamassist.agent.observability;

/**
 * Types of sources that can be referenced in agent execution.
 */
public enum SourceType {
    /** Local file */
    FILE,

    /** Web URL */
    URL,

    /** Java class */
    CLASS,

    /** Git commit */
    COMMIT,

    /** Documentation file */
    DOCUMENTATION,

    /** Code snippet (no specific file) */
    SNIPPET,

    /** From vector store */
    EMBEDDING
}
