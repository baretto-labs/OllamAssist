# AGENT_ARCH.md

Architecture rules for the OllamAssist autonomous agent system.
These rules are binding for all implementation work on the agent feature.

## Vision

Transform the OllamAssist chat into a fully autonomous agent capable of reading, editing,
creating, and deleting files, running commands, and searching the codebase — with a
human-in-the-loop planning step and a continuous feedback loop.

Medium-term goal: use the agent as the execution engine for a spec-driven development
workflow directly inside the IDE.

---

## Core Loop: Plan → Validate → Execute → Critic

```
User Prompt
    ↓
[PlannerAgent]          structured output → AgentPlan
    ↓
[UI: Plan Panel]        user edits / validates / enables auto-validation
    ↓
for each logical phase:
    for each step in phase:
        [ToolDispatcher]    deterministic Java dispatch (no LLM tool calling)
            ↓
        [Tool]              self-contained execution
            ↓
        [AgentProgressNotifier]   streaming status to UI
    ↓
    [CriticAgent]           structured output → CriticDecision
        OK      → next phase
        ADAPT   → generate revised partial plan, loop
        ABORT   → report to user with full reasoning
```

---

## Rule 1: No LLM Tool Calling

Never use LangChain4j's `@Tool` / function-calling mechanism with Ollama models.
Small models are unreliable at tool selection.

- The **PlannerAgent** uses structured output to produce `AgentPlan`.
- The **CriticAgent** uses structured output to produce `CriticDecision`.
- The **ToolDispatcher** is pure deterministic Java: reads `step.toolId`, calls the matching tool.

The LLM plans. Java executes.

---

## Rule 2: Two-Level Plan Abstraction

**Level 1 — Logical steps (user-visible):**
- Readable intent: "Modify function `foo` in `Bar.java`"
- This is what appears in the Plan Panel
- Grouped into logical phases

**Level 2 — Tool calls (internal):**
- Each tool is self-contained and handles its own sub-operations
- `EditFileTool` reads the file internally, applies the edit, writes back — the plan does not contain a separate "ReadFile" step
- The user never sees raw tool names in the plan unless they expand a step

Never expose atomic tool mechanics as top-level plan steps.

---

## Rule 3: CriticAgent Trigger Granularity

Trigger the CriticAgent **after each logical phase**, not after each atomic step.

Calling an LLM after every step on a local model will produce unacceptable latency.

Exception: a `paranoid` mode (opt-in in settings) may trigger the Critic after every step
for sensitive operations (file deletion, command execution).

CriticDecision structure:
```java
record CriticDecision(
    Status status,      // OK, ADAPT, ABORT
    String reasoning,
    List<Step> revisedSteps  // non-null when ADAPT
) {}
```

---

## Rule 4: Plan UI is Inline in Chat, Not Modal

The plan is displayed as a message component inside `MessagesPanel`, inline in the
conversation flow — not as a dialog, not as a separate panel tab.

Requirements:
- Each logical step is displayed with an editable description
- User can remove or reorder steps before confirming
- Buttons: [Edit] [Validate] [Auto-validate]
- During execution: steps transition to icons: pending / running (spinner) / success / failed
- The Critic's ADAPT decisions produce a visible "plan revised" message in the same flow

This mirrors modern AI agent UIs (Cursor, Copilot Workspace, Claude Code).

---

## Rule 5: File Operations Use JetBrains Abstractions

All file read/write/delete operations must go through IntelliJ Platform APIs, never raw Java IO.

- Read: `VirtualFile.contentsToByteArray()` or `FileDocumentManager`
- Write/Edit: `WriteCommandAction.runWriteCommandAction(project, description, null, () -> { ... })`
- Delete: `VirtualFile.delete(requestor)`
- Find: `LocalFileSystem.getInstance().refreshAndFindFileByPath()`
- Search: `FilenameIndex`, `PsiManager`, `PsiShortNamesCache`

**Critical:** `WriteCommandAction` must run on the EDT. Agent steps run on background threads.
Always wrap writes with `ApplicationManager.getApplication().invokeLater(...)` or use
`WriteCommandAction.runWriteCommandAction` which handles EDT dispatch internally.

