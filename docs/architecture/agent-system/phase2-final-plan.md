# Phase 2: Modèle de Domaine - Plan Final (Architecture LangChain4J)

**Objectif:** Créer les classes de base réutilisables avec tests (approche TDD) en utilisant au maximum LangChain4J

**Durée estimée:** 2-3 jours

**Critères d'acceptance:**
- ✅ Couverture tests >80%
- ✅ Tous les tests passent
- ✅ Code compilable et utilisable par Phase 3 (SupervisorAgent)
- ✅ Documentation Javadoc complète
- ✅ Intégration LangChain4J validée

---

## 🎯 Architecture Adoptée

### Utilisation de LangChain4J

1. ✅ **SupervisorAgent** - Remplace notre Orchestrator custom
2. ✅ **@Agent** - Interfaces pour agents spécialisés
3. ✅ **AgenticScope** - Communication inter-agents (on wrappe)
4. ✅ **HumanInTheLoop** - Validation utilisateur
5. ✅ **@Tool** - Tools avec observability custom
6. ✅ **Workflows** - Sequential, parallel, conditional natifs

### Ce qu'on Garde (Notre Valeur Ajoutée)

1. ✅ **SourceReference** - Traçabilité détaillée (fichiers, lignes)
2. ✅ **ExecutionTrace/StepTrace** - Observabilité riche
3. ✅ **ExecutionMetrics** - Métriques détaillées (tokens, coût)
4. ✅ **Plan/PlanStep** - Affichage plan avant exécution
5. ✅ **AgentTool interface** - Metadata custom (requiresApproval, owner)
6. ✅ **ToolResult** - Résultats avec sources

---

## Stratégie d'Implémentation

### Principe TDD (Test-Driven Development)

Pour **chaque** classe/interface :

1. **RED** 🔴 : Écrire le test (qui échoue)
2. **GREEN** 🟢 : Écrire le code minimal pour passer le test
3. **REFACTOR** 🔵 : Améliorer le code tout en gardant les tests verts

### Ordre d'Implémentation Révisé

```
1. Enums (pas de dépendances)
   └── AgentType, ExecutionState, StepState, SourceType

2. Value Objects simples
   └── SourceReference

3. Metrics (dépend de Duration, Instant uniquement)
   └── StepMetrics, ExecutionMetrics

4. Tools
   └── ToolParameter, ToolResult, AgentTool

5. Plan (pour structured output avant exécution)
   └── PlanStep, Plan

6. Observability (dépend de ToolResult)
   └── StepTrace, ExecutionTrace

7. LangChain4J Integration (nouveau)
   └── OllamAssistAgenticScope, AgentToolBridge

8. Validation finale
   └── Tests d'intégration avec AgenticScope
```

---

## Détail par Sous-Phase

---

## 2.1: Modèle d'Observabilité

**Durée:** 1 jour

**Objectif:** Classes pour tracer l'exécution et les sources

### 2.1.1: SourceType Enum

**Test:**
```java
@Test
void sourceType_shouldHaveAllExpectedValues() {
    assertThat(SourceType.values()).containsExactlyInAnyOrder(
        SourceType.FILE,
        SourceType.URL,
        SourceType.CLASS,
        SourceType.COMMIT,
        SourceType.DOCUMENTATION,
        SourceType.SNIPPET,
        SourceType.EMBEDDING
    );
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.observability;

/**
 * Types of sources that can be referenced in agent execution.
 */
public enum SourceType {
    FILE,           // Local file
    URL,            // Web URL
    CLASS,          // Java class
    COMMIT,         // Git commit
    DOCUMENTATION,  // Documentation file
    SNIPPET,        // Code snippet (no specific file)
    EMBEDDING       // From vector store
}
```

**Fichiers:**
- `src/main/java/fr/baretto/ollamassist/agent/observability/SourceType.java`
- `src/test/java/fr/baretto/ollamassist/agent/observability/SourceTypeTest.java`

---

### 2.1.2: SourceReference Class

**Tests (5 tests):**

