package fr.baretto.ollamassist.benchmark.judge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Context handling shared by every {@link ContextJudge}, so both judging layers see exactly
 * the same text and the comparison between them measures the judge, not the preparation.
 */
final class ContextPreparation {

    private static final int MAX_CONTEXT_CHARS = 6_000;
    private static final String CHUNK_SEPARATOR = "\n---\n";

    private ContextPreparation() {
    }

    /** Shuffles the chunks to blunt position bias, then joins and caps the result. */
    static String shuffleAndJoin(List<String> contexts) {
        List<String> shuffled = new ArrayList<>(contexts);
        Collections.shuffle(shuffled);
        String text = String.join(CHUNK_SEPARATOR, shuffled);
        return text.length() > MAX_CONTEXT_CHARS
                ? text.substring(0, MAX_CONTEXT_CHARS) + "\n[... truncated ...]"
                : text;
    }

    /** Fraction of expected symbols present in the context. No LLM involved. */
    static double hintCoverage(String contextText, String[] hints) {
        if (hints == null || hints.length == 0) {
            return 1.0;
        }
        String lower = contextText.toLowerCase();
        long found = 0;
        for (String hint : hints) {
            if (lower.contains(hint.toLowerCase())) {
                found++;
            }
        }
        return (double) found / hints.length;
    }
}
