# AGENT_ARCH.md — Agent Architecture Rules

This file defines the binding architecture rules for the OllamAssist agent subsystem.
It supplements `ARCH_SECURITY.md` (security invariants A1–A7) and `DDD.md` (domain model).

---

## Architecture: ReAct loop via native function calling

The agent uses a **ReAct** (Reason + Act) loop built on LangChain4j's native function calling.

```
User Goal
    │
    ▼
FunctionCallingAgentService
    │  AiServices with @Tool-annotated AgentToolProvider
    │
    ├─ [Thought]      LLM reasons about the next action
    ├─ [Action]       LLM emits a tool call
    ├─ [Observation]  Framework executes the tool, returns result to LLM
    └─ repeat until final answer or MAX_TOOL_CALLS reached
```

**No upfront plan.** The LLM adapts after each observation. There is no Planner, no Critic,
no Phase/Step decomposition. The LLM reasons inline after every tool result.

---

## Rule 1 — Java executes. LLM decides.

The LLM decides *what* to call and *when*. Java executes the call and returns the result.

- Tool implementations live in `fr.baretto.ollamassist.agent.tools.*` — no LLM calls inside.
- `AgentToolProvider` exposes tools to LangChain4j via `@Tool`-annotated methods.
- `FunctionCallingAgentService` owns the `AiServices` instance and all loop guards.
- **Never** put execution logic (file I/O, subprocess, HTTP) inside a prompt or system message.

---

## Rule 2 — @Tool method contract

Every method annotated with `@Tool` must:

1. Accept only Java primitive types or `String` as parameters — never `Map<String,Object>`.
2. Carry a `@Tool` description that tells the LLM exactly what the tool does and when to use it.
   Vague descriptions produce incorrect tool calls.
3. Return a `String` — either the result content or a prefixed error message
   `"ERROR: <reason>"` so the LLM can reason about failures.
4. Never propagate checked exceptions to the LLM layer — catch internally and return an error
   string. Unchecked exceptions are caught by LangChain4j and fed back as observations.
5. Delegate all business logic to the underlying `AgentTool` implementation.
   `AgentToolProvider` is an adapter, not a domain class.

```java
// Correct
@Tool("Read the content of a file at the given path relative to the project root")
public String readFile(
    @P("File path relative to project root, e.g. src/main/java/Foo.java") String path
) {
    ToolResult result = readFileTool.execute(Map.of("path", path));
    return result.isSuccess() ? result.getOutput() : "ERROR: " + result.getError();
}

// Wrong — Map parameter, no description, throws exception
public String readFile(Map<String, Object> params) throws IOException { ... }
```

---

## Rule 3 — Loop guards are mandatory

Every agent execution must be bounded by three independent guards:

| Guard | Default | Where enforced |
|---|---|---|
| `MAX_TOOL_CALLS_PER_EXECUTION` | 30 | `FunctionCallingAgentService` |
| Execution timeout | 5 min (configurable in settings) | `FunctionCallingAgentService` |
| Per-tool rate limit | See `ToolRateLimiter` | `AgentToolProvider` before each tool call |

When `MAX_TOOL_CALLS` is reached, the service stops the loop and streams a clear
message to the user: `"Agent stopped: maximum tool calls (30) reached for this execution."`

`ToolRateLimiter.reset()` is called at the start of each new execution, not at construction.

---

## Rule 4 — Human-in-the-loop for mutating operations

Any tool that writes to the filesystem (`FILE_EDIT`, `FILE_WRITE`) must:

1. Compute the diff (before/after) before writing.
2. Publish a `FileApprovalRequestNotifier` event carrying the diff.
3. Block until the user approves or rejects (via `CompletableFuture`).
4. On rejection: return `"ERROR: User rejected the change."` to the LLM as an observation.
5. On timeout (30 s): treat as rejection.

This behaviour is bypassed only when `agentApprovalMode == AUTO` in settings.

`FILE_DELETE` always requires explicit approval regardless of `agentApprovalMode`.

---

## Rule 5 — File operations use JetBrains abstractions

All file read/write/delete operations go through IntelliJ Platform APIs, not raw Java IO.