```java
@Test
void builder_shouldCreateSourceReferenceWithAllFields() {
    SourceReference ref = SourceReference.builder()
        .uri("/path/to/file.java")
        .type(SourceType.FILE)
        .lineStart(10)
        .lineEnd(20)
        .snippet("public void test() {}")
        .description("Test method")
        .relevanceScore(0.95)
        .sourceAgent("RAG_SEARCH")
        .timestamp(Instant.now())
        .build();

    assertThat(ref.getUri()).isEqualTo("/path/to/file.java");
    assertThat(ref.getType()).isEqualTo(SourceType.FILE);
    assertThat(ref.getLineStart()).isEqualTo(10);
    // ... autres assertions
}

@Test
void getDisplayName_shouldReturnFileNameForFileType() {
    SourceReference ref = SourceReference.builder()
        .uri("/path/to/MyClass.java")
        .type(SourceType.FILE)
        .build();

    assertThat(ref.getDisplayName()).isEqualTo("MyClass.java");
}

@Test
void getDisplayName_shouldReturnUriForNonFileType() {
    SourceReference ref = SourceReference.builder()
        .uri("https://example.com/doc")
        .type(SourceType.URL)
        .build();

    assertThat(ref.getDisplayName()).isEqualTo("https://example.com/doc");
}

@Test
void getNavigationUrl_shouldIncludeLineNumberForFiles() {
    SourceReference ref = SourceReference.builder()
        .uri("/path/to/file.java")
        .type(SourceType.FILE)
        .lineStart(42)
        .build();

    assertThat(ref.getNavigationUrl()).isEqualTo("file:///path/to/file.java:42");
}

@Test
void isNavigable_shouldReturnTrueForFileClassCommit() {
    assertThat(SourceReference.builder().type(SourceType.FILE).build().isNavigable())
        .isTrue();
    assertThat(SourceReference.builder().type(SourceType.CLASS).build().isNavigable())
        .isTrue();
    assertThat(SourceReference.builder().type(SourceType.COMMIT).build().isNavigable())
        .isTrue();
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.observability;

import lombok.Builder;
import lombok.Data;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;

/**
 * References to source code, files, or documentation used during agent execution.
 * Provides navigation capabilities for IntelliJ integration.
 */
@Data
@Builder
public class SourceReference {
    private String uri;                      // File path, URL, or identifier
    private SourceType type;                 // Type of source
    private Integer lineStart;               // Start line (1-based)
    private Integer lineEnd;                 // End line (inclusive)
    private Integer columnStart;             // Start column (optional)
    private Integer columnEnd;               // End column (optional)
    private String snippet;                  // Code/text excerpt (max 500 chars)
    private String description;              // Human-readable description
    private Double relevanceScore;           // Confidence/relevance (0.0-1.0)
    private String sourceAgent;              // Agent that produced this source
    private Instant timestamp;               // When source was created
    private Map<String, String> metadata;    // Additional metadata

    /**
     * Returns display name for UI (filename for files, URI otherwise).
     */
    public String getDisplayName() {
        if (type == SourceType.FILE) {
            return Paths.get(uri).getFileName().toString();
        }
        return uri;
    }

    /**
     * Returns navigation URL for IntelliJ.
     */
    public String getNavigationUrl() {
        if (type == SourceType.FILE && lineStart != null) {
            return String.format("file://%s:%d", uri, lineStart);
        }
        return uri;
    }

    /**
     * Returns true if this source can be navigated to in IntelliJ.
     */
    public boolean isNavigable() {
        return type == SourceType.FILE ||
               type == SourceType.CLASS ||
               type == SourceType.COMMIT;
    }
}
```

**Fichiers:**
- `src/main/java/fr/baretto/ollamassist/agent/observability/SourceReference.java`
- `src/test/java/fr/baretto/ollamassist/agent/observability/SourceReferenceTest.java`

---

### 2.1.3: StepState Enum

**Test:**
```java
@Test
void stepState_shouldHaveAllExpectedValues() {
    assertThat(StepState.values()).containsExactlyInAnyOrder(
        StepState.PENDING,
        StepState.RUNNING,
        StepState.COMPLETED,
        StepState.FAILED
    );
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.observability;

/**
 * Execution state of a single step.
 */
public enum StepState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
```

**Fichiers:**
- `src/main/java/fr/baretto/ollamassist/agent/observability/StepState.java`
- `src/test/java/fr/baretto/ollamassist/agent/observability/StepStateTest.java`

---

### 2.1.4: StepMetrics Class

**Tests:**
```java
@Test
void recordLLMCall_shouldUpdateMetrics() {
    StepMetrics metrics = new StepMetrics();

    metrics.recordLLMCall(100, 200, Duration.ofSeconds(2));

    assertThat(metrics.getTotalLLMCalls()).isEqualTo(1);
    assertThat(metrics.getTotalTokensInput()).isEqualTo(100);
    assertThat(metrics.getTotalTokensOutput()).isEqualTo(200);
    assertThat(metrics.getLlmTotalTime()).isEqualTo(Duration.ofSeconds(2));
}

@Test
void recordLLMCall_shouldAccumulateMultipleCalls() {
    StepMetrics metrics = new StepMetrics();

    metrics.recordLLMCall(100, 150, Duration.ofSeconds(1));
    metrics.recordLLMCall(50, 100, Duration.ofSeconds(1));

    assertThat(metrics.getTotalLLMCalls()).isEqualTo(2);
    assertThat(metrics.getTotalTokensInput()).isEqualTo(150);
    assertThat(metrics.getTotalTokensOutput()).isEqualTo(250);
    assertThat(metrics.getLlmTotalTime()).isEqualTo(Duration.ofSeconds(2));
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.observability;

import lombok.Data;

import java.time.Duration;

/**
 * Metrics for a single step execution.
 */
@Data
public class StepMetrics {
    private int totalLLMCalls = 0;
    private int totalTokensInput = 0;
    private int totalTokensOutput = 0;
    private Duration llmTotalTime = Duration.ZERO;
    private Duration toolExecutionTime = Duration.ZERO;

    public void recordLLMCall(int inputTokens, int outputTokens, Duration duration) {
        this.totalLLMCalls++;
        this.totalTokensInput += inputTokens;
        this.totalTokensOutput += outputTokens;
        this.llmTotalTime = llmTotalTime.plus(duration);
    }
}
```

