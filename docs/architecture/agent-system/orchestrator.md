# Orchestrator Architecture

## Overview
The orchestrator is the brain of the agent system. It receives user requests, generates structured plans, delegates to specialized agents, and aggregates results with complete observability.

## Core Components

### 1. OrchestratorAgent
- Coordinates plan generation → validation → execution → aggregation
- Implements human-in-the-loop pattern
- Manages execution state (running, paused, cancelled, completed)

### 2. PlanGenerator
- Uses LangChain4J Structured Outputs with JSON Schema
- Generates 1-N steps (N configurable, default 5)
- Temperature: 0.3 for deterministic planning

### 3. AgentDelegator
- Retrieves agents from AgentRegistry
- Selects @Tool methods based on action
- Executes with full observability (StepTrace)

### 4. ExecutionContext
- Shared state between plan steps
- Thread-safe via AtomicReference and ConcurrentHashMap

### 5. AgentRegistry
- Maps AgentType enum to concrete Agent implementations
- Validates agent availability
- Supports dynamic registration

### 6. PlanValidator
- Displays plan in UI before execution
- Blocks execution until user responds (Accept/Reject/Modify)

### 7. ExecutionTraceCollector
- Aggregates observability traces
- Clickable sources for navigation
- Reasoning chain visible for each step

## Integration with OllamaService
```java
@Service(Service.Level.PROJECT)
public final class OllamaService {
    private Assistant assistant;              // Existing chat mode
    private OrchestratorAgent orchestratorAgent;  // New agent mode
    private AgentMode currentMode = CHAT;
}
```

## Configuration
- `maxPlanSteps`: 1-10 (default 5)
- `executionTimeout`: seconds (default 300)
- `defaultMode`: CHAT / AGENT
- `observabilityLevel`: MINIMAL / STANDARD / VERBOSE
