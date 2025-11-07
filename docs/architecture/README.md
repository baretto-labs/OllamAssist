# OllamAssist Architecture Documentation

This directory contains detailed architecture documentation for the OllamAssist plugin.

## Agent System Architecture

The Agent System is the evolution of OllamAssist from a simple RAG chat to an autonomous multi-agent system powered by LangChain4J.

### Documentation Files

- **[orchestrator.md](agent-system/orchestrator.md)** - Orchestrator architecture
  - Core components: OrchestratorAgent, PlanGenerator, AgentDelegator, ExecutionContext
  - Integration with OllamaService
  - Configuration and constraints

- **[specialized-agents.md](agent-system/specialized-agents.md)** - Specialized agents architecture
  - Agent interface and patterns
  - RAG/Search Agent (semantic code search)
  - Git Agent (repository operations)
  - Refactoring Agent (code analysis and suggestions)
  - Code Analysis Agent (complexity, dependencies, code smells)

- **[observability.md](agent-system/observability.md)** - Observability system
  - ExecutionTrace, StepTrace, SourceReference
  - Performance metrics and monitoring
  - Real-time streaming to UI
  - Trace export (JSON, Markdown)

- **[implementation-plan.md](agent-system/implementation-plan.md)** - Implementation roadmap
  - 10 phases, 35-45 days total
  - Detailed sub-tasks with TDD approach
  - Current status tracking
  - Acceptance criteria for each phase

## Architecture Principles

1. **Orchestrator Pattern** - Central coordinator delegates to specialized agents
2. **Hybrid Approach** - Structured Outputs (orchestrator) + Tools (@Tool) (agents)
3. **Observability First** - Complete traceability with sources and reasoning
4. **Human-in-the-Loop** - User validation at plan and action levels
5. **Incremental Delivery** - Each phase independently deliverable to production

## Quick Links

- Main documentation: [/CLAUDE.md](../../CLAUDE.md)
- Implementation plan: [agent-system/implementation-plan.md](agent-system/implementation-plan.md)
- Current phase: **Phase 1 (Fondations) 🟡 In Progress**