**Fichiers:**
- `src/main/java/fr/baretto/ollamassist/agent/observability/StepMetrics.java`
- `src/test/java/fr/baretto/ollamassist/agent/observability/StepMetricsTest.java`

---

### 2.1.5: ExecutionMetrics Class

**Tests:**
```java
@Test
void recordLLMCall_shouldCalculateEstimatedCost() {
    ExecutionMetrics metrics = new ExecutionMetrics();

    metrics.recordLLMCall(500, 500, Duration.ofSeconds(1));

    assertThat(metrics.getEstimatedCost()).isCloseTo(0.01, within(0.001));
}

@Test
void incrementFailures_shouldIncreaseCounter() {
    ExecutionMetrics metrics = new ExecutionMetrics();

    metrics.incrementFailures();
    metrics.incrementFailures();

    assertThat(metrics.getFailures()).isEqualTo(2);
}

@Test
void getAverageTokensPerCall_shouldCalculateCorrectly() {
    ExecutionMetrics metrics = new ExecutionMetrics();

    metrics.recordLLMCall(100, 200, Duration.ofSeconds(1));
    metrics.recordLLMCall(150, 250, Duration.ofSeconds(1));

    assertThat(metrics.getAverageTokensPerCall()).isEqualTo(350.0);
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.observability;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Duration;

/**
 * Metrics for entire execution.
 * Extends StepMetrics with additional execution-level metrics.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionMetrics extends StepMetrics {
    private double estimatedCost = 0.0;
    private Duration validationTime = Duration.ZERO;
    private long peakMemoryUsage = 0;
    private int embeddingSearches = 0;
    private int embeddingHits = 0;
    private double averageRelevanceScore = 0.0;
    private int retries = 0;
    private int failures = 0;

    @Override
    public void recordLLMCall(int inputTokens, int outputTokens, Duration duration) {
        super.recordLLMCall(inputTokens, outputTokens, duration);
        // Estimate cost: $0.01 per 1K tokens (example rate)
        this.estimatedCost += (inputTokens + outputTokens) / 1000.0 * 0.01;
    }

    public void incrementFailures() {
        this.failures++;
    }

    public double getAverageTokensPerCall() {
        return getTotalLLMCalls() > 0
            ? (double) (getTotalTokensInput() + getTotalTokensOutput()) / getTotalLLMCalls()
            : 0.0;
    }
}
```

**Fichiers:**
- `src/main/java/fr/baretto/ollamassist/agent/observability/ExecutionMetrics.java`
- `src/test/java/fr/baretto/ollamassist/agent/observability/ExecutionMetricsTest.java`

---

### 2.1.6: StepTrace Class

**Tests (5 tests):**

