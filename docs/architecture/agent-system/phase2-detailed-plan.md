# Phase 2: Modèle de Domaine - Plan Détaillé

**Objectif:** Créer les classes de base réutilisables avec tests (approche TDD)

**Durée estimée:** 2-3 jours

**Critères d'acceptance:**
- ✅ Couverture tests >80%
- ✅ Tous les tests passent
- ✅ Code compilable et utilisable par Phase 3
- ✅ Documentation Javadoc complète

---

## Stratégie d'Implémentation

### Principe TDD (Test-Driven Development)

Pour **chaque** classe/interface :

1. **RED** 🔴 : Écrire le test (qui échoue)
2. **GREEN** 🟢 : Écrire le code minimal pour passer le test
3. **REFACTOR** 🔵 : Améliorer le code tout en gardant les tests verts

### Ordre d'Implémentation

On implémente dans cet ordre pour respecter les dépendances :

```
1. Enums (pas de dépendances)
   └── AgentType, ExecutionState, StepState, SourceType

2. Value Objects simples (pas de dépendances complexes)
   └── SourceReference

3. Metrics (dépend de Duration, Instant uniquement)
   └── ExecutionMetrics, StepMetrics

4. Interfaces de base
   └── Agent, AgentTool, ToolParameter

5. Objets métier avec dépendances
   └── ToolResult → PlanStep → Plan
   └── StepTrace → ExecutionTrace

6. Validation finale
   └── Tests d'intégration du modèle complet
```

---

## Détail par Sous-Phase

---

## 2.1: Modèle d'Observabilité

**Durée:** 1 jour

**Objectif:** Classes pour tracer l'exécution et les sources

### 2.1.1: SourceType Enum

**Test à écrire:**
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

**Fichier:** `src/main/java/fr/baretto/ollamassist/agent/observability/SourceType.java`

**Test:** `src/test/java/fr/baretto/ollamassist/agent/observability/SourceTypeTest.java`

---

### 2.1.2: SourceReference Class

**Tests à écrire (dans l'ordre):**

**Test 1: Builder pattern fonctionne**
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
    assertThat(ref.getLineEnd()).isEqualTo(20);
    assertThat(ref.getSnippet()).isEqualTo("public void test() {}");
    assertThat(ref.getDescription()).isEqualTo("Test method");
    assertThat(ref.getRelevanceScore()).isEqualTo(0.95);
    assertThat(ref.getSourceAgent()).isEqualTo("RAG_SEARCH");
    assertThat(ref.getTimestamp()).isNotNull();
}
```

**Test 2: getDisplayName retourne le nom du fichier**
```java
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
```

**Test 3: getNavigationUrl pour IntelliJ**
```java
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
void getNavigationUrl_shouldReturnUriWithoutLineForOthers() {
    SourceReference ref = SourceReference.builder()
        .uri("https://example.com")
        .type(SourceType.URL)
        .build();

    assertThat(ref.getNavigationUrl()).isEqualTo("https://example.com");
}
```

**Test 4: isNavigable**
```java
@Test
void isNavigable_shouldReturnTrueForFileClassCommit() {
    assertThat(SourceReference.builder().type(SourceType.FILE).build().isNavigable())
        .isTrue();
    assertThat(SourceReference.builder().type(SourceType.CLASS).build().isNavigable())
        .isTrue();
    assertThat(SourceReference.builder().type(SourceType.COMMIT).build().isNavigable())
        .isTrue();
}

@Test
void isNavigable_shouldReturnFalseForOthers() {
    assertThat(SourceReference.builder().type(SourceType.URL).build().isNavigable())
        .isFalse();
    assertThat(SourceReference.builder().type(SourceType.SNIPPET).build().isNavigable())
        .isFalse();
}
```

**Test 5: Metadata map**
```java
@Test
void metadata_shouldSupportCustomKeyValuePairs() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("repository", "main");
    metadata.put("branch", "develop");

    SourceReference ref = SourceReference.builder()
        .uri("/file.java")
        .type(SourceType.FILE)
        .metadata(metadata)
        .build();

    assertThat(ref.getMetadata()).containsEntry("repository", "main");
    assertThat(ref.getMetadata()).containsEntry("branch", "develop");
}
```

**Implémentation:**
- Utiliser Lombok `@Data` et `@Builder`
- Implémenter les méthodes `getDisplayName()`, `getNavigationUrl()`, `isNavigable()`
- Ajouter validation (uri non null, type non null)

**Fichier:** `src/main/java/fr/baretto/ollamassist/agent/observability/SourceReference.java`

**Test:** `src/test/java/fr/baretto/ollamassist/agent/observability/SourceReferenceTest.java`

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

public enum StepState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
```

