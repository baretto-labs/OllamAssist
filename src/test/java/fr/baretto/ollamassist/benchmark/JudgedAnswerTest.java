package fr.baretto.ollamassist.benchmark;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ragunit.core.assertion.RagAssert;
import org.ragunit.core.domain.Document;
import org.ragunit.core.domain.Question;
import org.ragunit.core.judge.OllamaJudge;
import org.ragunit.core.judge.RagJudge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the measurement chain end to end: JitPack dependency, local Ollama, and a judged
 * assertion that behaves like a test — it passes, or it fails with a reason.
 *
 * <p>Two things are demonstrated here, and both matter more than the scores themselves:
 * <ul>
 *   <li><b>The pyramid.</b> {@code contains(...)} is exact, instant and free; the judge is
 *       asked only about what no assertion can decide. Same chain, two kinds of check.</li>
 *   <li><b>Variance control.</b> {@code withRuns(2)} averages the judge and fails when it is
 *       too unstable — a high mean from an erratic judge is noise, not a measurement.</li>
 * </ul>
 *
 * <p>Skipped when Ollama is unreachable, so the suite stays runnable without a model.
 *
 * <pre>
 *   ./gradlew benchmark
 *   ./gradlew benchmark -Dbenchmark.judge.model=qwen2.5:7b
 * </pre>
 */
@Tag("benchmark")
@DisplayName("Judged answer — RAGUnit against a local Ollama")
class JudgedAnswerTest {

    private static final String OLLAMA_URL =
            System.getProperty("benchmark.judge.url", "http://localhost:11434");
    private static final String JUDGE_MODEL =
            System.getProperty("benchmark.judge.model", "qwen2.5:14b");

    /** A real fragment of the plugin: the retriever the RAG pipeline actually runs. */
    private static final List<Document> CONTEXT = List.of(new Document("""
            public class HybridRetriever implements ContentRetriever {
                private static final int KNN_TOP_K = 5;
                private static final int BM25_TOP_K = 5;
                private static final int FINAL_TOP_K = 3;

                public List<Content> retrieve(Query query) {
                    float[] queryVector = embeddingModel.embed(query.text()).content().vector();
                    // KNN and BM25 run in parallel, then Reciprocal Rank Fusion re-ranks
                    List<EmbeddingMatch<TextSegment>> fused =
                            RRFFusion.fuse(knnResults, bm25Results, FINAL_TOP_K);
                    return fused.stream().map(match -> Content.from(match.embedded())).toList();
                }
            }
            """));

    private static final Question QUESTION =
            new Question("How does HybridRetriever combine vector search and keyword search?");

    private static RagJudge judge;

    @BeforeAll
    static void requireOllama() {
        Assumptions.assumeTrue(ollamaIsReachable(),
                "Ollama unreachable at " + OLLAMA_URL + " — judged assertions skipped");
        URI url = URI.create(OLLAMA_URL);
        judge = OllamaJudge.builder()
                .model(JUDGE_MODEL)
                .host(url.getHost())
                .port(url.getPort() > 0 ? url.getPort() : 11434)
                .build();
    }

    @Test
    @DisplayName("passes when the answer stays inside the retrieved context")
    void shouldPassWhenAnswerIsGroundedInContext() {
        String answer = """
                HybridRetriever runs a KNN vector search and a BM25 keyword search in parallel, \
                then merges both result lists with Reciprocal Rank Fusion and keeps the top 3.""";

        RagAssert.assertThatAnswer(answer)
                .contains("Reciprocal Rank Fusion")   // exact, instant, no model involved
                .containsAll("KNN", "BM25")
                .givenContext(CONTEXT)
                .forQuestion(QUESTION)
                .evaluatedBy(judge)
                .withRuns(2)                        // averaged, and rejected if unstable
                .isFaithfulToContext(0.70);         // judged — no assertion can decide this
    }

    @Test
    @DisplayName("fails with the judge's reasoning when the answer invents its facts")
    void shouldFailWithAJustificationWhenAnswerIsUnfaithful() {
        String hallucinated = """
                HybridRetriever queries a remote Elasticsearch cluster over gRPC and re-ranks \
                the hits with a cross-encoder model hosted on HuggingFace.""";

        assertThatThrownBy(() ->
                RagAssert.assertThatAnswer(hallucinated)
                        .givenContext(CONTEXT)
                        .forQuestion(QUESTION)
                        .evaluatedBy(judge)
                        .isFaithfulToContext(0.70))
                .isInstanceOf(AssertionError.class)
                .satisfies(error -> {
                    // Printed on purpose: this message is the point of the whole exercise —
                    // a judged assertion fails like a test, with a reason a human can read.
                    System.out.println("\n--- failing judged assertion ---\n"
                            + error.getMessage() + "\n");
                    assertThat(error.getMessage())
                            .as("a failing judged assertion must say why, not just how much")
                            .contains("judge justification");
                });
    }

    private static boolean ollamaIsReachable() {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build()) {
            HttpResponse<Void> response = client.send(
                    HttpRequest.newBuilder(URI.create(OLLAMA_URL + "/api/tags"))
                            .timeout(Duration.ofSeconds(3))
                            .GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
