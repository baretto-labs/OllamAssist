# Specialized Agents Architecture

## Agent Interface
All agents implement the `Agent` interface:
- `getType()`: Returns AgentType
- `getDescription()`: Human-readable capabilities
- `getTools()`: List of @Tool methods
- `selectTool()`: Selects tool for action
- `isEnabled()`: Check if enabled in settings

## Agents Summary

| Agent | Tools | Requires Approval | Dependencies |
|-------|-------|-------------------|-------------|
| **RAG/Search** | searchCode, searchDocumentation, searchSimilarCode | No | LuceneEmbeddingStore, EmbeddingModel |
| **Git** | gitStatus, gitDiff, gitCommit, gitLog | Yes (commit) | Git4Idea plugin, DiffGenerator |
| **Refactoring** | analyzeCode, suggestRefactoring, applyRefactoring | Yes (apply) | PSI, Refactoring APIs, ChatModel |
| **Code Analysis** | analyzeComplexity, analyzeDependencies, detectCodeSmells | No | PSI, JavaPsiFacade |

## 1. RAG/Search Agent
**Tools:**
- `searchCode(query, maxResults)`: Semantic code search
- `searchDocumentation(query)`: Search docs/comments
- `searchSimilarCode(codeSnippet, language)`: Find similar code

**Observability:** Each result includes relevanceScore, file paths, line numbers

## 2. Git Agent
**Tools:**
- `gitStatus()`: Repository status
- `gitDiff(filePath, staged)`: File diffs
- `gitCommit(message, files)`: Create commit **[REQUIRES APPROVAL]**
- `gitLog(maxCount, author)`: Commit history

**Observability:** Each file/commit becomes a SourceReference

## 3. Refactoring Agent
**Tools:**
- `analyzeCode(filePath)`: Detect refactoring opportunities (PSI analysis)
- `suggestRefactoring(codeSelection, language)`: AI-powered suggestions
- `applyRefactoring(filePath, refactoringType, parameters)`: Apply refactoring **[REQUIRES APPROVAL]**

**Metrics:** Cyclomatic complexity, method length, code smells

## 4. Code Analysis Agent
**Tools:**
- `analyzeComplexity(filePath, methodName)`: Calculate metrics
- `analyzeDependencies(target)`: Dependency graph
- `detectCodeSmells(filePath)`: Detect anti-patterns

**Code Smells:** God Class, Long Method, Long Parameter List, Feature Envy, Data Clumps, Duplicate Code, Dead Code

## ToolResult Structure
```java
public class ToolResult {
    private String output;
    private Object structuredData;
    private List<SourceReference> sources;
    private Map<String, Object> metadata;
    private boolean success;
    private String errorMessage;
}
```