**Fichier:** `src/main/java/fr/baretto/ollamassist/agent/observability/StepState.java`

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
- Champs: llmCalls, inputTokens, outputTokens, llmTime, toolExecutionTime
- Méthode: `recordLLMCall(int input, int output, Duration duration)`

**Fichier:** `src/main/java/fr/baretto/ollamassist/agent/observability/StepMetrics.java`

---

### 2.1.5: ExecutionMetrics Class

**Tests:**
```java
@Test
void recordLLMCall_shouldCalculateEstimatedCost() {
    ExecutionMetrics metrics = new ExecutionMetrics();

    // 1000 tokens total = $0.01 (example rate)
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

    assertThat(metrics.getAverageTokensPerCall()).isEqualTo(350.0); // (300 + 400) / 2
}

@Test
void getAverageTokensPerCall_shouldReturnZeroWhenNoCalls() {
    ExecutionMetrics metrics = new ExecutionMetrics();

    assertThat(metrics.getAverageTokensPerCall()).isEqualTo(0.0);
}
```

**Implémentation:**
- Étendre StepMetrics avec champs supplémentaires
- Ajouter: validationTime, peakMemoryUsage, embeddingSearches, retries
- Méthode: `incrementFailures()`, `getAverageTokensPerCall()`

**Fichier:** `src/main/java/fr/baretto/ollamassist/agent/observability/ExecutionMetrics.java`

---

### 2.1.6: StepTrace Class

**Tests:**

**Test 1: recordStart**
```java
@Test
void recordStart_shouldSetStateAndStartTime() {
    StepTrace trace = StepTrace.builder()
        .stepId("step-1")
        .build();

    trace.recordStart();

    assertThat(trace.getState()).isEqualTo(StepState.RUNNING);
    assertThat(trace.getStartTime()).isNotNull();
}
```

**Test 2: recordSuccess**
```java
@Test
void recordSuccess_shouldSetStateAndCalculateDuration() {
    StepTrace trace = StepTrace.builder()
        .stepId("step-1")
        .build();

    trace.recordStart();
    Thread.sleep(100); // Wait 100ms

    ToolResult result = ToolResult.success("output", List.of());
    trace.recordSuccess(result);

    assertThat(trace.getState()).isEqualTo(StepState.COMPLETED);
    assertThat(trace.getEndTime()).isNotNull();
    assertThat(trace.getDuration()).isNotNull();
    assertThat(trace.getDuration().toMillis()).isGreaterThanOrEqualTo(100);
    assertThat(trace.getOutput()).isEqualTo(result);
}
```

**Test 3: recordError**
```java
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
```

**Test 4: addLog**
```java
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
```

**Test 5: recordReasoning**
```java
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
- Lombok @Data @Builder
- Méthodes: recordStart(), recordSuccess(ToolResult), recordError(Exception), addLog(String), recordReasoning(String)
- Initialiser logs en ArrayList vide par défaut

**Fichier:** `src/main/java/fr/baretto/ollamassist/agent/observability/StepTrace.java`

---

### 2.1.7: ExecutionTrace Class

**Tests:**

**Test 1: addStepTrace accumule sources**
```java
@Test
void addStepTrace_shouldAddToTracesAndAccumulateSources() {
    ExecutionTrace execTrace = ExecutionTrace.builder()
        .executionId("exec-1")
        .stepTraces(new ArrayList<>())
        .allSources(new ArrayList<>())
        .build();

    SourceReference source1 = SourceReference.builder()
        .uri("file1.java")
        .type(SourceType.FILE)
        .build();

    StepTrace step = StepTrace.builder()
        .stepId("step-1")
        .sources(List.of(source1))
        .build();

    execTrace.addStepTrace(step);

    assertThat(execTrace.getStepTraces()).hasSize(1);
    assertThat(execTrace.getAllSources()).hasSize(1);
    assertThat(execTrace.getAllSources()).contains(source1);
}
```

**Test 2: getCompletedSteps**
```java
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
```

**Test 3: getTotalSteps**
```java
@Test
void getTotalSteps_shouldReturnTotalCount() {
    ExecutionTrace execTrace = ExecutionTrace.builder()
        .stepTraces(new ArrayList<>())
        .build();

    execTrace.getStepTraces().add(StepTrace.builder().stepId("1").build());
    execTrace.getStepTraces().add(StepTrace.builder().stepId("2").build());

    assertThat(execTrace.getTotalSteps()).isEqualTo(2);
}
```

**Test 4: getFailedSteps**
```java
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
- Lombok @Data @Builder
- Méthodes: addStepTrace(StepTrace), getCompletedSteps(), getTotalSteps(), getFailedSteps()