Benefit: all edits are **undoable via Ctrl+Z** in the IDE. This is non-negotiable for user trust.

---

## Rule 6: Terminal Command Security — Three Tiers

```
READ_ONLY    git status, git log, ls, find, grep, cat, mvn verify (no deploy)
             → execute directly, no confirmation

MUTATING     git commit, git add, mvn install, npm install, mkdir, touch
             → show command + confirmation UI before execution

DESTRUCTIVE  rm, git reset --hard, git push --force, DROP, truncate
             → blocked by default; requires explicit opt-in in Settings
             → always show command + explicit confirmation even when opted in
```

Classification is done by a Java matcher (regex/prefix patterns), never by the LLM.
The exact command string is always displayed to the user before any MUTATING or DESTRUCTIVE execution.

---

## Rule 7: Streaming Status is Mandatory from Day One

Latency on local models is significant. The UI must provide continuous feedback.

Every state transition publishes to `AgentProgressNotifier`:
- Plan generation started / completed
- Step started (with step description)
- Step completed (success or failure with output)
- Critic thinking
- Plan adapted (with diff of changes)
- Agent completed / aborted

There is no "silent processing" phase longer than 2 seconds without a visible status update.

---

## Tool Catalog

| ID | Class | Category | Notes |
|---|---|---|---|
| `FILE_READ` | `ReadFileTool` | Files | Via VirtualFile |
| `FILE_WRITE` | `WriteFileTool` | Files | WriteCommandAction, undoable |
| `FILE_EDIT` | `EditFileTool` | Files | Read+patch+write, undoable |
| `FILE_DELETE` | `DeleteFileTool` | Files | Confirmation if MUTATING |
| `FILE_FIND` | `FindFilesTool` | Navigation | Glob patterns via FilenameIndex |
| `CODE_SEARCH` | `SearchCodeTool` | Navigation | Text + PSI search |
| `RUN_COMMAND` | `RunCommandTool` | Terminal | 3-tier security |
| `OPEN_IN_EDITOR` | `OpenInEditorTool` | IDE | FileEditorManager |
| `GET_CURRENT_FILE` | `GetCurrentFileTool` | IDE | DataContext |
| `SEARCH_KNOWLEDGE` | `SearchKnowledgeBaseTool` | RAG | Existing LuceneEmbeddingStore |
| `GIT_STATUS` | `GitStatusTool` | Git | READ_ONLY |
| `GIT_DIFF` | `GitDiffTool` | Git | READ_ONLY |

New tools must be registered in `ToolRegistry` and assigned a tier (READ_ONLY / MUTATING / DESTRUCTIVE).

---

## Package Structure

```
fr.baretto.ollamassist.agent/
  AgentOrchestrator          # main loop coordinator
  plan/
    PlannerAgent             # LangChain4j AiService, structured output
    AgentPlan                # record: List<Phase>
    Phase                    # record: description, List<Step>
    Step                     # record: toolId, description, params Map
  critic/
    CriticAgent              # LangChain4j AiService, structured output
    CriticDecision           # record: Status, reasoning, revisedSteps
  tools/
    ToolRegistry             # maps toolId → Tool instance
    ToolDispatcher           # dispatches Step → Tool
    Tool                     # interface: execute(Map<String,Object>) → ToolResult
    ToolResult               # record: success, output, errorMessage
    files/                   # ReadFileTool, WriteFileTool, EditFileTool, DeleteFileTool
    navigation/              # FindFilesTool, SearchCodeTool
    terminal/                # RunCommandTool (with tier classification)
    ide/                     # OpenInEditorTool, GetCurrentFileTool
    rag/                     # SearchKnowledgeBaseTool
    git/                     # GitStatusTool, GitDiffTool
  ui/
    AgentPlanPanel           # Swing component rendered inline in MessagesPanel
    StepStatusComponent      # individual step with status icon
  events/
    AgentProgressNotifier    # MessageBus topic for streaming status
```

---

