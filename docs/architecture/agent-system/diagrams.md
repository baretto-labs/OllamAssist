# Architecture Diagrams - Agent System

Cette documentation présente les diagrammes d'architecture du système d'agents OllamAssist.

---

## 1. Vue d'Ensemble du Système

```mermaid
graph TB
    subgraph "User Interface"
        UI[MessagesPanel]
        Toggle[AgentModeToggle]
        PlanPanel[PlanDisplayPanel]
        TracePanel[ExecutionTracePanel]
    end

    subgraph "OllamaService"
        Assistant[Assistant<br/>Chat Mode]
        Orchestrator[OrchestratorAgent<br/>Agent Mode]
        Memory[ChatMemory<br/>Shared]
    end

    subgraph "Specialized Agents"
        RAG[RagSearchAgent<br/>@Tool: searchCode<br/>@Tool: searchDocumentation<br/>@Tool: searchSimilarCode]
        Git[GitAgent<br/>@Tool: gitStatus<br/>@Tool: gitDiff<br/>@Tool: gitCommit<br/>@Tool: gitLog]
        Refactor[RefactoringAgent<br/>@Tool: analyzeCode<br/>@Tool: suggestRefactoring<br/>@Tool: applyRefactoring]
        Analysis[CodeAnalysisAgent<br/>@Tool: analyzeComplexity<br/>@Tool: analyzeDependencies<br/>@Tool: detectCodeSmells]
    end

    subgraph "Infrastructure"
        Ollama[Ollama LLM<br/>llama3.2]
        VectorStore[LuceneEmbeddingStore]
        GitRepo[Git Repository]
        PSI[IntelliJ PSI]
    end

    UI -->|chat mode| Assistant
    UI -->|agent mode| Orchestrator
    Toggle -->|switch| OllamaService

    Orchestrator -->|delegates| RAG
    Orchestrator -->|delegates| Git
    Orchestrator -->|delegates| Refactor
    Orchestrator -->|delegates| Analysis

    Assistant -->|LLM calls| Ollama
    Orchestrator -->|LLM calls| Ollama

    RAG -->|semantic search| VectorStore
    Git -->|operations| GitRepo
    Refactor -->|code analysis| PSI
    Analysis -->|metrics| PSI

    Orchestrator -->|plan display| PlanPanel
    Orchestrator -->|traces| TracePanel
```

---

## 2. Orchestrator Flow (Flux d'Exécution Agent)

```mermaid
sequenceDiagram
    participant User
    participant UI as MessagesPanel
    participant Orch as OrchestratorAgent
    participant PlanGen as PlanGenerator
    participant PlanVal as PlanValidator
    participant Deleg as AgentDelegator
    participant Agent as SpecializedAgent
    participant Obs as ObservabilityCollector
    participant LLM as Ollama LLM

    User->>UI: Enter request<br/>"Find all TODOs in code"
    UI->>Orch: executeAgentRequest(request)

    activate Orch
    Orch->>Obs: init(executionId)
    Orch->>PlanGen: generatePlan(request)

    activate PlanGen
    PlanGen->>LLM: Generate plan<br/>(Structured Output)
    Note over PlanGen,LLM: Temperature: 0.3<br/>JSON Schema: Plan
    LLM-->>PlanGen: Plan { steps: [...] }
    PlanGen-->>Orch: Plan (1-10 steps)
    deactivate PlanGen

    Orch->>PlanVal: validateWithUser(plan)
    activate PlanVal
    PlanVal->>UI: Display plan<br/>(Accept/Reject/Modify)
    UI->>User: Show plan
    User->>UI: Accept ✓
    UI-->>PlanVal: ValidationResult.ACCEPTED
    PlanVal-->>Orch: ValidationResult
    deactivate PlanVal

    loop For each step in Plan
        Orch->>Obs: startStep(stepNumber, agentType, action)
        Obs->>UI: onStepStarted(stepTrace)
        UI->>User: Display "Step 1 running..."

        Orch->>Deleg: executeStep(planStep, context)
        activate Deleg
        Deleg->>Agent: selectTool(action)
        Agent-->>Deleg: @Tool method

        alt Tool requires approval
            Deleg->>UI: Request approval
            UI->>User: Confirm action?
            User->>UI: Approve ✓
            UI-->>Deleg: Approved
        end

        Deleg->>Agent: execute(params, stepTrace)
        activate Agent

        alt RAG Agent
            Agent->>LLM: embed(query)
            LLM-->>Agent: Embedding
            Note over Agent: Search in VectorStore
            Agent-->>Agent: Convert to SourceReferences
        else Git Agent
            Note over Agent: Use Git4Idea APIs
        else Refactoring Agent
            Note over Agent: PSI analysis + LLM suggestions
        else Analysis Agent
            Note over Agent: Calculate metrics (PSI)
        end

        Agent-->>Deleg: ToolResult(output, sources)
        deactivate Agent

        Deleg-->>Orch: ToolResult
        deactivate Deleg

        Orch->>Obs: recordStepSuccess(stepTrace, result)
        Obs->>UI: onStepCompleted(stepTrace)
        UI->>User: Display "Step 1 ✓ completed"
    end

    Orch->>Obs: complete(COMPLETED)
    Obs->>UI: onExecutionCompleted(trace)
    Orch-->>UI: AgentResponse(result, trace, sources)
    deactivate Orch

    UI->>User: Display final result<br/>+ sources + traces
```