- Read: `VirtualFile.contentsToByteArray()` or `FileDocumentManager`
- Write/Edit: `WriteCommandAction.runWriteCommandAction(project, ...)` — makes edits undoable
- Delete: `VirtualFile.delete(requestor)`
- Find: `LocalFileSystem.getInstance().refreshAndFindFileByPath()`

**Critical:** `WriteCommandAction` runs on the EDT. Agent steps run on background threads.
Always dispatch via `ApplicationManager.getApplication().invokeLater(...)`.

Benefit: all edits are **undoable via Ctrl+Z** in the IDE.

---

## Rule 6 — Terminal command security — three tiers

```
READ_ONLY    git status, git log, ls, find, grep, cat
             → execute directly, no confirmation

MUTATING     git commit, git add, mvn install, mkdir, touch
             → show command + confirmation before execution

DESTRUCTIVE  rm, git reset --hard, git push --force
             → blocked by default; explicit opt-in in settings required
             → always show command + confirmation even when opted in
```

Classification is performed by `CommandClassifier` (Java regex/prefix), never by the LLM.

---

## Rule 7 — Streaming status is mandatory

Latency on local models is significant. The UI must provide continuous feedback.

`AgentProgressEvent` is published for every state transition:
- Tool call started (tool name + parameters summary)
- Tool call completed (success or failure)
- LLM reasoning token (streamed)
- Execution stopped (completed / aborted / MAX_TOOL_CALLS reached)

There is no silent processing phase longer than 2 seconds without a visible status update.

---

## Tool Catalog

| ID | Class | Tier | Notes |
|---|---|---|---|
| `FILE_READ` | `ReadFileTool` | READ_ONLY | Via VirtualFile, 512 KB limit |
| `FILE_WRITE` | `WriteFileTool` | MUTATING | Creates file; approval required |
| `FILE_EDIT` | `EditFileTool` | MUTATING | Search/replace; diff + approval required |
| `FILE_APPEND` | `AppendFileTool` | MUTATING | Appends to existing file |
| `FILE_DELETE` | `DeleteFileTool` | DESTRUCTIVE | Always requires approval |
| `FILE_FIND` | `FindFilesTool` | READ_ONLY | Glob patterns, 100 result max |
| `LIST_DIRECTORY` | `ListDirectoryTool` | READ_ONLY | Directory listing |
| `CODE_SEARCH` | `SearchCodeTool` | READ_ONLY | Keyword search in workspace |
| `SEARCH_KNOWLEDGE` | `SearchKnowledgeBaseTool` | READ_ONLY | Semantic search via LuceneEmbeddingStore |
| `WEB_SEARCH` | `WebSearchAgentTool` | READ_ONLY | DuckDuckGo search |
| `RUN_COMMAND` | `RunCommandTool` | varies | 3-tier security via CommandClassifier |
| `OPEN_IN_EDITOR` | `OpenInEditorTool` | READ_ONLY | FileEditorManager |
| `GET_CURRENT_FILE` | `GetCurrentFileTool` | READ_ONLY | Currently open file |
| `GIT_STATUS` | `GitStatusTool` | READ_ONLY | git status output |
| `GIT_DIFF` | `GitDiffTool` | READ_ONLY | git diff output |

New tools must be registered in `ToolRegistry` with a tier before use.
Any new MUTATING or DESTRUCTIVE tool must be counted by `ToolRateLimiter`.

---

## Package Structure

```
fr.baretto.ollamassist.agent/
  FunctionCallingAgentService.java    ← loop owner, @Service(PROJECT)
  AgentToolProvider.java              ← @Tool adapter injected into AiServices
  AgentProgressEvent.java             ← streamed to UI during execution
  tools/
    AgentTool.java                    ← interface: execute(Map) → ToolResult
    ToolResult.java                   ← value object: success flag, output, error
    ToolRateLimiter.java              ← per-tool + global call limits
    ToolRegistry.java                 ← tier metadata (READ_ONLY / MUTATING / DESTRUCTIVE)
    SecretDetector.java               ← detects secrets in file content before reads
    files/                            ← ReadFileTool, WriteFileTool, EditFileTool, …
    git/                              ← GitStatusTool, GitDiffTool
    ide/                              ← OpenInEditorTool, GetCurrentFileTool
    navigation/                       ← FindFilesTool, SearchCodeTool, ListDirectoryTool
    rag/                              ← SearchKnowledgeBaseTool
    terminal/                         ← RunCommandTool, CommandClassifier, CommandTier
    web/                              ← WebSearchAgentTool
  ui/
    FileDiffPanel.java                ← shows diff before file mutation (human-in-the-loop)
```

