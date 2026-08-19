package fr.baretto.ollamassist.benchmark.judge;

import org.ragunit.core.judge.Criterion;
import org.ragunit.core.judge.Judge;
import org.ragunit.core.judge.JudgeQuery;
import org.ragunit.core.judge.JudgeResult;
import org.ragunit.core.judge.OllamaJudge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

/**
 * Rates a retrieved context with RAGUnit instead of a hand-rolled prompt.
 *
 * <p>What the library brings over {@link BenchmarkJudge}: the prompt is a public versioned
 * constant rather than a string in this repository, and the result carries the exact prompt
 * sent and the raw model response, so a surprising score can be audited instead of argued
 * about.
 *
 * <p>RAGUnit scores in {@code [0.0, 1.0]}; the benchmark records 0-10, so the score is scaled
 * on the way out and results stay comparable with every run since May 2026.
 *
 * <p>Not covered yet: {@code suggestsUnknown}. RAGUnit's equivalent is the built-in
 * {@code CONTEXT_REJECTION} metric, which costs a second judge call per question — left out
 * of this first integration and always reported as {@code false} on this path.
 *
 * <pre>
 *   ./gradlew benchmark -Dbenchmark.judge.impl=ragunit
 *   ./gradlew benchmark -Dbenchmark.judge.impl=ragunit -Dbenchmark.judge.model=qwen2.5:14b
 * </pre>
 */
public final class RagUnitJudge implements ContextJudge {

    private static final Logger log = LoggerFactory.getLogger(RagUnitJudge.class);

    private static final String DEFAULT_MODEL = "qwen2.5:14b";
    private static final String DEFAULT_URL = "http://localhost:11434";
    /** Pinned in build.gradle.kts; recorded in the results so a bump is visible in the history. */
    private static final String RAGUNIT_VERSION = "v0.2.1";

    /**
     * The question put to the judge. Deliberately worded like the rubric of the hand-rolled
     * judge so the two layers can be compared on the same benchmark.
     */
    private static final Criterion CONTEXT_SUFFICIENCY = Criterion.of(
            "context-sufficiency",
            """
            Does the retrieved context contain enough information to accurately answer the \
            question about this Java codebase? Rate 1.0 when the context answers the question \
            fully and directly, around 0.5 when key information is missing, and 0.0 when the \
            context is unrelated to the question.""");

    private final Judge judge;
    private final boolean available;
    private final String id;

    /** Builds an Ollama-backed judge from the {@code benchmark.judge.*} system properties. */
    public RagUnitJudge() {
        String model = System.getProperty("benchmark.judge.model", DEFAULT_MODEL);
        URI url = URI.create(System.getProperty("benchmark.judge.url", DEFAULT_URL));

        Judge built = null;
        boolean ok = false;
        try {
            built = OllamaJudge.builder()
                    .model(model)
                    .host(url.getHost())
                    .port(url.getPort() > 0 ? url.getPort() : 11434)
                    .build();
            built.evaluate(JudgeQuery.builder()
                    .criterion(CONTEXT_SUFFICIENCY)
                    .input(JudgeQuery.INPUT_QUESTION, "ping")
                    .input(JudgeQuery.INPUT_CONTEXT, "pong")
                    .build());
            ok = true;
            log.info("RAGUnit judge ready: {} @ {}", model, url);
        } catch (Exception e) {
            log.warn("RAGUnit judge unavailable ({}). Only hintCoverage will be computed.", e.getMessage());
        }
        this.judge = built;
        this.available = ok;
        this.id = "ragunit-%s/%s".formatted(RAGUNIT_VERSION, model);
    }

    /** Injects the judge directly — used by tests and by any non-Ollama backend. */
    RagUnitJudge(Judge judge) {
        this.judge = judge;
        this.available = true;
        this.id = "ragunit-" + RAGUNIT_VERSION;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public Judgement judge(String question, List<String> contexts, String[] expectedHints) {
        if (contexts.isEmpty()) {
            return Judgement.noContext();
        }

        String contextText = ContextPreparation.shuffleAndJoin(contexts);
        double hintCoverage = ContextPreparation.hintCoverage(contextText, expectedHints);

        if (!available || judge == null) {
            return Judgement.notJudged("Judge not available", hintCoverage);
        }

        try {
            JudgeResult result = judge.evaluate(JudgeQuery.builder()
                    .criterion(CONTEXT_SUFFICIENCY)
                    .input(JudgeQuery.INPUT_QUESTION, question)
                    .input(JudgeQuery.INPUT_CONTEXT, contextText)
                    .build());

            return new Judgement(toTenPointScale(result.score()), result.justification(),
                    false, hintCoverage, true);
        } catch (Exception e) {
            log.warn("RAGUnit judge failed for '{}': {}", question, e.getMessage());
            return Judgement.notJudged("Judge error: " + e.getMessage(), hintCoverage);
        }
    }

    private static int toTenPointScale(double normalizedScore) {
        return Math.clamp(Math.round(normalizedScore * 10), 0, 10);
    }
}
