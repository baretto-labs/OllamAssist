# Agent Mode Architecture

## 🎯 Vision and User Experience

Agent Mode provides autonomous development assistance with user validation. The core principle is **user control** - every action must be validated before execution.

### Design Principles
1. **Step-by-step validation** - Users review and approve each action individually
2. **Granular control** - Ability to accept/reject specific code changes within a file
3. **Unified interface** - Single screen showing chat, progress, and results
4. **Transparency** - Clear visibility into what the agent wants to do
5. **Rollback capability** - Ability to undo agent actions

### User Experience Flow
```
User Request → Agent Analysis → Action Plan → Step-by-Step Validation → Execution → Results
```

**Example Workflow:**
```
👤 User: "Refactor UserService class"
🤖 Agent: "I propose 4 actions:"
         "1. Create IUserService interface"
         "2. Modify UserService.java (3 changes)"
         "3. Add unit tests"
         "4. Update documentation"

📋 Step 1/4: Create IUserService.java
   📄 [Code preview showing interface]
   [✅ Create] [❌ Skip] [🔧 Modify]

📋 Step 2/4: Modify UserService.java
   📊 Diff View:
   ✅ [✓] Rename getUser() → fetchUser()
   ❌ [✗] Remove deprecated method
   📝 User feedback: "Keep old method, add @Deprecated"
   [🔄 Regenerate] [✅ Apply selected] [❌ Skip]

☑️ Auto-validate similar changes for remaining steps
```

## 🏗️ Technical Architecture

### Core Components

**1. Agent Orchestration Layer**
```
AgentCoordinator → AgentService → Tool Execution → Real Actions
```

**2. Tool System (Hybrid Architecture)**
```java
// Primary: Native LangChain4J Tools (when supported)
@Tool("Create a Java class file")
public String createJavaClass(
    @P("Class name") String className,
    @P("File path") String filePath,
    @P("Class content") String content) {
    // Direct tool execution via LangChain4J
}

// Fallback: JSON-based Tool Execution (compatibility)
{
  "actions": [
    {
      "tool": "createJavaClass",
      "parameters": {
        "className": "HelloWorld",
        "filePath": "src/main/java/HelloWorld.java",
        "classContent": "public class HelloWorld { ... }"
      }
    }
  ],
  "message": "Class created successfully"
}
```

### Model Compatibility Strategy

**Tool-Compatible Models (Native execution):**
- Llama 3.1 (8B, 70B, 405B) ✅
- Llama 3.2 (1B, 3B) ✅
- Mistral/Mixtral ✅
- Firefunction ✅
- Granite 3.0 ✅

**Legacy Models (JSON fallback):**
- Llama 2.x → JSON parsing
- Code Llama → JSON parsing
- Other models without function calling → JSON parsing

### Architecture Layers

**1. UI Layer - UnifiedAgentPanel**
```java
public class UnifiedAgentPanel {
    private ChatArea chatArea;                    // User interaction
    private StepByStepValidator stepValidator;    // Action validation
    private ProgressMonitor progressMonitor;      // Real-time progress
    private ResultsPanel resultsPanel;           // Action results
    // Single screen - no tab switching
}
```

**2. Coordination Layer - AgentCoordinator**
```java
@Service(Service.Level.PROJECT)
public class AgentCoordinator {
    private AgentService agentService;
    private ActionPlanValidator validator;
    private ExecutionEngine executionEngine;

    public CompletableFuture<ActionPlan> planActions(String userRequest);
    public CompletableFuture<ActionResult> executeAction(AgentAction action);
    public Stream<AgentEvent> getEventStream(); // For real-time UI updates
}
```

**3. Agent Service Layer - Hybrid Execution**
```java
public interface AgentExecutor {
    CompletableFuture<ActionPlan> generatePlan(String userRequest);
    boolean supportsNativeTools();
}

// Implementation A: Native Tools (preferred)
public class NativeToolExecutor implements AgentExecutor {
    private final AiServices langchainAgent; // With @Tool methods
}

// Implementation B: JSON Fallback (compatibility)
public class JsonFallbackExecutor implements AgentExecutor {
    private final ObjectMapper jsonParser;
    private final ToolRegistry toolRegistry;
}
```

**4. Tool Execution Layer**
```java
public interface AgentTool {
    ToolResult execute(ToolParameters params);
    ToolSchema getSchema();
    boolean canExecute(ToolParameters params);
}

// Available Tools:
- CreateFileTool: Create any file with content
- ModifyFileTool: Modify existing files with diff
- AnalyzeCodeTool: Code analysis and recommendations
- GitOperationTool: Git commands (commit, push, pull, etc.)
- BuildProjectTool: Compile, test, package operations
```

