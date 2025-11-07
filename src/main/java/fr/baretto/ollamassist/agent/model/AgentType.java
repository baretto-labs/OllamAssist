package fr.baretto.ollamassist.agent.model;

/**
 * Types of agents in the system.
 * Used for routing and identification.
 */
public enum AgentType {
    /** Orchestrator agent coordinating others */
    ORCHESTRATOR,

    /** RAG/Search agent for codebase search */
    RAG_SEARCH,

    /** Git operations agent */
    GIT,

    /** Code refactoring agent */
    REFACTORING,

    /** Code quality analysis agent */
    CODE_ANALYSIS
}
