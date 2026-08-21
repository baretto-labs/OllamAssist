# AGENT_ARCH.md — Agent Mode Architecture

Binding rules for everything under `fr.baretto.ollamassist.agent`.
Loaded by `CLAUDE.md`. Cross-cutting security rules live in `.claude/rules/ARCH_SECURITY.md`;
this file holds the agent-specific invariants **SI-1 … SI-8**.

**Architecture decision (2026-08-19):** the agent is **Plan-then-Execute**. The
function-calling / ReAct implementation (`FunctionCallingAgentService`, `AgentToolProvider`,
`ToolRegistry`, `ToolRateLimiter`, `TextToolCallParser`, `GoalContextResolver`, the terminal
and git tools) was removed — it was unreachable from the running plugin. Do not reintroduce
a second agent architecture alongside this one. If Plan-then-Execute must be replaced, the
replacement deletes it in the same change.

---

## The three phases

`PlanAndExecuteAgentService` (project service, entry point `execute(goal, contextFiles, handler)`):

1. **Discover** — keywords extracted from the goal, workspace searched (PSI word index,
   grep fallback), the most relevant file fragments read. Bounded by `MAX_DISCOVERY_FILES`
   and `MAX_CONTEXT_CHARS`.
2. **Plan** — one LLM call. Input: goal + source roots + discovered file contents.
   Output: a JSON array of steps. No streaming of intermediate reasoning, no loop.
3. **Execute** — Java applies each step through the file tools. Any failure stops the run.

The LLM decides **what** to do, once. Java decides **whether and how** it happens.

---

## SI-1 — Fail-closed

Any method returning a security decision produces the restrictive outcome when it cannot
complete: null input, I/O error, missing settings. `ToolApprovalHelper.requestApproval()`
must never fall back to auto-approval when settings are unavailable.

## SI-2 — Path confinement

Every tool accepting a path calls `FilePathGuard.resolveConfined` before any I/O, and
returns `ToolResult.failure` when the path escapes the project root — never a silent
fallback to the project root. Symlinks are resolved, including on the ancestor chain of a
file that does not exist yet.

## SI-3 — No subprocess without a whitelist

There is currently no tool that spawns a process. If one is reintroduced, its arguments
come from an explicit whitelist — never from sanitising an LLM-provided string — and the
rejected value is named in the failure message.

## SI-4 — Untrusted content is data, not instructions

Everything injected into the planning prompt — the goal, discovered file contents, tool
output — originates outside the plugin and must pass through `PromptSanitizer.sanitize()`.
No raw concatenation of file content into a prompt builder.

## SI-5 — The plan vocabulary is a closed whitelist

`PlanAndExecuteAgentService.parseSteps` keeps only known verbs (`writeFile`, `editFile`,
`deleteFile`, `appendFile`, `answer`). Adding a verb means updating, in the same change:
the system prompt, the whitelist in `parseSteps`, `dispatchStep`, and the approval preview
in `requestPlanApproval`. A verb the approval preview cannot render must not be executable.

## SI-6 — Blast radius is bounded per execution

A single run must not produce unbounded side effects:

| Layer | Guard |
|---|---|
| Plan size | hard cap on the number of steps accepted from the LLM |
| Mutation | user Approval before the first file is touched |
| Cancellation | a cancel request stops the run at the next step boundary |

Execution state (cancellation flag, progress) belongs to **one execution**, not to the
project-level service — otherwise a new run resets the flag of the previous one.

## SI-7 — Truncation preserves the tail

Any truncation of content injected into a prompt or a log uses first + last, keeping at
least 30 % for the tail. Head-only truncation drops the error root cause.

## SI-8 — What the user approved is what gets executed

The plan shown in the approval dialog is the plan that runs. No step may be retargeted,
rewritten, or added after approval — not by path correction, not by source-root resolution,
not by an LLM repair call. If execution needs to deviate, it asks again.

Every mutating step is auditable: one `AuditLogger` record per tool invocation, which is
what `AgentHistoryPopup` reads.

---

## Tool contract

A tool implements `AgentTool`: `toolId()` + `execute(Map<String,Object> params)` returning
`ToolResult`. It never throws to the caller — failures are `ToolResult.failure(reason)`.

Every **mutating** tool (`WriteFileTool`, `EditFileTool`, `LineEditTool`, `DeleteFileTool`,
`AppendFileTool`) performs, in this order:

1. parameter validation → `failure` on missing/blank required param
2. `FilePathGuard.resolveConfined(path, project)` (SI-2)
3. `SecretDetector.detect(content)` on anything written to disk
4. syntax validation when the language supports it (`PsiSyntaxValidator`)
5. approval — covered by the plan approval, or requested by the tool, but never both and
   never neither (SI-8)
6. `WriteCommandAction.runWriteCommandAction(project, name, groupId, …)` with the execution
   correlation id as `groupId`, so one plan is one undo group

LangChain4j `@Tool` / function-calling APIs (`dev.langchain4j.agent.tool.*`) are **not**
used anywhere in this codebase. Do not add them back to reach a tool.

---

## Known gaps (audit 2026-08-19)

Open defects in the current implementation, to fix in this order. Each one is a violation
of the invariant named next to it.

Fixed since the audit: **C1** — the planning context now carries the real line numbers of
each file, gaps name the lines they hide, and the phantom line a trailing newline used to
produce is gone.

| # | Gap | Invariant |
|---|---|---|
| C2 | `buildPlanningMessage` concatenates goal and file contents raw; `PromptSanitizer` is not called | SI-4 |
| C3 | `LineEditTool` has no approval, no `SecretDetector`, no syntax validation | SI-8 / tool contract |
| M1 | `resolveEditFilePath`, `SourceRootResolver.findInSourceRoots` and `fixSyntaxError` rewrite steps after approval | SI-8 |
| M2 | `writeFile` / `deleteFile` / `appendFile` request a second approval after the plan approval | SI-8 |
| M3 | `executionCancelled` lives on the service and is reset by the next run | SI-6 |
| M4 | `AuditLogger` is never called, so `AgentHistoryPopup` is always empty | SI-8 |
| M5 | `AgentMemoryService` is only ever cleared; nothing writes or reads it | — |
| M6 | Four agent settings (plan/tool/global timeouts, paranoid mode) are read by nobody | — |
| M7 | The syntax self-repair path targets `writeFile` failures with an `editFile` fix | correctness |
| M8 | `__correlationId` is never put in `AgentStep.toParams()`, so undo is not grouped | tool contract |
| M9 | `extractKeywords` returns nothing for a plain-language goal, so the planner gets no context | correctness |
| M10 | No plan size cap | SI-6 |
