# LangChain4J Agents - Intégration dans OllamAssist

**Date:** 2025-01-06
**Statut:** 🔄 En cours d'intégration dans Phase 2

Cette documentation analyse comment intégrer les patterns LangChain4J Agents dans notre architecture OllamAssist.

---

## 🔍 Découvertes Importantes

### 1. Agent vs AI Service

**AI Service (@Tool):**
- Méthodes exposées comme tools
- Invoquées par le LLM quand nécessaire
- Pattern que nous avions déjà identifié

**Agent (@Agent):**
- Interface complète avec annotation `@Agent`
- Nom et description obligatoires
- Coordination via `AgenticScope`
- Support workflows (sequential, loop, parallel, conditional)

### 2. AgenticScope - Shared State

> "AgenticScope is a shared data structure among agents that stores variables, tracks invocation sequences, and enables inter-agent communication."

**Implications pour notre architecture:**
- ✅ Remplace/complète notre `ExecutionContext`
- ✅ Variables nommées avec `outputKey`
- ✅ Communication inter-agents sans couplage
- ✅ Observability intégrée (listeners)

### 3. Supervisor Pattern = Notre Orchestrateur !

```java
SupervisorAgent supervisor = AgenticServices
    .supervisorBuilder()
    .chatModel(plannerModel)
    .subAgents(agent1, agent2, agent3)
    .responseStrategy(SupervisorResponseStrategy.SUMMARY)
    .build();
```

**C'est exactement notre `OrchestratorAgent` !**
- ✅ Décide autonomement des tâches
- ✅ Génère un plan d'exécution
- ✅ Délègue aux sous-agents
- ✅ Agrège les résultats

### 4. Human-in-the-Loop Natif

```java
HumanInTheLoop humanLoop = AgenticServices
    .humanInTheLoopBuilder()
    .description("Asks user for approval")
    .outputKey("approval")
    .requestWriter(System.out::println)
    .responseReader(() -> readUserInput())
    .build();
```

**Intégration parfaite avec notre système:**
- ✅ `PlanValidator` devient un HumanInTheLoop agent
- ✅ `ActionValidator` aussi
- ✅ Standardisé par LangChain4J

### 5. Observability Listeners

```java
AgenticServices.agentBuilder()
    .beforeAgentInvocation((scope, agent) -> log("Starting: " + agent))
    .afterAgentInvocation((scope, agent, result) -> log("Completed: " + agent))
    .build();
```

**Remplace notre `ObservabilityCollector` ?**
- ⚠️ Moins granulaire que notre système de traces
- ✅ Mais intégré nativement
- 💡 **Décision:** Utiliser les deux (listeners LangChain4J + notre StepTrace)

---

## 🎯 Impacts sur notre Architecture

### Changements Majeurs

#### 1. Agents Spécialisés = @Agent (pas juste @Tool)

**Avant (notre plan):**
```java
public class RagSearchAgent implements Agent {
    @Tool("Search code")
    public ToolResult searchCode(...) { }
}
```

**Après (avec LangChain4J):**
```java
@Agent(name = "RagSearchAgent", description = "Searches codebase using RAG")
public interface RagSearchAgent {
    @UserMessage("Search for code matching: {{query}}")
    String searchCode(@V("query") String query,
                     @V("maxResults") Integer maxResults);
}

// Built via AgenticServices
RagSearchAgent agent = AgenticServices
    .agentBuilder(RagSearchAgent.class)
    .chatModel(chatModel)
    .tools(searchCodeTool)  // Notre implémentation @Tool
    .outputKey("searchResults")
    .build();
```

**Implication:**
- ✅ Les agents deviennent des **interfaces** annotées `@Agent`
- ✅ Les **tools** sont des classes séparées avec `@Tool`
- ✅ Agent = orchestration, Tool = action concrète

#### 2. ExecutionContext → AgenticScope

**Avant:**
```java
public class ExecutionContext {
    private Map<Integer, ToolResult> stepResults;
    private ChatMemory chatMemory;
    private List<SourceReference> allSources;
}
```

