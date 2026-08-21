package fr.baretto.ollamassist.benchmark.judge;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * LLM-as-a-judge evaluator for RAG context quality.
 *
 * <p>Adapted from the external benchmark's {@code LLMJudge}. Uses Ollama structured output
 * (via LangChain4j AiServices) to score retrieved context relevance on a 0-10 scale.
 *
 * <p>Features:
 * <ul>
 *   <li>Few-shot examples in system prompt to anchor the scoring scale</li>
 *   <li>Position bias mitigation: context chunks are randomly shuffled before evaluation</li>
 *   <li>Deterministic {@code hintCoverage} metric computed independently of the LLM</li>
 * </ul>
 *
 * <p>Configuration via system properties:
 * <pre>
 *   -Dbenchmark.judge.model=qwen2.5:14b    (default)
 *   -Dbenchmark.judge.url=http://localhost:11434  (default)
 * </pre>
 */
public class BenchmarkJudge implements ContextJudge {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkJudge.class);

    private static final String DEFAULT_MODEL = "qwen2.5:14b";
    private static final String DEFAULT_URL   = "http://localhost:11434";

    public record JudgementOutput(
            int     score,
            String  rationale,
            boolean suggestsUnknown
    ) {}

    interface JudgeService {
        @SystemMessage("""
            You are an expert Java software engineer evaluating a RAG (Retrieval-Augmented Generation) system.
            Your task: assess whether the retrieved context contains enough information to accurately answer the question.

            Scoring scale (integer 0 to 10):
            - 10 : context fully and directly answers the question
            - 7-9 : context mostly answers with minor gaps
            - 4-6 : context partially answers, key information is missing
            - 1-3 : context is tangentially related but does not answer
            - 0   : context is irrelevant or empty

            Also determine if the context would force the system to answer "I don't know".

            --- FEW-SHOT EXAMPLES ---

            Example 1 (score 9):
            Question: What fields does Class0Service have?
            Context: Type: com.example.service.Class0Service
            Field: private String id;
            Field: private List<String> items;
            Field: private int counter;
            Method: public String getId()
            Method: public void processInput(String input)
            Expected output: {"score": 9, "rationale": "Context directly lists all three fields (id, items, counter) of Class0Service.", "suggestsUnknown": false}

            Example 2 (score 4):
            Question: What is the call chain starting from Class0Service?
            Context: Type: com.example.service.Class0Service
            Field: private String id;
            Method: public void processInput(String input)
            Expected output: {"score": 4, "rationale": "Context shows Class0Service but lacks the delegation chain to subsequent classes.", "suggestsUnknown": false}

            Example 3 (score 0):
            Question: Which classes in com.example.repository call classes in com.example.service?
            Context: Type: java.util.ArrayList
            Method: boolean add(Object element)
            Expected output: {"score": 0, "rationale": "Context contains only java.util standard library classes, completely unrelated.", "suggestsUnknown": true}

            --- END EXAMPLES ---

            Be strict and consistent. Respond with the structured output only.
            """)
        JudgementOutput evaluate(@UserMessage String userPrompt);
    }

    private final JudgeService judgeService;
    private final boolean      available;

    public BenchmarkJudge() {
        String model  = System.getProperty("benchmark.judge.model", DEFAULT_MODEL);
        String url    = System.getProperty("benchmark.judge.url", DEFAULT_URL);
        JudgeService svc = null;
        boolean ok = false;
        try {
            ChatModel chatModel = OllamaChatModel.builder()
                    .baseUrl(url)
                    .modelName(model)
                    .temperature(0.0)
                    .timeout(Duration.ofSeconds(120))
                    .build();
            svc = AiServices.create(JudgeService.class, chatModel);
            // Probe connectivity
            svc.evaluate("Question: ping\n\nRetrieved context: pong");
            ok = true;
            log.info("LLM judge ready: {} @ {}", model, url);
        } catch (Exception e) {
            log.warn("LLM judge unavailable ({}). Only hintCoverage will be computed.", e.getMessage());
        }
        this.judgeService = svc;
        this.available    = ok;
    }

    /** Constructor for direct injection (tests). */
    public BenchmarkJudge(ChatModel chatModel) {
        this.judgeService = AiServices.create(JudgeService.class, chatModel);
        this.available    = true;
    }

    @Override
    public String id() {
        return "legacy-aiservices/" + System.getProperty("benchmark.judge.model", DEFAULT_MODEL);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * Judges the quality of retrieved context for a given question.
     *
     * @param question      the question asked of the RAG system
     * @param contexts      list of retrieved context chunks
     * @param expectedHints FQN fragments expected to appear in a good context
     * @return judgement result with score, rationale, and hintCoverage
     */
    @Override
    public Judgement judge(String question, List<String> contexts, String[] expectedHints) {
        if (contexts.isEmpty()) {
            return Judgement.noContext();
        }

        String contextText = ContextPreparation.shuffleAndJoin(contexts);
        double hintCoverage = ContextPreparation.hintCoverage(contextText, expectedHints);

        if (!available || judgeService == null) {
            return Judgement.notJudged("Judge not available", hintCoverage);
        }

        try {
            JudgementOutput output = judgeService.evaluate(
                    "Question: %s\n\nRetrieved context:\n%s".formatted(question, contextText));
            int score = Math.clamp(output.score(), 0, 10);
            return new Judgement(score, output.rationale(), output.suggestsUnknown(), hintCoverage, true);
        } catch (Exception e) {
            log.warn("Judge call failed for '{}': {}", question, e.getMessage());
            return Judgement.notJudged("Judge error: " + e.getMessage(), hintCoverage);
        }
    }

}