```java
@Test
void recordStart_shouldSetStateAndStartTime() {
    StepTrace trace = StepTrace.builder()
        .stepId("step-1")
        .logs(new ArrayList<>())
        .build();

    trace.recordStart();

    assertThat(trace.getState()).isEqualTo(StepState.RUNNING);
    assertThat(trace.getStartTime()).isNotNull();
}

@Test
void recordSuccess_shouldSetStateAndCalculateDuration() throws InterruptedException {
    StepTrace trace = StepTrace.builder()
        .stepId("step-1")
        .logs(new ArrayList<>())
        .build();

    trace.recordStart();
    Thread.sleep(100);

    ToolResult result = ToolResult.success("output", List.of());
    trace.recordSuccess(result);

    assertThat(trace.getState()).isEqualTo(StepState.COMPLETED);
    assertThat(trace.getEndTime()).isNotNull();
    assertThat(trace.getDuration()).isNotNull();
    assertThat(trace.getDuration().toMillis()).isGreaterThanOrEqualTo(100);
}

@Test
void recordError_shouldSetStateAndErrorMessage() {
    StepTrace trace = StepTrace.builder()
        .stepId("step-1")
        .logs(new ArrayList<>())
        .build();

    trace.recordStart();
    trace.recordError(new RuntimeException("Test error"));

    assertThat(trace.getState()).isEqualTo(StepState.FAILED);
    assertThat(trace.getErrorMessage()).isEqualTo("Test error");
    assertThat(trace.getLogs()).anyMatch(log -> log.contains("ERROR: Test error"));
}

@Test
void addLog_shouldAppendLogWithTimestamp() {
    StepTrace trace = StepTrace.builder()
        .stepId("step-1")
        .logs(new ArrayList<>())
        .build();

    trace.addLog("Test log message");

    assertThat(trace.getLogs()).hasSize(1);
    assertThat(trace.getLogs().get(0)).contains("Test log message");
    assertThat(trace.getLogs().get(0)).matches("\\[.*\\] Test log message");
}

@Test
void recordReasoning_shouldSetReasoningAndAddLog() {
    StepTrace trace = StepTrace.builder()
        .stepId("step-1")
        .logs(new ArrayList<>())
        .build();

    trace.recordReasoning("LLM decided to search code first");

    assertThat(trace.getReasoning()).isEqualTo("LLM decided to search code first");
    assertThat(trace.getLogs()).anyMatch(log -> log.contains("REASONING:"));
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.observability;

import fr.baretto.ollamassist.agent.model.AgentType;
import fr.baretto.ollamassist.agent.tool.ToolResult;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Trace of a single step execution in an agent workflow.
 * Captures input, output, reasoning, sources, and metrics.
 */
@Data
@Builder
public class StepTrace {
    private String stepId;
    private int stepNumber;
    private String executionId;

    private AgentType agentType;
    private String toolName;
    private String action;

    private Instant startTime;
    private Instant endTime;
    private Duration duration;

    private StepState state;
    private String errorMessage;

    private Map<String, Object> inputParameters;
    private ToolResult output;

    private String reasoning;
    @Builder.Default
    private List<SourceReference> sources = new ArrayList<>();
    @Builder.Default
    private List<String> logs = new ArrayList<>();

    @Builder.Default
    private StepMetrics metrics = new StepMetrics();

    public void recordStart() {
        this.startTime = Instant.now();
        this.state = StepState.RUNNING;
    }

    public void recordSuccess(ToolResult result) {
        this.endTime = Instant.now();
        this.duration = Duration.between(startTime, endTime);
        this.state = StepState.COMPLETED;
        this.output = result;
        this.sources = result.getSources();
    }

    public void recordError(Exception e) {
        this.endTime = Instant.now();
        this.duration = Duration.between(startTime, endTime);
        this.state = StepState.FAILED;
        this.errorMessage = e.getMessage();
        addLog("ERROR: " + e.getMessage());
    }

    public void addLog(String message) {
        logs.add(String.format("[%s] %s", Instant.now().toString(), message));
    }

    public void recordReasoning(String reasoning) {
        this.reasoning = reasoning;
        addLog("REASONING: " + reasoning);
    }
}
```

**Fichiers:**
- `src/main/java/fr/baretto/ollamassist/agent/observability/StepTrace.java`
- `src/test/java/fr/baretto/ollamassist/agent/observability/StepTraceTest.java`

---

### 2.1.7: ExecutionTrace Class

**Tests:**

```java
@Test
void addStepTrace_shouldAddToTracesAndAccumulateSources() {
    ExecutionTrace execTrace = ExecutionTrace.builder()
        .executionId("exec-1")
        .stepTraces(new ArrayList<>())
        .allSources(new ArrayList<>())
        .build();

    SourceReference source = SourceReference.builder()
        .uri("file.java")
        .type(SourceType.FILE)
        .build();

    StepTrace step = StepTrace.builder()
        .stepId("step-1")
        .sources(List.of(source))
        .build();

    execTrace.addStepTrace(step);

    assertThat(execTrace.getStepTraces()).hasSize(1);
    assertThat(execTrace.getAllSources()).hasSize(1);
    assertThat(execTrace.getAllSources()).contains(source);
}

@Test
void getCompletedSteps_shouldCountOnlyCompletedSteps() {
    ExecutionTrace execTrace = ExecutionTrace.builder()
        .stepTraces(new ArrayList<>())
        .build();

    execTrace.getStepTraces().add(
        StepTrace.builder().stepId("1").state(StepState.COMPLETED).build()
    );
    execTrace.getStepTraces().add(
        StepTrace.builder().stepId("2").state(StepState.FAILED).build()
    );
    execTrace.getStepTraces().add(
        StepTrace.builder().stepId("3").state(StepState.COMPLETED).build()
    );

    assertThat(execTrace.getCompletedSteps()).isEqualTo(2);
}

@Test
void getTotalSteps_shouldReturnTotalCount() {
    ExecutionTrace execTrace = ExecutionTrace.builder()
        .stepTraces(new ArrayList<>())
        .build();

    execTrace.getStepTraces().add(StepTrace.builder().stepId("1").build());
    execTrace.getStepTraces().add(StepTrace.builder().stepId("2").build());

    assertThat(execTrace.getTotalSteps()).isEqualTo(2);
}

@Test
void getFailedSteps_shouldReturnOnlyFailedSteps() {
    ExecutionTrace execTrace = ExecutionTrace.builder()
        .stepTraces(new ArrayList<>())
        .build();

    StepTrace failed1 = StepTrace.builder().stepId("1").state(StepState.FAILED).build();
    StepTrace completed = StepTrace.builder().stepId("2").state(StepState.COMPLETED).build();
    StepTrace failed2 = StepTrace.builder().stepId("3").state(StepState.FAILED).build();

    execTrace.getStepTraces().addAll(List.of(failed1, completed, failed2));

    assertThat(execTrace.getFailedSteps()).containsExactly(failed1, failed2);
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.observability;

import fr.baretto.ollamassist.agent.model.ExecutionState;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Complete trace of an agent execution workflow.
 * Contains all step traces, aggregated sources, and metrics.
 */
@Data
@Builder
public class ExecutionTrace {
    private String executionId;
    private String userRequestId;

    private Instant startTime;
    private Instant endTime;
    private Duration totalDuration;

    private ExecutionState finalState;
    private String errorMessage;

    @Builder.Default
    private List<StepTrace> stepTraces = new ArrayList<>();

    @Builder.Default
    private List<SourceReference> allSources = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Builder.Default
    private ExecutionMetrics metrics = new ExecutionMetrics();

    public void addStepTrace(StepTrace stepTrace) {
        stepTraces.add(stepTrace);
        allSources.addAll(stepTrace.getSources());
    }

    public int getCompletedSteps() {
        return (int) stepTraces.stream()
            .filter(s -> s.getState() == StepState.COMPLETED)
            .count();
    }

    public int getTotalSteps() {
        return stepTraces.size();
    }

    public List<StepTrace> getFailedSteps() {
        return stepTraces.stream()
            .filter(s -> s.getState() == StepState.FAILED)
            .collect(Collectors.toList());
    }
}
```