---

## 3. Plan Generation (Structured Output)

```mermaid
graph TB
    subgraph "Input"
        UserReq[User Request<br/>'Find all TODO comments']
    end

    subgraph "PlanGenerator"
        SystemPrompt[System Prompt<br/>Available agents:<br/>- RAG_SEARCH<br/>- GIT<br/>- REFACTORING<br/>- CODE_ANALYSIS<br/><br/>Max steps: 5<br/>Require justifications]

        LLMCall[Ollama LLM Call<br/>Temperature: 0.3<br/>Response Format:<br/>JSON Schema]

        Schema[Plan Schema<br/>goal: string<br/>steps: PlanStep[]<br/>estimatedDuration: int]
    end

    subgraph "Output"
        Plan[Plan Object<br/>goal: 'Find TODO comments'<br/>steps:<br/>1. RAG_SEARCH: searchCode<br/>2. CODE_ANALYSIS: analyzeComplexity<br/>estimatedDuration: 30s]
    end

    UserReq --> SystemPrompt
    SystemPrompt --> LLMCall
    Schema --> LLMCall
    LLMCall --> Plan

    style LLMCall fill:#e1f5ff
    style Schema fill:#fff4e1
    style Plan fill:#e8f5e9
```

**Plan JSON Example:**
```json
{
  "goal": "Find all TODO comments in the codebase",
  "steps": [
    {
      "stepNumber": 1,
      "agentType": "RAG_SEARCH",
      "action": "Search for TODO comments using semantic search",
      "justification": "RAG can find TODO patterns across all indexed files",
      "expectedOutput": "List of files containing TODO comments with line numbers"
    },
    {
      "stepNumber": 2,
      "agentType": "CODE_ANALYSIS",
      "action": "Analyze complexity of methods with TODOs",
      "justification": "Understanding complexity helps prioritize TODO resolution",
      "expectedOutput": "Complexity metrics for methods containing TODOs"
    }
  ],
  "estimatedDuration": 30
}
```

---

## 4. Tool Execution Flow (@Tool Method)

```mermaid
sequenceDiagram
    participant Deleg as AgentDelegator
    participant Registry as AgentRegistry
    participant Agent as RagSearchAgent
    participant Tool as @Tool searchCode()
    participant Embed as EmbeddingModel
    participant Store as LuceneEmbeddingStore
    participant Trace as StepTrace

    Deleg->>Registry: getAgent(RAG_SEARCH)
    Registry-->>Deleg: RagSearchAgent instance

    Deleg->>Agent: selectTool("search for code")
    Agent-->>Deleg: searchCode method

    Deleg->>Trace: recordToolInvocation(searchCode, params)
    Deleg->>Tool: searchCode(query="TODO", maxResults=10)

    activate Tool
    Tool->>Embed: embed(query)
    Embed-->>Tool: Embedding vector

    Tool->>Store: findRelevant(embedding, maxResults=10, minScore=0.7)
    Store-->>Tool: List<EmbeddingMatch>

    loop For each match
        Tool->>Tool: Convert to SourceReference<br/>- uri: file path<br/>- lineStart, lineEnd<br/>- snippet: code excerpt<br/>- relevanceScore<br/>- description
    end

    Tool->>Tool: formatSearchResults(sources)
    Tool-->>Deleg: ToolResult.success(output, sources)
    deactivate Tool

    Deleg->>Trace: recordToolSuccess(result, duration)
```