**Après:**
```java
// LangChain4J fournit AgenticScope
// Nous wrappons/étendons pour ajouter nos features

public class OllamAssistAgenticScope extends AgenticScope {
    // Ajouts spécifiques
    private ExecutionTrace executionTrace;
    private List<SourceReference> allSources;

    // AgenticScope native gère déjà:
    // - Variables map (outputKey → results)
    // - Invocation sequence
    // - Agent coordination
}
```

#### 3. Orchestrator = SupervisorAgent

**Avant:**
```java
public class OrchestratorAgent {
    private PlanGenerator planGenerator;
    private AgentDelegator agentDelegator;

    public AgentResponse execute(UserRequest request) {
        Plan plan = planGenerator.generate(request);
        // ...
    }
}
```

**Après:**
```java
// L'orchestrator DEVIENT un SupervisorAgent LangChain4J

SupervisorAgent orchestrator = AgenticServices
    .supervisorBuilder()
    .chatModel(plannerModel)  // Température 0.3
    .subAgents(
        ragSearchAgent,
        gitAgent,
        refactoringAgent,
        codeAnalysisAgent,
        planValidatorAgent,  // HumanInTheLoop
        actionValidatorAgent // HumanInTheLoop
    )
    .responseStrategy(SupervisorResponseStrategy.SUMMARY)
    .contextStrategy(ContextStrategy.CHAT_MEMORY)
    .maxIterations(10)
    .build();
```

#### 4. PlanValidator/ActionValidator = HumanInTheLoop

**Avant:**
```java
public class PlanValidator {
    public ValidationResult validateWithUser(Plan plan) {
        // Custom UI logic
    }
}
```

**Après:**
```java
HumanInTheLoop planValidator = AgenticServices
    .humanInTheLoopBuilder()
    .name("PlanValidator")
    .description("Asks user to approve execution plan")
    .outputKey("planApproval")
    .requestWriter(plan -> uiPanel.displayPlan(plan))
    .responseReader(() -> uiPanel.waitForUserDecision())
    .build();
```

---

## 🔄 Architecture Révisée

### Nouvelle Hiérarchie

```
SupervisorAgent (Orchestrator)
    ├─ PlanValidator (HumanInTheLoop)
    ├─ RagSearchAgent (@Agent)
    │   └─ SearchCodeTool (@Tool)
    │   └─ SearchDocumentationTool (@Tool)
    ├─ GitAgent (@Agent)
    │   └─ GitStatusTool (@Tool)
    │   └─ GitCommitTool (@Tool)
    │       └─ ActionValidator (HumanInTheLoop)
    ├─ RefactoringAgent (@Agent)
    │   └─ AnalyzeCodeTool (@Tool)
    │   └─ ApplyRefactoringTool (@Tool)
    │       └─ ActionValidator (HumanInTheLoop)
    └─ CodeAnalysisAgent (@Agent)
        └─ AnalyzeComplexityTool (@Tool)
        └─ DetectCodeSmellsTool (@Tool)
```

### AgenticScope Variables Flow

```
User Request
    ↓
SupervisorAgent decides → invokes RagSearchAgent
    ↓
RagSearchAgent.searchCode() → outputKey="searchResults"
    ↓
AgenticScope.set("searchResults", List<SourceReference>)
    ↓
SupervisorAgent reads "searchResults" → decides next step
    ↓
SupervisorAgent invokes CodeAnalysisAgent
    ↓
CodeAnalysisAgent reads @V("searchResults") from scope
    ↓
CodeAnalysisAgent.analyzeComplexity() → outputKey="complexityMetrics"
    ↓
SupervisorAgent aggregates all results → final response
```

---

## 📋 Plan Phase 2 Révisé

### Changements dans le Modèle de Domaine

#### 1. Agent Interface → Pas Nécessaire !

❌ **Supprimer:** `Agent` interface custom

✅ **Utiliser:** Interfaces annotées `@Agent` de LangChain4J

**Impact:** Simplification du modèle !