**Fichiers:**
- `src/main/java/fr/baretto/ollamassist/agent/observability/ExecutionTrace.java`
- `src/test/java/fr/baretto/ollamassist/agent/observability/ExecutionTraceTest.java`

---

## 2.2: Modèle de Plan

**Durée:** 0.5 jour

**Objectif:** Classes pour représenter les plans (structured output avant exécution)

### 2.2.1: AgentType Enum

**Test:**
```java
@Test
void agentType_shouldHaveAllExpectedValues() {
    assertThat(AgentType.values()).containsExactlyInAnyOrder(
        AgentType.ORCHESTRATOR,
        AgentType.RAG_SEARCH,
        AgentType.GIT,
        AgentType.REFACTORING,
        AgentType.CODE_ANALYSIS
    );
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.model;

/**
 * Types of agents in the system.
 * Used for routing and identification.
 */
public enum AgentType {
    ORCHESTRATOR,
    RAG_SEARCH,
    GIT,
    REFACTORING,
    CODE_ANALYSIS
}
```

**Fichiers:**
- `src/main/java/fr/baretto/ollamassist/agent/model/AgentType.java`
- `src/test/java/fr/baretto/ollamassist/agent/model/AgentTypeTest.java`

---

### 2.2.2: ExecutionState Enum

**Tests:**
```java
@Test
void executionState_shouldHaveAllExpectedValues() {
    assertThat(ExecutionState.values()).containsExactlyInAnyOrder(
        ExecutionState.PENDING,
        ExecutionState.RUNNING,
        ExecutionState.PAUSED,
        ExecutionState.COMPLETED,
        ExecutionState.FAILED,
        ExecutionState.CANCELLED
    );
}

@Test
void isTerminal_shouldReturnTrueForFinalStates() {
    assertThat(ExecutionState.COMPLETED.isTerminal()).isTrue();
    assertThat(ExecutionState.FAILED.isTerminal()).isTrue();
    assertThat(ExecutionState.CANCELLED.isTerminal()).isTrue();
}

@Test
void isTerminal_shouldReturnFalseForNonFinalStates() {
    assertThat(ExecutionState.PENDING.isTerminal()).isFalse();
    assertThat(ExecutionState.RUNNING.isTerminal()).isFalse();
    assertThat(ExecutionState.PAUSED.isTerminal()).isFalse();
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.model;

/**
 * Execution state of the entire workflow.
 */
public enum ExecutionState {
    PENDING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    /**
     * Returns true if this is a terminal state (execution finished).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
```

**Fichiers:**
- `src/main/java/fr/baretto/ollamassist/agent/model/ExecutionState.java`
- `src/test/java/fr/baretto/ollamassist/agent/model/ExecutionStateTest.java`

---

### 2.2.3: PlanStep Record

**Tests:**
```java
@Test
void planStep_shouldCreateWithAllFields() {
    PlanStep step = new PlanStep(
        1,
        AgentType.RAG_SEARCH,
        "Search for TODO comments",
        "RAG can find patterns across indexed files",
        "List of files with TODOs",
        List.of()
    );

    assertThat(step.stepNumber()).isEqualTo(1);
    assertThat(step.agentType()).isEqualTo(AgentType.RAG_SEARCH);
    assertThat(step.action()).isEqualTo("Search for TODO comments");
}

@Test
void planStep_shouldSupportDependencies() {
    PlanStep step = new PlanStep(
        2,
        AgentType.CODE_ANALYSIS,
        "Analyze complexity",
        "Prioritize refactoring",
        "Metrics report",
        List.of(1)
    );

    assertThat(step.dependencies()).containsExactly(1);
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A single step in an execution plan.
 * Generated by the orchestrator via structured output.
 */
public record PlanStep(
    @Schema(description = "Step number starting from 1")
    @JsonProperty(required = true)
    int stepNumber,

    @Schema(description = "Agent type to use")
    @JsonProperty(required = true)
    AgentType agentType,

    @Schema(description = "Action to perform")
    @JsonProperty(required = true)
    String action,

    @Schema(description = "Why this step is needed")
    @JsonProperty(required = true)
    String justification,

    @Schema(description = "Expected output")
    String expectedOutput,

    @Schema(description = "Step numbers this depends on")
    List<Integer> dependencies
) {}
```