**Fichier:** `src/main/java/fr/baretto/ollamassist/agent/observability/ExecutionTrace.java`

---

## 2.2: Modèle de Plan

**Durée:** 0.5 jour

**Objectif:** Classes pour représenter les plans d'exécution

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

public enum AgentType {
    ORCHESTRATOR,
    RAG_SEARCH,
    GIT,
    REFACTORING,
    CODE_ANALYSIS
}
```

**Fichier:** `src/main/java/fr/baretto/ollamassist/agent/model/AgentType.java`

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

public enum ExecutionState {
    PENDING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
```

**Fichier:** `src/main/java/fr/baretto/ollamassist/agent/model/ExecutionState.java`

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
    assertThat(step.justification()).isEqualTo("RAG can find patterns across indexed files");
    assertThat(step.expectedOutput()).isEqualTo("List of files with TODOs");
    assertThat(step.dependencies()).isEmpty();
}

@Test
void planStep_shouldSupportDependencies() {
    PlanStep step = new PlanStep(
        2,
        AgentType.CODE_ANALYSIS,
        "Analyze complexity",
        "Prioritize refactoring",
        "Metrics report",
        List.of(1) // Depends on step 1
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
 * Represents a single step in an execution plan.
 */
public record PlanStep(
    @Schema(description = "Step number starting from 1")
    @JsonProperty(required = true)
    int stepNumber,

    @Schema(description = "Agent type to use for this step")
    @JsonProperty(required = true)
    AgentType agentType,

    @Schema(description = "Action to perform")
    @JsonProperty(required = true)
    String action,

    @Schema(description = "Why this step is needed")
    @JsonProperty(required = true)
    String justification,

    @Schema(description = "Expected output from this step")
    String expectedOutput,

    @Schema(description = "Step numbers this step depends on")
    List<Integer> dependencies
) {}
```

**Fichier:** `src/main/java/fr/baretto/ollamassist/agent/model/PlanStep.java`

---

### 2.2.4: Plan Record

**Tests:**
```java
@Test
void plan_shouldCreateWithAllFields() {
    PlanStep step1 = new PlanStep(1, AgentType.RAG_SEARCH, "action1", "just1", "out1", List.of());
    PlanStep step2 = new PlanStep(2, AgentType.GIT, "action2", "just2", "out2", List.of(1));

    Plan plan = new Plan(
        "Find and analyze TODOs",
        List.of(step1, step2),
        60
    );

    assertThat(plan.goal()).isEqualTo("Find and analyze TODOs");
    assertThat(plan.steps()).hasSize(2);
    assertThat(plan.estimatedDuration()).isEqualTo(60);
}

@Test
void plan_shouldAllowNullEstimatedDuration() {
    Plan plan = new Plan("Goal", List.of(), null);

    assertThat(plan.estimatedDuration()).isNull();
}

@Test
void plan_shouldValidateStepsOrder() {
    PlanStep step1 = new PlanStep(1, AgentType.RAG_SEARCH, "a", "j", "o", List.of());
    PlanStep step2 = new PlanStep(2, AgentType.GIT, "a", "j", "o", List.of());

    Plan plan = new Plan("Goal", List.of(step1, step2), 30);

    // Verify steps are in order
    assertThat(plan.steps().get(0).stepNumber()).isEqualTo(1);
    assertThat(plan.steps().get(1).stepNumber()).isEqualTo(2);
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Represents an execution plan generated by the orchestrator.
 */
public record Plan(
    @Schema(description = "Overall goal of the plan")
    @JsonProperty(required = true)
    String goal,

    @Schema(description = "List of steps to execute in order")
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

**Fichier:** `src/main/java/fr/baretto/ollamassist/agent/model/Plan.java`

---

## 2.3: Interfaces Agent de Base

**Durée:** 0.5 jour

**Objectif:** Interfaces pour agents et tools

### 2.3.1: ToolParameter Record

**Tests:**
```java
@Test
void toolParameter_shouldCreateWithAllFields() {
    ToolParameter param = new ToolParameter(
        "query",
        "string",
        "Search query",
        true
    );

    assertThat(param.name()).isEqualTo("query");
    assertThat(param.type()).isEqualTo("string");
    assertThat(param.description()).isEqualTo("Search query");
    assertThat(param.required()).isTrue();
}

@Test
void toolParameter_shouldDefaultToRequired() {
    ToolParameter param = new ToolParameter(
        "query",
        "string",
        "Search query",
        null
    );

    // Par défaut required = true si null
    assertThat(param.required()).isTrue();
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent.tool;

/**
 * Represents a parameter for an agent tool.
 */
public record ToolParameter(
    String name,
    String type,
    String description,
    Boolean required
) {
    public ToolParameter {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Parameter name cannot be null or empty");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Parameter type cannot be null or empty");
        }
        if (required == null) {
            required = true; // Default to required
        }
    }

    public boolean isRequired() {
        return required != null && required;
    }
}
```

**Fichier:** `src/main/java/fr/baretto/ollamassist/agent/tool/ToolParameter.java`

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

    ToolResult result = ToolResult.success("Output text", List.of(source));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getOutput()).isEqualTo("Output text");
    assertThat(result.getSources()).containsExactly(source);
    assertThat(result.getErrorMessage()).isNull();
}

@Test
void error_shouldCreateFailedResult() {
    ToolResult result = ToolResult.error("Something went wrong");

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getErrorMessage()).isEqualTo("Something went wrong");
    assertThat(result.getOutput()).isNull();
}

@Test
void withStructuredData_shouldAddStructuredData() {
    ToolResult result = ToolResult.success("Output", List.of())
        .withStructuredData(Map.of("key", "value"));

    assertThat(result.getStructuredData()).isNotNull();
    assertThat(result.getStructuredData()).isInstanceOf(Map.class);
}

@Test
void withMetadata_shouldAddMetadata() {
    ToolResult result = ToolResult.success("Output", List.of())
        .withMetadata(Map.of("executionTime", "2s"));

    assertThat(result.getMetadata()).containsEntry("executionTime", "2s");
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

**Fichier:** `src/main/java/fr/baretto/ollamassist/agent/tool/ToolResult.java`

---

### 2.3.3: AgentTool Interface

**Tests (via Mock):**
```java
@Test
void agentTool_shouldProvideBasicInformation() {
    AgentTool tool = mock(AgentTool.class);
    when(tool.getId()).thenReturn("search-code");
    when(tool.getName()).thenReturn("searchCode");
    when(tool.getDescription()).thenReturn("Search for code");
    when(tool.requiresUserApproval()).thenReturn(false);
    when(tool.getOwnerAgent()).thenReturn(AgentType.RAG_SEARCH);

    assertThat(tool.getId()).isEqualTo("search-code");
    assertThat(tool.getName()).isEqualTo("searchCode");
    assertThat(tool.getDescription()).isEqualTo("Search for code");
    assertThat(tool.requiresUserApproval()).isFalse();
    assertThat(tool.getOwnerAgent()).isEqualTo(AgentType.RAG_SEARCH);
}

@Test
void agentTool_shouldHaveParameters() {
    AgentTool tool = mock(AgentTool.class);
    ToolParameter param = new ToolParameter("query", "string", "Search query", true);
    when(tool.getParameters()).thenReturn(List.of(param));

    assertThat(tool.getParameters()).hasSize(1);
    assertThat(tool.getParameters().get(0).name()).isEqualTo("query");
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
 * Tools are executable actions provided by specialized agents.
 */
public interface AgentTool {
    /**
     * Unique identifier for this tool.
     */
    String getId();

    /**
     * Human-readable name.
     */
    String getName();

    /**
     * Description of what this tool does (used by LLM).
     */
    String getDescription();

    /**
     * Parameter definitions for this tool.
     */
    List<ToolParameter> getParameters();

    /**
     * Executes the tool with given parameters.
     *
     * @param parameters Input parameters
     * @param trace Trace collector for observability
     * @return Tool result with output and sources
     */
    ToolResult execute(Map<String, Object> parameters, StepTrace trace);

    /**
     * Whether this tool requires user approval before execution.
     */
    boolean requiresUserApproval();

    /**
     * Agent type that owns this tool.
     */
    AgentType getOwnerAgent();
}
```

**Fichier:** `src/main/java/fr/baretto/ollamassist/agent/tool/AgentTool.java`

---

### 2.3.4: Agent Interface

**Tests (via Mock):**
```java
@Test
void agent_shouldProvideBasicInformation() {
    Agent agent = mock(Agent.class);
    when(agent.getType()).thenReturn(AgentType.RAG_SEARCH);
    when(agent.getDescription()).thenReturn("Searches codebase using RAG");
    when(agent.isEnabled()).thenReturn(true);

    assertThat(agent.getType()).isEqualTo(AgentType.RAG_SEARCH);
    assertThat(agent.getDescription()).isEqualTo("Searches codebase using RAG");
    assertThat(agent.isEnabled()).isTrue();
}

@Test
void agent_shouldProvideTools() {
    Agent agent = mock(Agent.class);
    AgentTool tool1 = mock(AgentTool.class);
    AgentTool tool2 = mock(AgentTool.class);
    when(agent.getTools()).thenReturn(List.of(tool1, tool2));

    assertThat(agent.getTools()).hasSize(2);
}

@Test
void agent_shouldSelectToolByAction() {
    Agent agent = mock(Agent.class);
    AgentTool tool = mock(AgentTool.class);
    Map<String, Object> params = Map.of("query", "TODO");

    when(agent.selectTool("search for code", params)).thenReturn(tool);

    AgentTool selected = agent.selectTool("search for code", params);

    assertThat(selected).isEqualTo(tool);
}
```

**Implémentation:**
```java
package fr.baretto.ollamassist.agent;

import fr.baretto.ollamassist.agent.model.AgentType;
import fr.baretto.ollamassist.agent.tool.AgentTool;

import java.util.List;
import java.util.Map;

/**
 * Interface for specialized agents.
 * Each agent provides specific capabilities via tools.
 */
public interface Agent {
    /**
     * Returns the type of this agent.
     */
    AgentType getType();

    /**
     * Returns a human-readable description of this agent's capabilities.
     */
    String getDescription();

    /**
     * Returns all tools provided by this agent.
     */
    List<AgentTool> getTools();

    /**
     * Selects the appropriate tool for a given action description.
     *
     * @param action Natural language description of the action
     * @param parameters Available parameters for tool selection
     * @return The selected tool
     * @throws ToolNotFoundException if no matching tool is found
     */
    AgentTool selectTool(String action, Map<String, Object> parameters);

    /**
     * Indicates whether this agent is enabled in settings.
     */
    boolean isEnabled();
}
```

**Fichier:** `src/main/java/fr/baretto/ollamassist/agent/Agent.java`

---

## Récapitulatif de l'Ordre d'Implémentation

### Jour 1: Observabilité (2.1)
1. ✅ `SourceType` enum (simple)
2. ✅ `SourceReference` class (builder, méthodes utilitaires)
3. ✅ `StepState` enum
4. ✅ `StepMetrics` class
5. ✅ `ExecutionMetrics` class
6. ✅ `StepTrace` class (dépend de StepState, StepMetrics, SourceReference)
7. ✅ `ExecutionTrace` class (dépend de StepTrace, ExecutionMetrics)

### Jour 2: Plan + Interfaces (2.2 + 2.3)
8. ✅ `AgentType` enum
9. ✅ `ExecutionState` enum
10. ✅ `PlanStep` record
11. ✅ `Plan` record (dépend de PlanStep)
12. ✅ `ToolParameter` record
13. ✅ `ToolResult` class (dépend de SourceReference)
14. ✅ `AgentTool` interface (dépend de ToolParameter, ToolResult, StepTrace)
15. ✅ `Agent` interface (dépend de AgentTool, AgentType)

---

## Structure des Fichiers

```
src/main/java/fr/baretto/ollamassist/
├── agent/
│   ├── Agent.java                           # Interface (2.3.4)
│   ├── model/
│   │   ├── AgentType.java                   # Enum (2.2.1)
│   │   ├── ExecutionState.java              # Enum (2.2.2)
│   │   ├── Plan.java                        # Record (2.2.4)
│   │   └── PlanStep.java                    # Record (2.2.3)
│   ├── observability/
│   │   ├── ExecutionMetrics.java            # Class (2.1.5)
│   │   ├── ExecutionTrace.java              # Class (2.1.7)
│   │   ├── SourceReference.java             # Class (2.1.2)
│   │   ├── SourceType.java                  # Enum (2.1.1)
│   │   ├── StepMetrics.java                 # Class (2.1.4)
│   │   ├── StepState.java                   # Enum (2.1.3)
│   │   └── StepTrace.java                   # Class (2.1.6)
│   └── tool/
│       ├── AgentTool.java                   # Interface (2.3.3)
│       ├── ToolParameter.java               # Record (2.3.1)
│       └── ToolResult.java                  # Class (2.3.2)

src/test/java/fr/baretto/ollamassist/
├── agent/
│   ├── AgentTest.java                       # Tests interface Agent
│   ├── model/
│   │   ├── AgentTypeTest.java
│   │   ├── ExecutionStateTest.java
│   │   ├── PlanStepTest.java
│   │   └── PlanTest.java
│   ├── observability/
│   │   ├── ExecutionMetricsTest.java
│   │   ├── ExecutionTraceTest.java
│   │   ├── SourceReferenceTest.java
│   │   ├── SourceTypeTest.java
│   │   ├── StepMetricsTest.java
│   │   ├── StepStateTest.java
│   │   └── StepTraceTest.java
│   └── tool/
│       ├── AgentToolTest.java
│       ├── ToolParameterTest.java
│       └── ToolResultTest.java
```

---

## Dépendances Gradle à Ajouter

Vérifier que `build.gradle.kts` contient:

```kotlin
dependencies {
    // Lombok (déjà présent)
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // Jackson pour JSON (structured outputs)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")

    // Swagger annotations (pour @Schema dans records)
    implementation("io.swagger.core.v3:swagger-annotations:2.2.20")

    // Tests (déjà présent)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.25.1")
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.10.0")
}
```

---

## Checklist Complète Phase 2

### 2.1: Modèle d'Observabilité
- [ ] 2.1.1: SourceType enum + tests
- [ ] 2.1.2: SourceReference class + tests (5 tests)
- [ ] 2.1.3: StepState enum + tests
- [ ] 2.1.4: StepMetrics class + tests
- [ ] 2.1.5: ExecutionMetrics class + tests (4 tests)
- [ ] 2.1.6: StepTrace class + tests (5 tests)
- [ ] 2.1.7: ExecutionTrace class + tests (4 tests)

### 2.2: Modèle de Plan
- [ ] 2.2.1: AgentType enum + tests
- [ ] 2.2.2: ExecutionState enum + tests (3 tests)
- [ ] 2.2.3: PlanStep record + tests (2 tests)
- [ ] 2.2.4: Plan record + tests (3 tests)

### 2.3: Interfaces Agent de Base
- [ ] 2.3.1: ToolParameter record + tests (2 tests)
- [ ] 2.3.2: ToolResult class + tests (4 tests)
- [ ] 2.3.3: AgentTool interface + tests (2 tests avec mocks)
- [ ] 2.3.4: Agent interface + tests (3 tests avec mocks)

### Validation Finale
- [ ] Tous les tests passent (`./gradlew test`)
- [ ] Couverture >80% (`./gradlew jacocoTestReport`)
- [ ] Aucun warning de compilation
- [ ] Javadoc complète pour toutes les classes publiques
- [ ] Code formaté selon conventions du projet

---

## Commandes Utiles

```bash
# Exécuter tous les tests
./gradlew test

# Exécuter les tests d'un package spécifique
./gradlew test --tests fr.baretto.ollamassist.agent.observability.*

# Voir le rapport de couverture
./gradlew jacocoTestReport
open build/reports/jacoco/test/html/index.html

# Exécuter les tests en mode watch (IntelliJ)
# Right-click on test class → Run 'ClassName' with Coverage
```

---

## Prochaine Étape

Une fois la Phase 2 complétée avec succès, nous passerons à la **Phase 3: Orchestrateur MVP** qui utilisera toutes ces classes de base pour implémenter:
- PlanGenerator (avec structured outputs)
- PlanValidator (avec UI)
- AgentDelegator (avec tool invocation)
- ExecutionContext (shared state)

**Prêt à commencer l'implémentation ?** 🚀
