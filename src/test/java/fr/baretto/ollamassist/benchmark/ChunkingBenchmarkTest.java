package fr.baretto.ollamassist.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import fr.baretto.ollamassist.benchmark.judge.BenchmarkJudge;
import fr.baretto.ollamassist.chat.rag.CodeAwareDocumentSplitter;
import fr.baretto.ollamassist.chat.rag.HybridRetriever;
import fr.baretto.ollamassist.chat.rag.LuceneEmbeddingStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * LLM-as-a-judge benchmark comparing chunking strategies using the real production pipeline.
 *
 * <p>Uses the actual {@link LuceneEmbeddingStore}, {@link HybridRetriever}, and
 * {@link CodeAwareDocumentSplitter} on the OllamAssist source code. This ensures
 * benchmark results reflect the plugin's true retrieval quality.
 *
 * <p>Strategies compared:
 * <ul>
 *   <li><b>line-based</b>: {@code CodeAwareDocumentSplitter(null)} — forces the 60-line fallback</li>
 *   <li><b>psi-java</b>: {@code CodeAwareDocumentSplitter(project)} — real PSI class+method chunks</li>
 * </ul>
 *
 * <p>Metrics:
 * <ul>
 *   <li>{@code hintCoverage} — deterministic: fraction of expected FQN hints found in retrieved context</li>
 *   <li>{@code llmScore} — 0-10 from LLM judge (requires {@code -Dbenchmark.judge.enabled=true})</li>
 * </ul>
 *
 * <p>Results are appended to {@code benchmark-results/YYYY-MM-DD_chunking.jsonl}
 * (relative to project root) to track quality evolution over time.
 *
 * <p>Run:
 * <pre>
 *   ./gradlew benchmark
 *   ./gradlew benchmark -Dbenchmark.judge.enabled=true
 *   ./gradlew benchmark -Dbenchmark.judge.enabled=true -Dbenchmark.judge.model=llama3.2:3b
 * </pre>
 */
