# ADR-001: Architecture Refactoring for Agent Mode Support

## Status
Proposed

## Context

OllamAssist is currently designed around simple request/response interactions with AI models. We are preparing to implement **agent mode** which requires a more sophisticated architecture to handle:

- **Complex orchestration** of multiple tools (Git, Files, Web, Code Analysis)
- **Distributed state management** for agent executions
- **Multi-step workflows** with dependencies and rollbacks
- **Concurrency and cancellation** of long-running tasks
- **Observability and debugging** of agent behaviors

### Current Problems

1. **Tight coupling**: Services instantiated via static singletons (`LightModelAssistant.get()`)
2. **Fragile state management**: `volatile` fields for synchronization
3. **Ad-hoc communication**: No centralized event system
4. **Scattered configuration**: Settings spread across multiple classes
5. **Limited observability**: No centralized metrics

### Risks Without Refactoring

- **Explosive technical debt** with agent complexity addition
- **Spaghetti code** difficult to maintain and debug
- **Race conditions** and inconsistent states
- **Degraded performance** due to lack of optimized orchestration

## Decision

We proceed with a **3-phase architectural refactoring** before implementing agent mode:

### Phase 1: Foundations (1 week)
- **Dependency Injection** with ServiceLocator pattern
- **Centralized Event System** for inter-component communication
- **Centralized configuration** with type safety

### Phase 2: State and Concurrency (1 week)
- **Robust State Management** for agent executions
- **Task Orchestration** with CompletableFuture and cancellation
- **Thread-safe concurrency patterns**

### Phase 3: Observability (0.5 week)
- **Metrics and Monitoring** of performance
- **Structured logging** for debugging
- **Health checks** of services

## Consequences

### Expected Gains

#### Technical
- **Extensibility**: Easy addition of new agents and tools
- **Maintainability**: Modular code with clear responsibilities
- **Testability**: Mocking facilitated by dependency injection
- **Performance**: Optimized orchestration of concurrent tasks
- **Robustness**: Centralized error handling and automatic recovery

#### Business
- **Accelerated time-to-market** for future agents
- **Quality**: Reduction of bugs and erratic behaviors
- **User experience**: Improved responsiveness and reliability
- **Scalability**: Architecture prepared for growth

### Costs

#### Immediate
- **2-3 weeks** of development
- **Migration effort** for existing components
- **Temporary regression risk** during transition

#### Long term
- **Learning curve** for team on new patterns
- **Slightly increased initial complexity**

### Considered Alternatives

1. **Status Quo**: Continue with current architecture
   - ❌ Explosive technical debt with agent mode
   - ❌ Nightmarish long-term maintenance

2. **Partial refactoring**: Only refactor critical parts
   - ❌ Architectural inconsistency
   - ❌ Integration problems between old/new code

3. **Big Bang Rewrite**: Complete rewrite
   - ❌ Too high risk
   - ❌ Timeline incompatible with business objectives

## Implementation Plan

### Phase 1: Foundations (Week 1)

#### Day 1-2: Dependency Injection
```java
// ServiceLocator pattern
public class PluginServiceLocator {
    private static final PluginServiceLocator INSTANCE = new PluginServiceLocator();
    
    @Singleton private ModelAssistant modelAssistant;
    @Singleton private RAGService ragService;
    @Singleton private StateManager stateManager;
}

// Interface-based services
public interface ModelAssistant {
    CompletableFuture<String> chat(ChatRequest request);
    CompletableFuture<String> complete(CompletionRequest request);
}
```

#### Day 3-4: Event System
```java
public interface PluginEventBus {
    void publish(PluginEvent event);
    <T extends PluginEvent> void subscribe(Class<T> eventType, EventHandler<T> handler);
}

// Event types
public abstract class PluginEvent {
    public static class CompletionGenerated extends PluginEvent { }
    public static class AgentExecutionStarted extends PluginEvent { }
    public static class AgentExecutionCompleted extends PluginEvent { }
}
```

#### Day 5: Centralized Configuration
```java
public class PluginConfiguration {
    public AgentConfig getAgentConfig() { }
    public ModelConfig getModelConfig() { }
    public RAGConfig getRAGConfig() { }
}
```

### Phase 2: State and Concurrency (Week 2)

#### Day 1-3: State Management
```java
public interface AgentStateManager {
    CompletableFuture<AgentExecution> startExecution(AgentRequest request);
    void cancelExecution(String executionId);
    Optional<AgentExecution> getExecution(String executionId);
    List<AgentExecution> getActiveExecutions();
}

public class AgentExecution {
    private final String id;
    private final AgentRequest request;
    private volatile ExecutionState state;
    private final List<AgentStep> steps;
    private final CompletableFuture<AgentResult> future;
}
```

#### Day 4-5: Task Orchestration
```java
public interface TaskOrchestrator {
    CompletableFuture<TaskResult> orchestrate(TaskPlan plan);
    CompletableFuture<Void> executeInParallel(List<Task> tasks);
    CompletableFuture<TaskResult> executeWithTimeout(Task task, Duration timeout);
}
```

### Phase 3: Observability (Week 3, first days)

#### Day 1-2: Metrics and Monitoring
```java
public interface PluginMetrics {
    Timer agentExecutionTimer();
    Counter toolUsageCounter(String toolName);
    Gauge activeExecutionsGauge();
    
    void recordCompletionLatency(Duration latency);
    void recordRAGQueryTime(Duration queryTime);
}
```

#### Day 3: Health Checks
```java
public interface HealthCheckService {
    HealthCheck ollamaConnectionCheck();
    HealthCheck luceneIndexCheck();
    HealthCheck memoryUsageCheck();
}
```

## Validation Criteria

### Non-Regression Tests
- [ ] All existing tests pass
- [ ] Existing functionalities unchanged
- [ ] Performance maintained or improved

### New Capabilities
- [ ] Dependency injection functional
- [ ] Event system operational
- [ ] Thread-safe state management
- [ ] Metrics collected correctly

### Agent Mode Preparation
- [ ] Agent interface defined
- [ ] Task orchestrator ready
- [ ] Cancellation management implemented
- [ ] Observability in place

## Notes

This architectural decision prepares OllamAssist to become a **robust and extensible AI agent platform**, capable of handling complex workflows while maintaining a smooth user experience.

The refactoring will be performed **incrementally** with a **test-first** approach to minimize risks and ensure plugin stability.

---

**Author**: Claude Code  
**Date**: September 12, 2025  
**Reviewers**: OllamAssist Team