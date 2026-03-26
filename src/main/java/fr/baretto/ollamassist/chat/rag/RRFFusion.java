package fr.baretto.ollamassist.chat.rag;

import dev.langchain4j.store.embedding.EmbeddingMatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion (RRF) for combining KNN and BM25 ranked lists.
 *
 * <p>Formula: score(d) = Σ 1 / (k + rank(d))
 * where k=60 is the standard TREC constant that dampens the impact of high-ranked documents.
 */
public class RRFFusion {

    static final int RRF_K = 60;

    private RRFFusion() {
    }

    public static <T> List<EmbeddingMatch<T>> fuse(
            List<EmbeddingMatch<T>> knnResults,
            List<EmbeddingMatch<T>> bm25Results,
            int topK) {

        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, EmbeddingMatch<T>> byId = new HashMap<>();

        for (int i = 0; i < knnResults.size(); i++) {
            EmbeddingMatch<T> match = knnResults.get(i);
            rrfScores.merge(match.embeddingId(), 1.0 / (RRF_K + i + 1), Double::sum);
            byId.putIfAbsent(match.embeddingId(), match);
        }

        for (int i = 0; i < bm25Results.size(); i++) {
            EmbeddingMatch<T> match = bm25Results.get(i);
            rrfScores.merge(match.embeddingId(), 1.0 / (RRF_K + i + 1), Double::sum);
            byId.putIfAbsent(match.embeddingId(), match);
        }

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(rrfScores.entrySet());
        sorted.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        List<EmbeddingMatch<T>> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, sorted.size()); i++) {
            Map.Entry<String, Double> entry = sorted.get(i);
            EmbeddingMatch<T> original = byId.get(entry.getKey());
            result.add(new EmbeddingMatch<>(entry.getValue(), entry.getKey(), null, original.embedded()));
        }
        return result;
    }
}