@Tag("benchmark")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChunkingBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(ChunkingBenchmarkTest.class);

    private static final Path SOURCE_DIR    = Path.of("src/main/java/fr/baretto/ollamassist");
    private static final Path RESULTS_DIR   = Path.of("benchmark-results");
    private static final int  SEARCH_TOP_K  = 5;

    private EmbeddingModel embeddingModel;
    private BenchmarkJudge judge;
    private List<Path>     sourceFiles;
    private ObjectMapper   mapper;
    private Project        psiProject;

    @BeforeAll
    void setUp() throws Exception {
        log.info("=== Chunking Benchmark Setup ===");

        embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        log.info("Embedding model ready");

        sourceFiles = scanSourceFiles(SOURCE_DIR);
        log.info("Source files found: {}", sourceFiles.size());

        judge  = new BenchmarkJudge();
        mapper = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        Files.createDirectories(RESULTS_DIR);
    }

    private Project resolveProject() {
        try {
            ProjectManager pm = ApplicationManager.getApplication().getService(ProjectManager.class);
            return pm != null ? pm.getDefaultProject() : null;
        } catch (Throwable t) {
            log.warn("ProjectManager unavailable (headless mode): {}", t.getMessage());
            return null;
        }
    }

    @AfterAll
    void printSummary() throws IOException {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        Path outputFile = RESULTS_DIR.resolve(today + "_chunking.jsonl");
        if (!Files.exists(outputFile)) return;

        Map<String, StrategyStats> stats = new LinkedHashMap<>();
        try (Stream<String> lines = Files.lines(outputFile)) {
            lines.forEach(line -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = mapper.readValue(line, Map.class);
                    String strategy = (String) row.get("strategy");
                    StrategyStats s = stats.computeIfAbsent(strategy, k -> new StrategyStats());
                    s.count++;
                    s.totalHintCoverage += ((Number) row.getOrDefault("hintCoverage", 0.0)).doubleValue();
                    Number llm = (Number) row.get("llmScore");
                    if (llm != null && llm.intValue() >= 0) {
                        s.judgedCount++;
                        s.totalLlmScore += llm.intValue();
                    }
                } catch (Exception ignored) {}
            });
        }

        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           CHUNKING BENCHMARK RESULTS                      ║");
        System.out.println("╠══════════════════════╦═══════════════╦════════════════════╣");
        System.out.printf( "║ %-20s ║ HintCoverage  ║ LLM Score          ║%n", "Strategy");
        System.out.println("╠══════════════════════╬═══════════════╬════════════════════╣");
        stats.forEach((name, s) -> {
            double avgHint = s.count > 0 ? s.totalHintCoverage / s.count : 0.0;
            String llmStr  = s.judgedCount > 0
                    ? "%.2f/10 (n=%d)".formatted(s.totalLlmScore / (double) s.judgedCount, s.judgedCount)
                    : "N/A (judge off)    ";
            System.out.printf("║ %-20s ║ %5.1f%%        ║ %-20s║%n", name, avgHint * 100, llmStr);
        });
        System.out.println("╚══════════════════════╩═══════════════╩════════════════════╝");
        System.out.println("Results: " + outputFile.toAbsolutePath());
    }

    @Test
    void benchmark() throws Exception {
        String today   = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String runTs   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        Path outputFile = RESULTS_DIR.resolve(today + "_chunking.jsonl");

        psiProject = resolveProject();
        log.info("Using IntelliJ project: {}", psiProject != null ? psiProject.getName() : "N/A (headless)");

        try (var writer = Files.newBufferedWriter(outputFile,
                Files.exists(outputFile)
                        ? java.nio.file.StandardOpenOption.APPEND
                        : java.nio.file.StandardOpenOption.CREATE)) {

            // Strategy A: line-based fallback (null project forces fallback in CodeAwareDocumentSplitter)
            runStrategy("line-based", new CodeAwareDocumentSplitter(null), runTs, writer);

            // Strategy B: PSI Java (real project — class chunk + method chunk)
            runStrategy("psi-java", new CodeAwareDocumentSplitter(psiProject), runTs, writer);
        }
    }

    private void runStrategy(String strategyName,
                              CodeAwareDocumentSplitter splitter,
                              String runTs,
                              java.io.BufferedWriter writer) throws Exception {

        log.info("\n--- Strategy: {} ---", strategyName);

        // Unique index name per strategy+run to avoid interference
        Project storeProject = mockProjectNamed("benchmark-" + strategyName);
        LuceneEmbeddingStore<TextSegment> store = new LuceneEmbeddingStore<>(storeProject);
        try {
            // Build ingestor with the real embedding model + selected splitter
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .embeddingStore(store)
                    .embeddingModel(embeddingModel)
                    .documentSplitter(splitter)
                    .build();

            // Index source files
            log.info("[{}] Indexing {} files...", strategyName, sourceFiles.size());
            int indexed = 0;
            for (Path file : sourceFiles) {
                try {
                    Document doc = FileSystemDocumentLoader.loadDocument(file);
                    var app = ApplicationManager.getApplication();
                    if (app != null) {
                        app.runReadAction((Runnable) () -> ingestor.ingest(doc));
                    } else {
                        ingestor.ingest(doc);
                    }
                    indexed++;
                } catch (Exception e) {
                    log.debug("[{}] Skipped {}: {}", strategyName, file.getFileName(), e.getMessage());
                }
            }
            log.info("[{}] Indexed {} documents", strategyName, indexed);

            // Evaluate on question corpus
            HybridRetriever retriever = new HybridRetriever(store, embeddingModel);
            log.info("[{}] Evaluating {} questions...", strategyName, Questions.ALL.size());

            for (Questions.Question q : Questions.ALL) {
                List<String> contexts = retriever.retrieve(Query.from(q.text()))
                        .stream()
                        .map(c -> c.textSegment().text())
                        .toList();

                BenchmarkJudge.Result result = judge.judge(q.text(), contexts, q.expectedHints());

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ts",             runTs);
                row.put("strategy",       strategyName);
                row.put("difficulty",     q.difficulty().name());
                row.put("question",       q.text());
                row.put("expected",       q.expectedHints());
                row.put("contextChunks",  contexts.size());
                row.put("hintCoverage",   Math.round(result.hintCoverage() * 1000.0) / 1000.0);
                row.put("llmScore",       result.judged() ? result.score() : null);
                row.put("rationale",      result.rationale());
                row.put("suggestsUnknown", result.suggestsUnknown());

                writer.write(mapper.writeValueAsString(row));
                writer.newLine();
                writer.flush();

                log.info("[{}] {} | hint={}% | llm={}",
                        strategyName,
                        q.difficulty(),
                        String.format("%.0f", result.hintCoverage() * 100),
                        result.judged() ? result.score() + "/10" : "N/A");
            }
        } finally {
            store.recreateIndex();
            store.close();
        }
    }

    /** Creates a minimal Project mock that returns a unique name (for LuceneEmbeddingStore path). */
    private static Project mockProjectNamed(String name) {
        Project mock = Mockito.mock(Project.class);
        Mockito.when(mock.getName()).thenReturn(name);
        Mockito.when(mock.getBasePath()).thenReturn(System.getProperty("user.home"));
        return mock;
    }

    private static List<Path> scanSourceFiles(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            log.warn("Source directory not found: {}", dir.toAbsolutePath());
            return List.of();
        }
        List<Path> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".kt"))
                    .forEach(result::add);
        }
        return result;
    }

    private static class StrategyStats {
        int    count;
        double totalHintCoverage;
        int    judgedCount;
        double totalLlmScore;
    }
}