**ToolResult Structure:**
```java
ToolResult {
    output: "Found 15 TODO comments across 8 files:\n- src/Main.java:45...",
    sources: [
        SourceReference {
            uri: "src/main/java/Main.java",
            lineStart: 45,
            lineEnd: 47,
            snippet: "// TODO: Refactor this method",
            relevanceScore: 0.92,
            description: "TODO comment in Main.java"
        },
        // ... 14 more
    ],
    success: true
}
```

---

## 5. Observability System

```mermaid
graph TB
    subgraph "Collection"
        Collector[ObservabilityCollector]
        ExecTrace[ExecutionTrace<br/>- executionId<br/>- startTime, endTime<br/>- totalDuration<br/>- finalState<br/>- stepTraces[]<br/>- allSources[]<br/>- metrics]
        StepTrace[StepTrace<br/>- stepId<br/>- stepNumber<br/>- agentType<br/>- toolName<br/>- action<br/>- startTime, endTime<br/>- state<br/>- inputParameters<br/>- output<br/>- reasoning<br/>- sources[]<br/>- logs[]]
    end

    subgraph "Listeners"
        UIListener[ExecutionTraceUIListener]
        ChatListener[ChatModelListener<br/>- onRequest<br/>- onResponse<br/>- onError]
    end

    subgraph "UI Display"
        TracePanel[ExecutionTracePanel<br/>- Steps list<br/>- Sources list<br/>- Metrics display<br/>- Reasoning viewer]
    end

    subgraph "Metrics"
        Metrics[ExecutionMetrics<br/>- totalLLMCalls<br/>- totalTokensInput<br/>- totalTokensOutput<br/>- estimatedCost<br/>- llmTotalTime<br/>- toolExecutionTime<br/>- failures, retries]
    end

    Collector -->|manages| ExecTrace
    ExecTrace -->|contains| StepTrace
    ExecTrace -->|contains| Metrics

    Collector -->|notifies| UIListener
    Collector -->|notifies| ChatListener

    UIListener -->|updates| TracePanel
    ChatListener -->|records| Metrics

    style ExecTrace fill:#e8f5e9
    style StepTrace fill:#fff4e1
    style Metrics fill:#e1f5ff
```

---

## 6. ChatModelListener Integration

```mermaid
sequenceDiagram
    participant Tool as @Tool Method
    participant LLM as Ollama LLM
    participant Listener as ChatModelListener
    participant Trace as StepTrace
    participant Metrics as ExecutionMetrics

    Note over Tool,LLM: Agent needs LLM for suggestRefactoring

    Tool->>Listener: onRequest(context)
    activate Listener
    Listener->>Listener: attributes.put("request-start", now())
    Listener->>Trace: addLog("LLM Request: 3 messages")
    deactivate Listener

    Tool->>LLM: generate(prompt)
    Note over LLM: Processing with<br/>temperature: 0.7

    alt Success
        LLM-->>Tool: ChatResponse
        Tool->>Listener: onResponse(context)
        activate Listener
        Listener->>Listener: Calculate duration<br/>Extract token usage
        Listener->>Metrics: recordLLMCall(inputTokens=150,<br/>outputTokens=320, duration=2.3s)
        Listener->>Trace: addLog("LLM Response: 320 tokens in 2300ms")
        deactivate Listener
    else Error
        LLM-->>Tool: Exception
        Tool->>Listener: onError(context)
        activate Listener
        Listener->>Trace: addLog("LLM Error: timeout")
        Listener->>Metrics: incrementFailures()
        deactivate Listener
    end
```

