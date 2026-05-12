package fr.baretto.ollamassist.agent.tools;

/**
 * Domain value object representing a single result from any search tool.
 *
 * <p>Acts as the anti-corruption boundary between LangChain4j types
 * ({@code EmbeddingMatch<TextSegment>}, {@code Content}) and agent tool output.
 * Adapters in each concrete tool convert provider-specific types to this record.
 */
public record SearchEntry(
        /** Origin identifier: file path, URL, or source label. May be blank. */
        String source,
        /** Short title or heading. May be blank. */
        String title,
        /** Main content body — the text the agent should read. */
        String body
) {
    /** Convenience factory when source and title are not available. */
    public static SearchEntry bodyOnly(String body) {
        return new SearchEntry("", "", body != null ? body : "");
    }
}