## Security Invariants

These invariants are **non-negotiable** for every component in the agent subsystem.
They are listed here so they can be cited during code review and during the RED phase of TDD.
An implementation that violates an invariant is incomplete, regardless of test coverage.

> Implementation patterns and anti-patterns for each invariant are documented in
> `.claude/rules/ARCH_SECURITY.md` (rules A1–A7). The SI numbers below map to those rules:
> SI-1 → A1, SI-2 → A3, SI-3 → A2, SI-4 → A4, SI-5 → A6, SI-6 → A6, SI-7 → A5.

### SI-1 — All security decisions are fail-closed

Any method whose job is to verify, validate, or classify must return the **safe / restrictive**
outcome when it cannot complete normally (null input, I/O error, missing key, unexpected state).

```
// Wrong — fail-open
if (keyFile == null) return true;

// Correct — fail-closed
if (keyFile == null) return false;
```

Applies to: `verifyHmac`, `SecretDetector.detect`, `CommandClassifier.classify`,
`FilePathGuard.*`, any future security predicate.

### SI-2 — File paths are always confined to the project root before use

Every tool that accepts a file path parameter must resolve the path through `FilePathGuard`
(or equivalent `toRealPath()` + `startsWith(root)` check) **before** performing any I/O.
A path that resolves outside the project root must produce `ToolResult.failure` — never a
silent fallback to the project root, which would hide the rejection from the agent.

Applies to: `ReadFileTool`, `WriteFileTool`, `EditFileTool`, `DeleteFileTool`,
`RunCommandTool.resolveWorkingDir`, `GoalContextResolver`.

### SI-3 — External inputs to subprocesses are whitelisted, never sanitised

Do not attempt to escape or sanitise arguments passed to external processes.
Maintain an explicit whitelist of known-safe values and reject everything else.
Sanitisation is fragile; whitelisting is verifiable.

```
// Wrong — sanitisation attempt
command.add(arg.replaceAll("[^a-zA-Z0-9_\\-]", ""));

// Correct — whitelist
if (!ALLOWED_ARGS.contains(arg)) return ToolResult.failure("Argument not allowed: " + arg);
command.add(arg);
```

Applies to: `GitDiffTool`, `GitStatusTool`, any future tool that builds a subprocess command.

### SI-4 — LLM-generated content is never trusted as instructions

Content returned by tools (file contents, command output, git diff) is data, not instructions.
Before injecting it into any LLM prompt it must pass through `PromptSanitizer.sanitize()`,
which wraps it in non-XML delimiters, escapes those delimiters if they appear in the content,
and strips control characters and bidi override sequences.

This applies even when the content looks benign. Prompt injection attacks are invisible by design.

### SI-5 — Critic-proposed plan revisions cannot escalate blast radius

When the Critic returns `ADAPT`, the revised phases are validated against:
1. The same structural rules as the original plan (unknown toolIds, MAX_DELETE_STEPS, etc.).
2. An additional check: `countDestructiveSteps(revised) ≤ countDestructiveSteps(original)`.

If either check fails, the execution is aborted. The Critic cannot grant itself more
destructive capability than the user originally approved.

### SI-6 — Rate limits and total invocation caps are enforced per execution, not per session

`ToolRateLimiter` counters reset at the start of each execution (`resetRateLimits()`).
They enforce:
- Per-tool limit: prevents a single tool from being called in a loop.
- Total invocation cap (`MAX_TOTAL_INVOCATIONS`): prevents spreading the loop across many tools.

Both limits must be enforced. Removing either one degrades the blast-radius guard.

### SI-7 — Truncation of outputs injected into LLM prompts uses first + last strategy

When a tool output is too long to inject into a prompt, keep the **first 60% and last 40%**
of the allowed budget. Never use head-only truncation: error messages, stack traces, and
root causes appear at the end of output and must reach the Critic.

---

## Non-Goals (out of scope for v1)

- LLM-based tool selection (function calling)
- Multi-agent parallelism
- Remote/cloud tool execution
- Sandboxed subprocess isolation (deferred to post-v1 security review)