**Attributes Map Usage:**
```java
// In onRequest
attributes.put("request-start", Instant.now());
attributes.put("step-id", currentStepId);

// In onResponse
Instant start = (Instant) attributes.get("request-start");
Duration duration = Duration.between(start, Instant.now());
```

---

## 7. Agent Registry Pattern

```mermaid
graph TB
    subgraph "AgentRegistry"
        Registry[AgentRegistry]
        Map[ConcurrentHashMap<br/>AgentType → Agent]
    end

    subgraph "Agent Implementations"
        RAG[RagSearchAgent<br/>implements Agent]
        Git[GitAgent<br/>implements Agent]
        Refactor[RefactoringAgent<br/>implements Agent]
        Analysis[CodeAnalysisAgent<br/>implements Agent]
    end

    subgraph "Agent Interface"
        IAgent[Agent Interface<br/>- getType<br/>- getDescription<br/>- getTools<br/>- selectTool<br/>- isEnabled]
    end

    subgraph "Tools"
        RAGTools[@Tool searchCode<br/>@Tool searchDocumentation<br/>@Tool searchSimilarCode]
        GitTools[@Tool gitStatus<br/>@Tool gitDiff<br/>@Tool gitCommit<br/>@Tool gitLog]
        RefactorTools[@Tool analyzeCode<br/>@Tool suggestRefactoring<br/>@Tool applyRefactoring]
        AnalysisTools[@Tool analyzeComplexity<br/>@Tool analyzeDependencies<br/>@Tool detectCodeSmells]
    end

    Registry -->|manages| Map
    Map -->|RAG_SEARCH| RAG
    Map -->|GIT| Git
    Map -->|REFACTORING| Refactor
    Map -->|CODE_ANALYSIS| Analysis

    RAG -.implements.- IAgent
    Git -.implements.- IAgent
    Refactor -.implements.- IAgent
    Analysis -.implements.- IAgent

    RAG -->|exposes| RAGTools
    Git -->|exposes| GitTools
    Refactor -->|exposes| RefactorTools
    Analysis -->|exposes| AnalysisTools

    style Registry fill:#e1f5ff
    style IAgent fill:#fff4e1
```

**Registry Usage:**
```java
// Initialization (OllamaService startup)
AgentRegistry registry = new AgentRegistry(project);
registry.register(new RagSearchAgent(project));
registry.register(new GitAgent(project));
registry.register(new RefactoringAgent(project));
registry.register(new CodeAnalysisAgent(project));

// During execution (AgentDelegator)
Agent agent = registry.getAgent(AgentType.RAG_SEARCH);
AgentTool tool = agent.selectTool("search for code");
ToolResult result = tool.execute(parameters, stepTrace);
```

---

## 8. Human-in-the-Loop Points

```mermaid
graph TB
    Start([User Request]) --> PlanGen[Plan Generation]

    PlanGen --> PlanValidation{Plan Validation<br/>CHECKPOINT 1}

    PlanValidation -->|Accept| StepLoop[Execute Steps Loop]
    PlanValidation -->|Reject| End1([End - Cancelled])
    PlanValidation -->|Modify| PlanGen

    StepLoop --> CheckApproval{Tool Requires<br/>Approval?}

    CheckApproval -->|No - Read-only| ExecuteTool[Execute Tool]
    CheckApproval -->|Yes - Destructive| ActionValidation{Action Validation<br/>CHECKPOINT 2}

    ActionValidation -->|Approve| ExecuteTool
    ActionValidation -->|Reject| SkipStep[Skip Step]

    ExecuteTool --> ControlCheck{User Control<br/>CHECKPOINT 3}
    SkipStep --> ControlCheck

    ControlCheck -->|Continue| MoreSteps{More Steps?}
    ControlCheck -->|Cancel| End2([End - Cancelled])
    ControlCheck -->|Pause| Paused[Paused State]

    Paused -->|Resume| MoreSteps

    MoreSteps -->|Yes| StepLoop
    MoreSteps -->|No| Complete([Complete - Show Results])

    style PlanValidation fill:#ffebee
    style ActionValidation fill:#ffebee
    style ControlCheck fill:#ffebee
    style Complete fill:#e8f5e9
```