#### 2. AgentTool Interface → Conserver !

✅ **Conserver:** Notre interface `AgentTool`

**Raison:** LangChain4J `@Tool` est une annotation, pas une interface. Notre interface fournit:
- Structure commune pour nos tools
- Metadata (requiresUserApproval, ownerAgent)
- `ToolResult` custom avec `SourceReference`

**Pattern d'implémentation:**
```java
public class SearchCodeTool implements AgentTool {
    @Override
    public String getId() { return "search-code"; }

    @Override
    public boolean requiresUserApproval() { return false; }

    @Tool("Search for code in the project")
    public ToolResult execute(
        @P("search query") String query,
        @P("max results") Integer maxResults,
        StepTrace trace  // Notre observability
    ) {
        // Implementation
        return ToolResult.success(output, sources);
    }
}
```

#### 3. ExecutionContext → Wrapper AgenticScope

✅ **Ajouter:** Classe wrapper pour AgenticScope

```java
public class OllamAssistAgenticScope {
    private final AgenticScope nativeScope;  // LangChain4J
    private final ExecutionTrace executionTrace;  // Notre observability
    private final ObservabilityCollector collector;

    public void setStepResult(String key, ToolResult result) {
        // Set dans AgenticScope
        nativeScope.set(key, result.getOutput());

        // Track dans notre observability
        collector.recordStepSuccess(currentStepTrace, result);
        executionTrace.getAllSources().addAll(result.getSources());
    }
}
```

#### 4. Plan/PlanStep → Utilisés par Supervisor

✅ **Conserver:** `Plan` et `PlanStep`

**Raison:** Le SupervisorAgent génère un plan en interne, mais nous voulons:
- Afficher le plan à l'utilisateur avant exécution
- Structured output pour le plan
- Metadata et justifications

**Solution:** PlanGenerator utilise structured output pour créer notre `Plan`, puis le Supervisor l'exécute.

---

## 🔧 Modèle de Domaine Mis à Jour

### Classes à Conserver (Phase 2)

#### 2.1: Observabilité (Inchangé)
1. ✅ `SourceType` enum
2. ✅ `SourceReference` class
3. ✅ `StepState` enum
4. ✅ `StepMetrics` class
5. ✅ `ExecutionMetrics` class
6. ✅ `StepTrace` class
7. ✅ `ExecutionTrace` class

**Raison:** Notre système d'observabilité est **plus riche** que celui de LangChain4J. On conserve tout.

#### 2.2: Plan (Inchangé)
8. ✅ `AgentType` enum
9. ✅ `ExecutionState` enum
10. ✅ `PlanStep` record
11. ✅ `Plan` record

**Raison:** Nécessaire pour l'affichage du plan avant exécution (human-in-the-loop).

#### 2.3: Tools (Modifié)
12. ✅ `ToolParameter` record
13. ✅ `ToolResult` class
14. ✅ `AgentTool` interface (notre abstraction)
15. ❌ ~~`Agent` interface~~ → Remplacé par `@Agent` de LangChain4J

### Nouvelles Classes à Ajouter (Phase 2 étendue)

#### 2.4: Integration LangChain4J
16. ✅ `OllamAssistAgenticScope` - Wrapper autour de AgenticScope
17. ✅ `AgentToolAdapter` - Adapte nos AgentTool vers @Tool LangChain4J
18. ✅ `HumanInTheLoopAdapter` - Adapte PlanValidator/ActionValidator vers HumanInTheLoop

---

## 🎨 Pattern d'Implémentation Recommandé

### Pattern 1: Agent Spécialisé

```java
// Interface annotée @Agent
@Agent(
    name = "RagSearchAgent",
    description = "Searches codebase using RAG and semantic search"
)
public interface RagSearchAgent {
    @UserMessage("""
        Search the codebase for code matching this query: {{query}}
        Return up to {{maxResults}} results.
        """)
    @Output("searchResults")
    String searchCode(
        @V("query") String query,
        @V("maxResults") Integer maxResults
    );
}

// Builder
RagSearchAgent ragAgent = AgenticServices
    .agentBuilder(RagSearchAgent.class)
    .chatModel(chatModel)
    .tools(new SearchCodeTool(embeddingStore))  // Notre @Tool
    .outputKey("searchResults")
    .build();
```

