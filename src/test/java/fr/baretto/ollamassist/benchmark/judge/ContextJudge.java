package fr.baretto.ollamassist.benchmark.judge;

import java.util.List;

/**
 * Rates how well a retrieved context answers a benchmark question.
 *
 * <p>Two implementations exist so a run can be reproduced with either judging layer:
 * {@link BenchmarkJudge} (hand-rolled prompt over LangChain4j {@code AiServices}) and
 * {@link RagUnitJudge} (RAGUnit, versioned prompt and auditable result). {@link #id()} is
 * written to the results file — a score is only comparable to another score produced by the
 * same judge.
 */
public interface ContextJudge {

    /** Identifies the judging layer and its version, e.g. {@code ragunit-v0.2.1}. */
    String id();

    /** Whether the judge answered a probe call at construction time. */
    boolean isAvailable();

    /**
     * Rates the retrieved context for one question.
     *
     * @param question      the benchmark question
     * @param contexts      the chunks the retriever returned
     * @param expectedHints symbols a good context is expected to contain
     */
    Judgement judge(String question, List<String> contexts, String[] expectedHints);

    /**
     * One rating.
     *
     * @param score           relevance on a 0-10 scale, or {@code -1} when no judgement was made
     * @param rationale       the judge's explanation, or the reason no judgement was made
     * @param suggestsUnknown whether the context is too poor to answer at all
     * @param hintCoverage    fraction of {@code expectedHints} present in the context — deterministic,
     *                        computed without the judge and therefore always available
     * @param judged          whether a judgement actually happened; averages must ignore the rest
     */
    record Judgement(int score, String rationale, boolean suggestsUnknown,
                     double hintCoverage, boolean judged) {

        /** Nothing was retrieved: a real zero, no judge call needed. */
        static Judgement noContext() {
            return new Judgement(0, "No context retrieved", true, 0.0, false);
        }

        /**
         * The judge could not answer. Reported as "not judged" rather than as a zero:
         * an outage is not a measurement, and averaging it in would drag the benchmark down.
         */
        static Judgement notJudged(String reason, double hintCoverage) {
            return new Judgement(-1, reason, false, hintCoverage, false);
        }
    }
}