**Checkpoints Details:**

1. **Plan Validation** (before execution starts)
   - User sees: Goal + Steps + Estimated duration
   - Actions: Accept / Reject / Modify
   - Location: `PlanValidator` → `PlanDisplayPanel`

2. **Action Validation** (during execution, for destructive tools)
   - Triggered for: `gitCommit`, `applyRefactoring`
   - User sees: Action description + Files affected
   - Actions: Approve / Reject
   - Location: `ActionValidator` → Dialog prompt

3. **User Controls** (always available)
   - Cancel: Stop execution immediately
   - Pause: Suspend and preserve state
   - Resume: Continue from paused state
   - Location: `ExecutionTracePanel` controls

---

## 9. Data Flow - Complete Example

**Scenario:** User asks "Find all TODO comments and analyze their complexity"

```mermaid
graph TB
    U1[User Request:<br/>'Find TODOs and analyze complexity'] --> O1[OrchestratorAgent]

    O1 --> P1[PlanGenerator]
    P1 --> LLM1[Ollama: Generate Plan<br/>Structured Output]
    LLM1 --> Plan[Plan with 2 steps:<br/>1. RAG: searchCode<br/>2. Analysis: analyzeComplexity]

    Plan --> V1{PlanValidator:<br/>User approves?}
    V1 -->|Yes| S1[Step 1: RAG Search]

    S1 --> RAG1[RagSearchAgent.searchCode<br/>query='TODO']
    RAG1 --> EMB1[EmbeddingModel: embed]
    EMB1 --> VS1[VectorStore: findRelevant]
    VS1 --> SR1[SourceReferences:<br/>15 files with TODOs]

    SR1 --> TR1[StepTrace 1:<br/>output: '15 TODOs found'<br/>sources: 15 SourceRefs]

    TR1 --> S2[Step 2: Code Analysis]

    S2 --> A1[CodeAnalysisAgent.analyzeComplexity<br/>files from Step 1]
    A1 --> PSI1[IntelliJ PSI: parse files]
    PSI1 --> CALC1[Calculate metrics:<br/>cyclomatic, cognitive, LOC]
    CALC1 --> SR2[SourceReferences:<br/>Complexity per method]

    SR2 --> TR2[StepTrace 2:<br/>output: 'Metrics calculated'<br/>sources: Complexity data]

    TR2 --> AGG[Aggregate Results]
    AGG --> TRACE[ExecutionTrace:<br/>- 2 steps completed<br/>- 30 total sources<br/>- Metrics: 2 LLM calls,<br/>  470 tokens, 3.5s]

    TRACE --> UI[Display in UI:<br/>- Final answer<br/>- Clickable sources<br/>- Complexity charts<br/>- Execution timeline]

    style U1 fill:#e3f2fd
    style Plan fill:#fff4e1
    style TR1 fill:#e8f5e9
    style TR2 fill:#e8f5e9
    style TRACE fill:#f3e5f5
    style UI fill:#e8f5e9
```

---

## 10. Class Diagram - Core Domain Model