---

## Security Invariants

These invariants are **non-negotiable** for every component in the agent subsystem.
Implementation patterns for each are in `.claude/rules/ARCH_SECURITY.md` (rules A1–A7).

### SI-1 — All security decisions are fail-closed

Any method that verifies, validates, or classifies must return the restrictive outcome
on null input, I/O error, or unexpected state — never the permissive outcome.

Applies to: `SecretDetector.detect`, `CommandClassifier.classify`, `FilePathGuard.*`,
any future boolean security predicate.

### SI-2 — File paths are confined to the project root before any I/O

Every tool that accepts a file path must call `FilePathGuard` (or `toRealPath()` +
`startsWith(root)`) before any I/O. A path that escapes the root produces
`ToolResult.failure` — never a silent fallback to the project root.

Applies to: `ReadFileTool`, `WriteFileTool`, `EditFileTool`, `DeleteFileTool`,
`RunCommandTool.resolveWorkingDir`, `GoalContextResolver`.

### SI-3 — External inputs to subprocesses are whitelisted, never sanitised

Maintain an explicit whitelist of known-safe values and reject everything else.
Do not escape or strip untrusted arguments.

Applies to: `GitDiffTool`, `GitStatusTool`, any future subprocess-building tool.

### SI-4 — LLM-generated content is treated as data, not instructions

Tool output injected into any LLM prompt must pass through `PromptSanitizer.sanitize()`
(delimiters, control character stripping, bidi override removal) before injection.

### SI-5 — Blast radius is bounded by rate limits, not by plan validation

The agent has no upfront plan to validate. Blast radius is controlled by:
1. `MAX_TOOL_CALLS_PER_EXECUTION` — absolute cap on total tool invocations.
2. `ToolRateLimiter` per-tool limits — prevents a single tool from being called in a loop.
3. Human-in-the-loop approval for every MUTATING and DESTRUCTIVE tool call.

Any new MUTATING or DESTRUCTIVE `@Tool` method must call `ToolRateLimiter.tryAcquire(toolId)`
before executing. Omitting this call is a security violation.

### SI-6 — Rate limits reset per execution, not per session

`ToolRateLimiter.reset()` is called at the start of each `FunctionCallingAgentService.execute()`
call. Counters must never carry over between user-initiated executions.

### SI-7 — Truncation of tool output uses first + last strategy

When tool output exceeds the size budget for prompt injection, preserve the first 60%
and last 40%. Head-only truncation silently drops error messages and stack traces.

---

## Agent mode stays in Preview until

1. All `@Tool` methods in `AgentToolProvider` have automated tests (happy path + adversarial).
2. Integration test demonstrating a multi-step ReAct loop with mock LLM.
3. Human-in-the-loop diff approval tested end-to-end.

---

## What was removed compared to the previous architecture

| Removed | Replaced by |
|---|---|
| `PlannerAgent` (structured JSON plan) | LLM native reasoning in the ReAct loop |
| `PlanValidator` | LangChain4j method-signature validation |
| `CriticAgent` | LLM self-corrects via observations |
| `AgentOrchestrator` | `FunctionCallingAgentService` |
| `AgentPlan`, `Phase`, `Step` | No upfront plan; LLM decides step by step |
| `StepParamResolver` | LangChain4j resolves parameters from method signatures |
| `ToolDispatcher` | LangChain4j dispatches tool calls automatically |
| `StepRecoveryEngine` | LLM retries by calling the next tool after an error observation |
| `AgentCheckpointService` | Conversation history is the state; no plan checkpoints |
| `ContinuationPlannerAgent` | No plan continuation; LLM continues naturally |