**Fichiers:**
- `src/main/java/fr/baretto/ollamassist/agent/model/PlanStep.java`
- `src/test/java/fr/baretto/ollamassist/agent/model/PlanStepTest.java`

---

### 2.2.4: Plan Record

**Tests:**
```java
@Test
void plan_shouldCreateWithAllFields() {
    PlanStep step1 = new PlanStep(1, AgentType.RAG_SEARCH, "a", "j", "o", List.of());
    PlanStep step2 = new PlanStep(2, AgentType.GIT, "a", "j", "o", List.of(1));

    Plan plan = new Plan("Find TODOs", List.of(step1, step2), 60);

    assertThat(plan.goal()).isEqualTo("Find TODOs");
    assertThat(plan.steps()).hasSize(2);
    assertThat(plan.estimatedDuration()).isEqualTo(60);
}

@Test
void plan_shouldValidateNonEmptyGoal() {
    assertThatThrownBy(() -> new Plan("", List.of(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Goal cannot be null or empty");
}

@Test
void plan_shouldValidateNonEmptySteps() {
    assertThatThrownBy(() -> new Plan("Goal", List.of(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Plan must have at least one step");
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Execution plan generated by the orchestrator.
 * Displayed to user before execution for approval.
 */
public record Plan(
    @Schema(description = "Overall goal of the plan")
    @JsonProperty(required = true)
    String goal,

    @Schema(description = "Steps to execute")
    @JsonProperty(required = true)
    List<PlanStep> steps,

    @Schema(description = "Estimated duration in seconds")
    Integer estimatedDuration
) {
    public Plan {
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("Goal cannot be null or empty");
        }
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("Plan must have at least one step");
        }
    }
}
```

**Fichiers:**
- `src/main/java/fr/baretto/ollamassist/agent/model/Plan.java`
- `src/test/java/fr/baretto/ollamassist/agent/model/PlanTest.java`

---

## 2.3: Tools

**Durée:** 0.5 jour

**Objectif:** Interfaces et classes pour tools avec observability custom

### 2.3.1: ToolParameter Record

**Tests:**
```java
@Test
void toolParameter_shouldCreateWithAllFields() {
    ToolParameter param = new ToolParameter("query", "string", "Search query", true);

    assertThat(param.name()).isEqualTo("query");
    assertThat(param.type()).isEqualTo("string");
    assertThat(param.description()).isEqualTo("Search query");
    assertThat(param.required()).isTrue();
}

@Test
void toolParameter_shouldDefaultToRequired() {
    ToolParameter param = new ToolParameter("query", "string", "desc", null);

    assertThat(param.isRequired()).isTrue();
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.tool;

/**
 * Parameter definition for an agent tool.
 */
public record ToolParameter(
    String name,
    String type,
    String description,
    Boolean required
) {
    public ToolParameter {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Type cannot be null or empty");
        }
        if (required == null) {
            required = true;
        }
    }

    public boolean isRequired() {
        return required != null && required;
    }
}
```

**Fichiers:**
- `src/main/java/fr/baretto/ollamassist/agent/tool/ToolParameter.java`
- `src/test/java/fr/baretto/ollamassist/agent/tool/ToolParameterTest.java`

---

### 2.3.2: ToolResult Class

**Tests:**
```java
@Test
void success_shouldCreateSuccessfulResult() {
    SourceReference source = SourceReference.builder()
        .uri("file.java")
        .type(SourceType.FILE)
        .build();

    ToolResult result = ToolResult.success("Output", List.of(source));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getOutput()).isEqualTo("Output");
    assertThat(result.getSources()).containsExactly(source);
}

@Test
void error_shouldCreateFailedResult() {
    ToolResult result = ToolResult.error("Error message");

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getErrorMessage()).isEqualTo("Error message");
}

@Test
void withStructuredData_shouldAddStructuredData() {
    ToolResult result = ToolResult.success("Out", List.of())
        .withStructuredData(Map.of("key", "value"));

    assertThat(result.getStructuredData()).isNotNull();
}

@Test
void withMetadata_shouldAddMetadata() {
    ToolResult result = ToolResult.success("Out", List.of())
        .withMetadata(Map.of("time", "2s"));

    assertThat(result.getMetadata()).containsEntry("time", "2s");
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.tool;

import fr.baretto.ollamassist.agent.observability.SourceReference;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of a tool execution.
 * Contains output, sources, and metadata for observability.
 */
@Data
@Builder
public class ToolResult {
    private String output;
    private Object structuredData;
    private List<SourceReference> sources;
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    private boolean success;
    private String errorMessage;

    public static ToolResult success(String output, List<SourceReference> sources) {
        return ToolResult.builder()
            .output(output)
            .sources(sources)
            .success(true)
            .build();
    }

    public static ToolResult error(String errorMessage) {
        return ToolResult.builder()
            .errorMessage(errorMessage)
            .success(false)
            .build();
    }

    public ToolResult withStructuredData(Object data) {
        this.structuredData = data;
        return this;
    }

    public ToolResult withMetadata(Map<String, Object> metadata) {
        this.metadata.putAll(metadata);
        return this;
    }
}
```