```mermaid
classDiagram
    class Agent {
        <<interface>>
        +getType() AgentType
        +getDescription() String
        +getTools() List~AgentTool~
        +selectTool(action, params) AgentTool
        +isEnabled() boolean
    }

    class AgentTool {
        <<interface>>
        +getId() String
        +getName() String
        +getDescription() String
        +getParameters() List~ToolParameter~
        +execute(params, trace) ToolResult
        +requiresUserApproval() boolean
        +getOwnerAgent() AgentType
    }

    class ToolResult {
        -String output
        -Object structuredData
        -List~SourceReference~ sources
        -Map~String,Object~ metadata
        -boolean success
        -String errorMessage
        +success(output, sources)$ ToolResult
        +error(message)$ ToolResult
    }

    class Plan {
        -String goal
        -List~PlanStep~ steps
        -Integer estimatedDuration
    }

    class PlanStep {
        -int stepNumber
        -AgentType agentType
        -String action
        -String justification
        -String expectedOutput
        -List~Integer~ dependencies
    }

    class ExecutionTrace {
        -String executionId
        -Instant startTime
        -Instant endTime
        -ExecutionState finalState
        -List~StepTrace~ stepTraces
        -List~SourceReference~ allSources
        -ExecutionMetrics metrics
        +addStepTrace(stepTrace)
        +getCompletedSteps() int
    }

    class StepTrace {
        -String stepId
        -int stepNumber
        -AgentType agentType
        -String toolName
        -Instant startTime
        -Instant endTime
        -StepState state
        -Map~String,Object~ inputParameters
        -ToolResult output
        -String reasoning
        -List~SourceReference~ sources
        -List~String~ logs
        +recordStart()
        +recordSuccess(result)
        +recordError(exception)
    }

    class SourceReference {
        -String uri
        -SourceType type
        -Integer lineStart
        -Integer lineEnd
        -String snippet
        -String description
        -Double relevanceScore
        +getDisplayName() String
        +isNavigable() boolean
    }

    class ExecutionMetrics {
        -int totalLLMCalls
        -int totalTokensInput
        -int totalTokensOutput
        -double estimatedCost
        -Duration llmTotalTime
        -int failures
        +recordLLMCall(input, output, duration)
        +incrementFailures()
    }

    class AgentType {
        <<enumeration>>
        ORCHESTRATOR
        RAG_SEARCH
        GIT
        REFACTORING
        CODE_ANALYSIS
    }

    class ExecutionState {
        <<enumeration>>
        PENDING
        RUNNING
        PAUSED
        COMPLETED
        FAILED
        CANCELLED
    }

    Agent "1" --> "*" AgentTool : exposes
    AgentTool --> ToolResult : returns
    ToolResult --> "*" SourceReference : contains

    Plan "1" --> "*" PlanStep : contains
    PlanStep --> AgentType : targets

    ExecutionTrace "1" --> "*" StepTrace : contains
    ExecutionTrace "1" --> "1" ExecutionMetrics : has
    ExecutionTrace --> "*" SourceReference : aggregates

    StepTrace --> AgentType : executed by
    StepTrace --> ToolResult : produces
    StepTrace --> "*" SourceReference : references
    StepTrace --> ExecutionState : has state
```

---

## Légende des Diagrammes

### Couleurs
- 🔵 **Bleu clair** (`#e1f5ff`) : Infrastructure / Registry / Configuration
- 🟡 **Jaune** (`#fff4e1`) : Interfaces / Schemas / Intermediate objects
- 🟢 **Vert** (`#e8f5e9`) : Résultats / Outputs / Success states
- 🟣 **Violet** (`#f3e5f5`) : Traces / Observability
- 🔴 **Rouge** (`#ffebee`) : Validation points / Human-in-the-loop

### Symboles
- `→` : Data flow
- `-.->` : Implements / Extends
- `<<interface>>` : Interface Java
- `<<enumeration>>` : Enum Java
- `$` : Static method
- `*` : Multiple cardinality

---

## Résumé des Flux Clés

### 1. User Request → Agent Response
User → MessagesPanel → OrchestratorAgent → PlanGenerator → [LLM] → Plan → PlanValidator → [User Approval] → AgentDelegator → SpecializedAgent → @Tool execution → ToolResult → StepTrace → ExecutionTrace → AgentResponse → UI

### 2. Tool Execution
AgentDelegator → AgentRegistry → Agent.selectTool() → @Tool method → [External systems: VectorStore/Git/PSI/LLM] → ToolResult (output + sources)

### 3. Observability
Tool execution → ChatModelListener.onRequest/onResponse/onError → Metrics collection → ObservabilityCollector → StepTrace → ExecutionTrace → ExecutionTraceUIListener → UI updates

### 4. Human-in-the-Loop
Checkpoint 1: Plan validation (Accept/Reject/Modify)
Checkpoint 2: Destructive action approval (Approve/Reject)
Checkpoint 3: Execution controls (Continue/Cancel/Pause/Resume)

---

**Prochaine étape:** Phase 2 - Implémentation du modèle de domaine avec TDD
