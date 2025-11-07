# Observability System Architecture

## Core Principles
1. **Complete Traceability**: Every action traced from request to response
2. **Source Attribution**: All information linked to origin (files, lines, URLs)
3. **Reasoning Capture**: LLM decision-making visible at each step
4. **Real-time Streaming**: Progress updates during execution
5. **Minimal Performance Impact**: Async logging, efficient data structures

## Key Components

### 1. ExecutionTrace (Top-level)
- `executionId`: UUID
- `startTime`, `endTime`, `totalDuration`
- `finalState`: COMPLETED, FAILED, CANCELLED
- `stepTraces[]`: All step traces
- `allSources[]`: All sources used
- `metrics`: Performance metrics

### 2. StepTrace (Individual Step)
- `stepId`, `stepNumber`, `executionId`
- `agentType`, `toolName`, `action`
- `startTime`, `endTime`, `duration`
- `state`: PENDING, RUNNING, COMPLETED, FAILED
- `inputParameters`, `output`
- `reasoning`: LLM reasoning
- `sources[]`: SourceReferences
- `logs[]`: Detailed logs

### 3. SourceReference (Source Attribution)
```java
public class SourceReference {
    private String uri;              // File path, URL, identifier
    private SourceType type;         // FILE, URL, CLASS, COMMIT, etc.
    private Integer lineStart, lineEnd;
    private String snippet;          // Max 500 chars
    private String description;
    private Double relevanceScore;   // 0.0-1.0
    private String sourceAgent;
    private Instant timestamp;
}
```

### 4. ExecutionMetrics (Performance)
- **LLM:** totalCalls, inputTokens, outputTokens, estimatedCost
- **Performance:** llmTotalTime, toolExecutionTime, validationTime
- **Resources:** peakMemoryUsage, concurrentOperations
- **RAG:** embeddingSearches, embeddingHits, averageRelevanceScore
- **Errors:** retries, failures

### 5. ObservabilityCollector
- Manages trace collection
- Streaming to UI via listeners
- Async execution (ExecutorService)

### 6. ObservabilityListener (UI Integration)
- `onStepStarted()`
- `onStepCompleted()`
- `onStepFailed()`
- `onExecutionCompleted()`
- `onSourcesDiscovered()`
- `onReasoningCaptured()`

### 7. ChatModelListener (LangChain4J Integration)
- `onRequest()`: Captures context before LLM call
- `onResponse()`: Token usage, duration
- `onError()`: Failures with context

## Observability Levels
- **MINIMAL**: Only errors and final results
- **STANDARD**: Errors, results, step summaries
- **VERBOSE**: Everything including reasoning and logs

## Trace Export
- JSON format (ObjectMapper with JavaTimeModule)
- Markdown format (human-readable report)
- File export for analysis/debugging
