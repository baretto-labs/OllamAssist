package fr.baretto.ollamassist.benchmark.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ragunit.core.judge.Judge;
import org.ragunit.core.judge.JudgeQuery;
import org.ragunit.core.judge.JudgeResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The RAGUnit-backed judge must stay comparable with the hand-rolled one: same 0-10 scale,
 * same behaviour on an empty context, and the same rule on failure — a judge that cannot
 * answer reports "not judged", it never reports a zero. A zero is a measurement; an outage
 * is not, and averaging the two would quietly drag the benchmark down.
 */
@DisplayName("RAGUnit benchmark judge")
class RagUnitJudgeTest {

    private static final String[] HINTS = {"LuceneEmbeddingStore", "knnSearch"};

    private static RagUnitJudge judgeReturning(double normalizedScore, String justification) {
        return new RagUnitJudge(query -> new JudgeResult(
                normalizedScore, justification, "prompt", "raw", "qwen2.5:14b"));
    }

    @Test
    @DisplayName("maps the normalized score onto the benchmark's ten-point scale")
    void shouldMapNormalizedScoreToTenPointScale() {
        RagUnitJudge judge = judgeReturning(0.72, "Context covers the method signature.");

        ContextJudge.Judgement judgement = judge.judge(
                "What does knnSearch do?",
                List.of("class LuceneEmbeddingStore { knnSearch(...) }"),
                HINTS);

        assertThat(judgement.score()).isEqualTo(7);
        assertThat(judgement.judged()).isTrue();
        assertThat(judgement.rationale()).isEqualTo("Context covers the method signature.");
    }

    @Test
    @DisplayName("computes hint coverage from the retrieved context")
    void shouldComputeHintCoverage() {
        RagUnitJudge judge = judgeReturning(1.0, "ok");

        ContextJudge.Judgement judgement = judge.judge(
                "What does knnSearch do?",
                List.of("class LuceneEmbeddingStore { }"),
                HINTS);

        assertThat(judgement.hintCoverage()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("scores zero without calling the judge when nothing was retrieved")
    void shouldNotCallJudgeWhenContextIsEmpty() {
        RagUnitJudge judge = new RagUnitJudge(query -> {
            throw new AssertionError("the judge must not be called on an empty context");
        });

        ContextJudge.Judgement judgement = judge.judge("any question", List.of(), HINTS);

        assertThat(judgement.score()).isZero();
        assertThat(judgement.suggestsUnknown()).isTrue();
        assertThat(judgement.judged()).isFalse();
    }

    @Test
    @DisplayName("reports a judge failure as not judged, never as a zero score")
    void shouldReportFailureAsNotJudged() {
        RagUnitJudge judge = new RagUnitJudge(query -> {
            throw new IllegalStateException("connection refused");
        });

        ContextJudge.Judgement judgement = judge.judge(
                "What does knnSearch do?",
                List.of("class LuceneEmbeddingStore { knnSearch(...) }"),
                HINTS);

        assertThat(judgement.judged()).isFalse();
        assertThat(judgement.score()).isEqualTo(-1);
        assertThat(judgement.rationale()).contains("connection refused");
    }

    @Test
    @DisplayName("still computes hint coverage when the judge fails")
    void shouldKeepDeterministicMetricWhenJudgeFails() {
        RagUnitJudge judge = new RagUnitJudge((Judge) query -> {
            throw new IllegalStateException("connection refused");
        });

        ContextJudge.Judgement judgement = judge.judge(
                "What does knnSearch do?",
                List.of("class LuceneEmbeddingStore { knnSearch(...) }"),
                HINTS);

        assertThat(judgement.hintCoverage()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("names the judge implementation so runs stay comparable")
    void shouldExposeItsIdentity() {
        assertThat(judgeReturning(1.0, "ok").id()).startsWith("ragunit");
    }

    @Test
    @DisplayName("sends the question and the retrieved context to the judge")
    void shouldSendQuestionAndContext() {
        var captured = new JudgeQuery[1];
        RagUnitJudge judge = new RagUnitJudge(query -> {
            captured[0] = query;
            return new JudgeResult(1.0, "ok", "prompt", "raw", "model");
        });

        judge.judge("What does knnSearch do?", List.of("knnSearch body"), HINTS);

        assertThat(captured[0].criterion().name()).isEqualTo("context-sufficiency");
        assertThat(captured[0].firstInput(JudgeQuery.INPUT_QUESTION))
                .contains("What does knnSearch do?");
        assertThat(captured[0].inputValues(JudgeQuery.INPUT_CONTEXT))
                .anySatisfy(value -> assertThat(value).contains("knnSearch body"));
    }
}
