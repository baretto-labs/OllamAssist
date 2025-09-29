# Agent Mode Documentation

This directory contains documentation specific to the Agent Mode feature of OllamAssist.

## 📁 Documentation Structure

- **[AGENT_ARCHITECTURE.md](AGENT_ARCHITECTURE.md)** - Complete technical architecture and design decisions
- **[UX_DESIGN.md](UX_DESIGN.md)** - User experience flow and interface design
- **[UNIFIED_INTERFACE.md](UNIFIED_INTERFACE.md)** - Unified agent interface design and implementation
- **[IMPLEMENTATION_GUIDE.md](IMPLEMENTATION_GUIDE.md)** - Development guidelines and best practices
- **[TESTING_NATIVE_TOOLS.md](TESTING_NATIVE_TOOLS.md)** - Testing native LangChain4J tools with Ollama
- **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** - Common issues and debugging tips

## 🎯 Quick Overview

**Agent Mode** provides autonomous development assistance with user validation. Key features:

- **Step-by-step validation** - Users approve each action individually
- **Granular control** - Accept/reject specific code changes
- **Unified interface** - Single screen for chat, validation, and results
- **Hybrid execution** - Native tools with JSON fallback
- **Model compatibility** - Works with Llama 3.1+, Mistral, and legacy models

## 🏗️ Architecture Components

```
src/main/java/fr/baretto/ollamassist/core/agent/
├── AgentCoordinator.java          # Main orchestration
├── AgentService.java              # Hybrid execution (tools/JSON)
├── IntelliJDevelopmentAgent.java  # Tool definitions with @Tool
├── ui/
│   ├── AgentModePanel.java        # Legacy UI (replaced by UnifiedAgentPanel)
│   └── UnifiedAgentPanel.java     # Current unified interface
├── execution/
│   ├── ExecutionEngine.java       # Task execution coordination
│   ├── FileOperationExecutor.java # Real file operations
│   └── ...                        # Other executors
├── task/
│   ├── Task.java                  # Task definitions
│   ├── TaskResult.java            # Execution results
│   └── ...                        # Task-related classes
└── ...
```

## 🚀 Development Status

### ✅ Completed
- Basic agent architecture with tools
- File creation working (hybrid JSON approach)
- Step-by-step validation concept
- Documentation and design decisions
- **Hybrid architecture implementation (native tools + JSON fallback)**
- **Native LangChain4J tools testing with Ollama**
- **Unified interface implementation (UnifiedAgentPanel)**
- **Step-by-step validation workflow integrated**

### 🔄 In Progress
- Production testing with different Ollama models
- Model compatibility detection
- Granular diff validation implementation

### 📋 Planned
- Granular diff validation
- Auto-validation settings
- Rollback functionality
- Performance optimizations

## 🔗 Related Documentation

- **[Main CLAUDE.md](../../CLAUDE.md)** - Overall project documentation
- **[ADR-002](../adr/ADR-002-Migration-LangChain4J-Agents.md)** - Migration decision record