### Pattern 2: Tool Implémentation

```java
public class SearchCodeTool implements AgentTool {
    private final EmbeddingStore embeddingStore;
    private final EmbeddingModel embeddingModel;

    @Override
    public String getId() { return "search-code"; }

    @Override
    public String getName() { return "searchCode"; }

    @Override
    public String getDescription() {
        return "Search for code in the project using semantic search";
    }

    @Override
    public boolean requiresUserApproval() { return false; }

    @Override
    public AgentType getOwnerAgent() { return AgentType.RAG_SEARCH; }

    @Tool("Search for code in the project using semantic search. Returns relevant code snippets.")
    public ToolResult execute(
        @P("search query describing what to find") String query,
        @P(value = "maximum number of results", required = false) Integer maxResults,
        StepTrace trace  // Notre observability
    ) {
        trace.addLog("Searching for: " + query);

        // 1. Create embedding
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 2. Search
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(
            queryEmbedding,
            maxResults != null ? maxResults : 5,
            0.7
        );

        // 3. Convert to SourceReferences
        List<SourceReference> sources = matches.stream()
            .map(this::toSourceReference)
            .collect(Collectors.toList());

        trace.recordSources(sources);

        return ToolResult.success(formatResults(sources), sources);
    }
}
```

### Pattern 3: Supervisor (Orchestrator)

```java
public class OrchestratorService {
    private final SupervisorAgent supervisor;
    private final OllamAssistAgenticScope scope;

    public OrchestratorService(Project project, OllamAssistSettings settings) {
        // Build agents
        RagSearchAgent ragAgent = buildRagSearchAgent();
        GitAgent gitAgent = buildGitAgent();
        RefactoringAgent refactoringAgent = buildRefactoringAgent();
        CodeAnalysisAgent analysisAgent = buildCodeAnalysisAgent();

        // Build HumanInTheLoop agents
        HumanInTheLoop planValidator = buildPlanValidator();
        HumanInTheLoop actionValidator = buildActionValidator();

        // Build supervisor
        this.supervisor = AgenticServices
            .supervisorBuilder()
            .chatModel(createPlannerModel())  // Temperature 0.3
            .subAgents(
                planValidator,      // First: validate plan
                ragAgent,
                gitAgent,
                refactoringAgent,
                analysisAgent,
                actionValidator     // For destructive actions
            )
            .responseStrategy(SupervisorResponseStrategy.SUMMARY)
            .contextStrategy(ContextStrategy.CHAT_MEMORY)
            .maxIterations(settings.getMaxPlanSteps())
            .beforeAgentInvocation(this::onAgentStart)
            .afterAgentInvocation(this::onAgentComplete)
            .errorHandler(this::onAgentError)
            .build();

        // Initialize scope
        this.scope = new OllamAssistAgenticScope(project);
    }

    public AgentResponse execute(UserRequest request) {
        scope.startExecution(request);

        String result = supervisor.execute(request.getMessage());

        return AgentResponse.builder()
            .result(result)
            .trace(scope.getExecutionTrace())
            .sources(scope.getAllSources())
            .build();
    }

    private void onAgentStart(AgenticScope scope, Object agent) {
        this.scope.onAgentStart(agent);
    }

    private void onAgentComplete(AgenticScope scope, Object agent, Object result) {
        this.scope.onAgentComplete(agent, result);
    }

    private Object onAgentError(AgenticScope scope, Object agent, Throwable error) {
        this.scope.onAgentError(agent, error);
        return "Error occurred, please try again";
    }
}
```

### Pattern 4: HumanInTheLoop (PlanValidator)

