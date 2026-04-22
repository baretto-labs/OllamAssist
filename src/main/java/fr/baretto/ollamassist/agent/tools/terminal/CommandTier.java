package fr.baretto.ollamassist.agent.tools.terminal;

/**
 * Security tier for a shell command.
 *
 * <ul>
 *   <li>{@link #READ_ONLY} — purely observational; executes without confirmation.</li>
 *   <li>{@link #MUTATING} — modifies local state (files, branches, packages);
 *       requires user confirmation before execution.</li>
 *   <li>{@link #DESTRUCTIVE} — irreversible or high-blast-radius operations
 *       (force-push, hard reset, mass deletion); always blocked.</li>
 * </ul>
 */
public enum CommandTier {
    READ_ONLY,
    MUTATING,
    DESTRUCTIVE
}