**5. Execution Engine - Real Actions**
```java
public class ExecutionEngine {
    private Map<TaskType, TaskExecutor> executors;

    // Real executors (not debug/simulation):
    - FileOperationExecutor: VFS operations with IntelliJ
    - GitOperationExecutor: Git commands
    - BuildOperationExecutor: Gradle/Maven operations
    - CodeAnalysisExecutor: Static analysis
}
```

## 🎨 User Interface Design

### Unified Agent Interface
```
┌─────────────────────────────────────────────────────────────┐
│                    OllamAssist Agent Mode                   │
├─────────────────────────────────────────────────────────────┤
│ Chat Area                    │ Current Action               │
│                              │                              │
│ 👤 "Refactor UserService"    │ 📋 Step 2/4: Modify File    │
│                              │                              │
│ 🤖 "I'll create interface    │ 📄 UserService.java         │
│     and refactor class"      │                              │
│                              │ 📊 Diff View:               │
│ [💬 New message...]         │ ✅ [✓] Add getAllUsers()     │
│                              │ ❌ [✗] Remove deprecated     │
│                              │                              │
│                              │ 📝 Feedback:                │
│                              │ "Keep deprecated method"     │
│                              │                              │
│                              │ [🔄 Regenerate] [✅ Apply]   │
├─────────────────────────────────────────────────────────────┤
│ Progress: ████████░░ 2/4     │ Results: 1 file created     │
│ ☑️ Auto-validate similar     │ 📁 IUserService.java ✅     │
└─────────────────────────────────────────────────────────────┘
```

## 🔧 Implementation Decisions

### Why Hybrid Architecture (Tools + JSON)?

**Decision**: Support both native LangChain4J tools AND JSON fallback

**Rationale**:
1. **Future-proof**: Ready for function calling when available
2. **Compatibility**: Works with all Ollama models
3. **Performance**: Native tools are faster when supported
4. **Reliability**: JSON fallback ensures consistent behavior

### Why Step-by-Step Validation?

**Decision**: Validate each action individually, not batch approval

**Rationale**:
1. **User control**: Developers want to see exactly what's happening
2. **Risk mitigation**: Prevent unwanted modifications
3. **Learning**: Users see what the agent is doing and why
4. **Flexibility**: Can modify agent behavior mid-execution

### Why Unified Interface?

**Decision**: Single screen instead of tabs (Chat, Tasks, Progress)

**Rationale**:
1. **Cognitive load**: No context switching between views
2. **Workflow efficiency**: See input, validation, and results together
3. **Real-time feedback**: Immediate visibility of agent actions
4. **Scalability**: Interface grows with features, not complexity

## 🚀 Development Guidelines

### Adding New Tools

1. **Create Tool Interface**:
```java
@Tool("Tool description for LLM")
public String myNewTool(@P("Parameter description") String param) {
    // Implementation
}
```

2. **Add JSON Support**:
```java
// In JsonFallbackExecutor
case "myNewTool":
    return developmentAgent.myNewTool(
        parameters.get("param").asText()
    );
```

3. **Add UI Support**:
```java
// In ActionValidator for complex validations
if (action.getTool().equals("myNewTool")) {
    return new CustomValidator(action);
}
```

### Testing Strategy

**1. Tool Testing**:
```java
@Test
public void testCreateFileToolDirect() {
    // Test tool directly without LLM
    String result = developmentAgent.createFile("test.txt", "content");
    assertThat(result).contains("success");
}
```

**2. Integration Testing**:
```java
@Test
public void testAgentWorkflow() {
    // Test complete workflow with mock responses
    when(ollama.generate(any())).thenReturn(mockJsonResponse);
    AgentResult result = agentService.execute("create HelloWorld");
    assertThat(result.getActions()).hasSize(1);
}
```

## 🔍 Debugging and Monitoring

### Log Levels and Patterns
```
🚀 AGENT SERVICE  - High-level agent operations
🔧 EXECUTOR       - Tool/executor selection
🎯 TOOL EXECUTION - Individual tool calls
📁 FILE OPERATION - File system operations
🔍 JSON PARSING   - JSON processing and validation
⭐ COORDINATION   - Workflow coordination
📊 UI EVENTS      - User interaction events
```

## 🎯 Success Metrics

**Technical Metrics**:
- Tool execution success rate > 95%
- Average action validation time < 30 seconds
- UI response time < 500ms
- Zero data loss during agent operations

**User Experience Metrics**:
- Actions accepted without modification > 80%
- User session completion rate > 90%
- Time to complete development tasks (vs manual)
- User satisfaction with validation workflow