**Fichiers:**
- `src/main/java/fr/baretto/ollamassist/agent/tool/ToolResult.java`
- `src/test/java/fr/baretto/ollamassist/agent/tool/ToolResultTest.java`

---

### 2.3.3: AgentTool Interface

**Tests (avec mocks):**
```java
@Test
void agentTool_shouldProvideBasicInformation() {
    AgentTool tool = mock(AgentTool.class);
    when(tool.getId()).thenReturn("search-code");
    when(tool.getName()).thenReturn("searchCode");
    when(tool.requiresUserApproval()).thenReturn(false);
    when(tool.getOwnerAgent()).thenReturn(AgentType.RAG_SEARCH);

    assertThat(tool.getId()).isEqualTo("search-code");
    assertThat(tool.getName()).isEqualTo("searchCode");
    assertThat(tool.requiresUserApproval()).isFalse();
}

@Test
void agentTool_shouldHaveParameters() {
    AgentTool tool = mock(AgentTool.class);
    ToolParameter param = new ToolParameter("query", "string", "desc", true);
    when(tool.getParameters()).thenReturn(List.of(param));

    assertThat(tool.getParameters()).hasSize(1);
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.tool;

import fr.baretto.ollamassist.agent.model.AgentType;
import fr.baretto.ollamassist.agent.observability.StepTrace;

import java.util.List;
import java.util.Map;

/**
 * Interface for agent tools.
 * Tools are executable actions with @Tool annotation.
 * This interface provides metadata and custom observability.
 */
public interface AgentTool {
    String getId();
    String getName();
    String getDescription();
    List<ToolParameter> getParameters();

    /**
     * Executes the tool.
     * The actual @Tool method should call this with observability support.
     */
    ToolResult execute(Map<String, Object> parameters, StepTrace trace);

    /**
     * Whether this tool requires user approval (for destructive actions).
     */
    boolean requiresUserApproval();

    /**
     * Agent that owns this tool.
     */
    AgentType getOwnerAgent();
}
```

**Fichiers:**
- `src/main/java/fr/baretto/ollamassist/agent/tool/AgentTool.java`
- `src/test/java/fr/baretto/ollamassist/agent/tool/AgentToolTest.java`

---

## 2.4: LangChain4J Integration (Nouveau)

**Durée:** 0.5 jour

**Objectif:** Wrapper AgenticScope et bridges pour integration

### 2.4.1: OllamAssistAgenticScope

