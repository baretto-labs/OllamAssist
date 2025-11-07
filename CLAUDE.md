# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Communication Language

**IMPORTANT**: Always communicate with the user in French, but write and comment code in English.

## Project Overview

OllamAssist is a JetBrains IntelliJ IDEA plugin that integrates with Ollama to provide AI-powered development assistance. It's written in Java with a Gradle Kotlin DSL build system and leverages LangChain4J for AI model integration.

## Quick Reference

### Prerequisites
- Java 21 (JDK 21)
- Ollama installed and running locally

### Core Commands
```bash
./gradlew build          # Build the plugin
./gradlew test           # Run tests
./gradlew benchmark      # Run benchmarks
./gradlew check          # Run all checks
```

### Testing Strategy - **CRITICAL**
**IMPORTANT: Test-First Development (TDD)**
- **Always write tests first** before implementing features
- RED-GREEN-REFACTOR cycle mandatory
- **Never reduce test quality** - fix implementation instead
- **Target coverage: >80%**
- Unit tests: `src/test/java/` (JUnit Jupiter + AssertJ + Mockito)

## Current Architecture

### Core Components
**Plugin:** `OllamAssistStartup`, `OllamaWindowFactory`
**AI/Chat:** `OllamaService`, `Assistant`, `LightModelAssistant`
**RAG:** `LuceneEmbeddingStore`, `DocumentIndexingPipeline`, `WorkspaceContextRetriever`
**Code Completion:** `InlineCompletionAction` (Shift+Space), `SuggestionManager`
**Git:** `CommitMessageGenerator`, `DiffGenerator`, `MyersDiff`
**Refactoring:** `RefactorAction`, `ApplyRefactoringAction`
**UI:** `MessagesPanel`, `PresentationPanel`, `PromptPanel`
**Services:** `PrerequisiteService`, `OllamAssistSettings`, `IndexRegistry`

### Dependencies
- LangChain4J (1.8.0), LangChain4J Easy RAG (1.8.0-beta15), LangChain4J Agentic (1.8.0)
- Apache Lucene, DJL
- IntelliJ IDEA Community 2024.3, Git4Idea
- RSyntaxTextArea, Jackson, Jsoup

## Agent System Architecture (New Development)

### Overview
Evolution from RAG chat to **autonomous agent system** using **LangChain4J Agents**.

### Stack Technique

**LangChain4J Components (Maximum utilization):**
- **SupervisorAgent** - Autonomous orchestrator
- **@Agent** - Interface-based agents
- **AgenticScope** - Shared state
- **HumanInTheLoop** - User validation
- **@Tool** - Method-level tools with auto-discovery
- **Structured Outputs** - JSON Schema for plan generation
- **ChatModelListener** - Observability hooks
- **Workflows** - Sequential, parallel, loop, conditional

**OllamAssist Custom (Observability):**
- **SourceReference** - File/line/URL tracking with navigation
- **ExecutionTrace/StepTrace** - Execution history with reasoning
- **ExecutionMetrics** - Token usage, cost, performance
- **ToolResult** - Results with SourceReference[] for traceability
- **OllamAssistAgenticScope** - Wrapper combining AgenticScope + traces

### Architecture Principles
1. **SupervisorAgent Pattern** - LLM decides which agents to invoke
2. **Maximum LangChain4J** - Use native components
3. **Observability First** - Complete traceability
4. **Human-in-the-Loop** - User validation (HumanInTheLoop)
5. **Incremental Delivery** - Each phase independently deliverable

### Agents (@Agent interfaces)
- **RagSearchAgent:** Search codebase (searchCode, searchDocumentation, searchSimilarCode)
- **GitAgent:** Git operations (gitStatus, gitDiff, gitCommit, gitLog)
- **RefactoringAgent:** Code improvements (analyzeCode, suggestRefactoring, applyRefactoring)
- **CodeAnalysisAgent:** Quality analysis (analyzeComplexity, analyzeDependencies, detectCodeSmells)

### Project Structure (Agent System)
```
src/main/java/fr/baretto/ollamassist/
├── agent/
│   ├── model/              # Plan, PlanStep, AgentType, ExecutionState
│   ├── observability/      # ExecutionTrace, StepTrace, SourceReference, Metrics
│   ├── tool/               # AgentTool, ToolResult, ToolParameter
│   └── integration/        # OllamAssistAgenticScope
├── chat/ui/                # AgentModeToggle, PlanDisplayPanel, ExecutionTracePanel
└── setting/                # AgentSettings
```

**Note:** Specialized agents are @Agent **interfaces**, not classes. Tools are separate classes implementing `AgentTool`.

## Implementation Plan

**Current Status:** Phase 2 (Modèle de domaine) starting

**Documentation:** `docs/architecture/agent-system/` contains detailed docs:
- `implementation-plan.md` - 10 phases, 35-45 days
- `phase2-final-plan.md` - Detailed Phase 2 TDD plan
- `orchestrator.md` - SupervisorAgent architecture
- `specialized-agents.md` - Agent tools and implementations
- `observability.md` - Trace and metrics system
- `diagrams.md` - 10 Mermaid diagrams
- `langchain4j-patterns.md` - LangChain4J patterns research
- `langchain4j-agents-integration.md` - Integration analysis

**Phases:**
1. 🟢 Fondations - Architecture documentation (COMPLETED)
2. 🟡 Modèle de domaine - Base classes with tests (STARTING)
3. 🔴 Orchestrateur MVP - Basic orchestrator
4. 🔴 Agent RAG - First specialized agent
5. 🔴 UI mode agent - Complete UI
6. 🔴 Configuration - Settings
7. 🔴 Agent Git - Git operations
8. 🔴 Agent Refactoring - Code improvements
9. 🔴 Agent Code Analysis - Quality analysis
10. 🔴 Tests d'intégration - E2E tests

## Key Architectural Decisions

1. **Tools (@Tool)** - Flexible, auto-discovered, easy to extend
2. **Structured Outputs** - Type-safe, eliminates parsing errors
3. **Sequential Execution** - Simpler reasoning, easier debugging
4. **Shared ChatMemory** - Maintains context when switching modes
5. **Multi-Level HumanInTheLoop** - Safety, transparency, control

## Integration Points

**OllamaService Enhancement:**
```java
@Service(Service.Level.PROJECT)
public final class OllamaService {
    private Assistant assistant;              // Existing chat mode
    private SupervisorAgent supervisorAgent;  // New agent mode
    private AgentMode currentMode = CHAT;
}
```

**Reused Components:**
- `OllamaStreamingChatModel` / `OllamaChatModel` - LLM calls
- `LuceneEmbeddingStore` - RAG agent
- `OllamAssistSettings` - Configuration
- IntelliJ `MessageBus` - Notifications
- `DiffGenerator` - Git agent

## Development Notes

- Plugin targets IntelliJ Platform 243+ (2024.3+)
- Java 21 source and target compatibility
- Lombok for boilerplate reduction
- TDD approach: Write tests first, then implementation
- Plan tracking: This file + `docs/architecture/agent-system/`