```java
public class PlanValidatorBuilder {
    public HumanInTheLoop build(PlanDisplayPanel uiPanel) {
        return AgenticServices
            .humanInTheLoopBuilder()
            .name("PlanValidator")
            .description("Asks user to approve the execution plan before proceeding")
            .outputKey("planApproval")
            .requestWriter(request -> {
                // Display plan in UI
                Plan plan = parsePlanFromRequest(request);
                ApplicationManager.getApplication().invokeLater(() ->
                    uiPanel.displayPlan(plan)
                );
            })
            .responseReader(() -> {
                // Wait for user decision (blocking)
                CompletableFuture<String> future = new CompletableFuture<>();

                ApplicationManager.getApplication().invokeLater(() -> {
                    String decision = uiPanel.waitForUserDecision(); // Accept/Reject/Modify
                    future.complete(decision);
                });

                try {
                    return future.get(); // Block until user responds
                } catch (Exception e) {
                    return "REJECTED";
                }
            })
            .build();
    }
}
```

---

## 📊 Comparaison Avant/Après

| Aspect | Architecture Originale | Architecture avec LangChain4J Agents |
|--------|------------------------|--------------------------------------|
| **Agents** | Interface custom | @Agent annotation LangChain4J |
| **Tools** | @Tool methods | @Tool methods (identique) |
| **Orchestration** | Custom OrchestratorAgent | SupervisorAgent natif |
| **State** | ExecutionContext custom | AgenticScope + wrapper |
| **Human-in-the-Loop** | Custom validators | HumanInTheLoop natif |
| **Observability** | Custom traces | Listeners + nos traces |
| **Workflows** | Manual delegation | Sequential/Loop/Parallel/Conditional natifs |
| **Plan Generation** | Structured output custom | Structured output (identique) |
| **Complexity** | Plus de code custom | Moins de code, plus standard |

---

## ✅ Avantages de l'Intégration

1. ✅ **Moins de code custom** - Utilise patterns LangChain4J éprouvés
2. ✅ **Workflows natifs** - Sequential, loop, parallel, conditional out-of-the-box
3. ✅ **HumanInTheLoop standardisé** - Pattern reconnu
4. ✅ **AgenticScope** - Communication inter-agents simplifiée
5. ✅ **Observability** - Listeners intégrés + nos traces détaillées
6. ✅ **Supervisor** - Décisions autonomes par le LLM
7. ✅ **Async support** - Parallélisation facile

## ⚠️ Points d'Attention

1. ⚠️ **Learning curve** - Nouveaux concepts (AgenticScope, workflows)
2. ⚠️ **Abstraction** - Moins de contrôle sur l'orchestration
3. ⚠️ **Observability** - Listeners moins granulaires (on garde nos traces)
4. ⚠️ **Structured outputs** - À vérifier la compatibilité avec SupervisorAgent
5. ⚠️ **Version Ollama** - S'assurer du support des features agents

---

## 🚀 Plan Phase 2 Final Révisé

### Sous-phase 2.1: Observabilité (Inchangé) ✅
- SourceType, SourceReference, StepState
- StepMetrics, ExecutionMetrics
- StepTrace, ExecutionTrace

### Sous-phase 2.2: Plan (Inchangé) ✅
- AgentType, ExecutionState
- PlanStep, Plan

### Sous-phase 2.3: Tools ✅
- ToolParameter
- ToolResult
- AgentTool interface

### Sous-phase 2.4: LangChain4J Integration (Nouveau) ✨
- OllamAssistAgenticScope (wrapper)
- AgentToolAdapter (bridge pattern)
- Tests d'intégration avec AgenticScope

**Durée totale révisée:** 2-3 jours (identique, mais 2.4 remplace Agent interface)

---

## 🎯 Prochaines Étapes

1. **Phase 2** : Implémenter le modèle de domaine avec intégration LangChain4J
2. **Phase 3** : Implémenter le SupervisorAgent (Orchestrator)
3. **Phase 4** : Implémenter le premier agent (@Agent RagSearchAgent + @Tool)
4. **Phase 5** : UI avec HumanInTheLoop integration

**Prêt à démarrer Phase 2 avec cette nouvelle architecture ? 🚀**