**Tests:**
```java
@Test
void setVariable_shouldStoreInNativeScopeAndTrack() {
    Project project = mock(Project.class);
    OllamAssistAgenticScope scope = new OllamAssistAgenticScope(project);

    ToolResult result = ToolResult.success("output", List.of());
    scope.setVariable("searchResults", result);

    assertThat(scope.getVariable("searchResults")).isEqualTo(result.getOutput());
    assertThat(scope.getExecutionTrace().getAllSources()).isEmpty();
}

@Test
void setVariable_shouldAccumulateSources() {
    Project project = mock(Project.class);
    OllamAssistAgenticScope scope = new OllamAssistAgenticScope(project);

    SourceReference source = SourceReference.builder()
        .uri("file.java")
        .type(SourceType.FILE)
        .build();

    ToolResult result = ToolResult.success("out", List.of(source));
    scope.setVariable("results", result);

    assertThat(scope.getExecutionTrace().getAllSources()).hasSize(1);
    assertThat(scope.getExecutionTrace().getAllSources()).contains(source);
}

@Test
void onAgentStart_shouldCreateStepTrace() {
    Project project = mock(Project.class);
    OllamAssistAgenticScope scope = new OllamAssistAgenticScope(project);

    scope.onAgentStart("RagSearchAgent");

    assertThat(scope.getCurrentStepTrace()).isNotNull();
    assertThat(scope.getCurrentStepTrace().getState()).isEqualTo(StepState.RUNNING);
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.integration;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.model.AgentType;
import fr.baretto.ollamassist.agent.model.ExecutionState;
import fr.baretto.ollamassist.agent.observability.ExecutionTrace;
import fr.baretto.ollamassist.agent.observability.StepTrace;
import fr.baretto.ollamassist.agent.tool.ToolResult;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wrapper around LangChain4J AgenticScope.
 * Adds OllamAssist-specific observability and source tracking.
 */
@Getter
public class OllamAssistAgenticScope {
    private final Project project;
    private final Map<String, Object> variables;  // Simulates AgenticScope
    private final ExecutionTrace executionTrace;
    private StepTrace currentStepTrace;
    private int stepCounter = 0;

    public OllamAssistAgenticScope(Project project) {
        this.project = project;
        this.variables = new HashMap<>();
        this.executionTrace = ExecutionTrace.builder()
            .executionId(UUID.randomUUID().toString())
            .startTime(Instant.now())
            .state(ExecutionState.RUNNING)
            .stepTraces(new ArrayList<>())
            .allSources(new ArrayList<>())
            .build();
    }

    /**
     * Set variable in scope (equivalent to AgenticScope.set).
     * If value is ToolResult, accumulate sources.
     */
    public void setVariable(String key, Object value) {
        variables.put(key, value instanceof ToolResult tr ? tr.getOutput() : value);

        if (value instanceof ToolResult tr) {
            executionTrace.getAllSources().addAll(tr.getSources());
        }
    }

    /**
     * Get variable from scope.
     */
    public Object getVariable(String key) {
        return variables.get(key);
    }

    /**
     * Called when an agent starts execution.
     */
    public void onAgentStart(String agentName) {
        stepCounter++;
        currentStepTrace = StepTrace.builder()
            .stepId(UUID.randomUUID().toString())
            .stepNumber(stepCounter)
            .executionId(executionTrace.getExecutionId())
            .agentType(parseAgentType(agentName))
            .action("Executing " + agentName)
            .logs(new ArrayList<>())
            .build();

        currentStepTrace.recordStart();
        executionTrace.addStepTrace(currentStepTrace);
    }

    /**
     * Called when an agent completes.
     */
    public void onAgentComplete(Object agent, Object result) {
        if (currentStepTrace != null && result instanceof ToolResult tr) {
            currentStepTrace.recordSuccess(tr);
        }
    }

    /**
     * Called when an agent fails.
     */
    public void onAgentError(Object agent, Throwable error) {
        if (currentStepTrace != null) {
            currentStepTrace.recordError(new RuntimeException(error));
        }
    }

    private AgentType parseAgentType(String agentName) {
        if (agentName.contains("Rag")) return AgentType.RAG_SEARCH;
        if (agentName.contains("Git")) return AgentType.GIT;
        if (agentName.contains("Refactoring")) return AgentType.REFACTORING;
        if (agentName.contains("Analysis")) return AgentType.CODE_ANALYSIS;
        return AgentType.ORCHESTRATOR;
    }
}
```

**Fichiers:**
- `src/main/java/fr/baretto/ollamassist/agent/integration/OllamAssistAgenticScope.java`
- `src/test/java/fr/baretto/ollamassist/agent/integration/OllamAssistAgenticScopeTest.java`

---

## Checklist Complète Phase 2

### 2.1: Observabilité
- [ ] 2.1.1: SourceType enum + test
- [ ] 2.1.2: SourceReference class + 5 tests
- [ ] 2.1.3: StepState enum + test
- [ ] 2.1.4: StepMetrics class + 2 tests
- [ ] 2.1.5: ExecutionMetrics class + 3 tests
- [ ] 2.1.6: StepTrace class + 5 tests
- [ ] 2.1.7: ExecutionTrace class + 4 tests

### 2.2: Plan
- [ ] 2.2.1: AgentType enum + test
- [ ] 2.2.2: ExecutionState enum + 3 tests
- [ ] 2.2.3: PlanStep record + 2 tests
- [ ] 2.2.4: Plan record + 3 tests

### 2.3: Tools
- [ ] 2.3.1: ToolParameter record + 2 tests
- [ ] 2.3.2: ToolResult class + 4 tests
- [ ] 2.3.3: AgentTool interface + 2 tests (mocks)

### 2.4: LangChain4J Integration
- [ ] 2.4.1: OllamAssistAgenticScope + 3 tests

### Validation Finale
- [ ] Tous les tests passent (`./gradlew test`)
- [ ] Couverture >80% (`./gradlew jacocoTestReport`)
- [ ] Aucun warning de compilation
- [ ] Javadoc complète
- [ ] Code formatté

**Total: 14 classes/interfaces + 39 tests**

---

## Dépendances Gradle

Ajouter dans `build.gradle.kts`:

```kotlin
dependencies {
    // LangChain4J (version à vérifier dans build.gradle.kts existant)
    implementation("dev.langchain4j:langchain4j-core:${langchain4jVersion}")
    implementation("dev.langchain4j:langchain4j-ollama:${langchain4jVersion}")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")

    // Swagger (pour @Schema)
    implementation("io.swagger.core.v3:swagger-annotations:2.2.20")

    // Tests
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.25.1")
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.10.0")

    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")
}
```

---

## Prochaine Étape

Une fois Phase 2 complétée, nous passerons à **Phase 3: SupervisorAgent (Orchestrator)** qui utilisera:
- SupervisorAgent de LangChain4J
- Notre OllamAssistAgenticScope
- HumanInTheLoop pour validation
- Structured output pour Plan generation

**Prêt à commencer l'implémentation Phase 2 ? 🚀**
