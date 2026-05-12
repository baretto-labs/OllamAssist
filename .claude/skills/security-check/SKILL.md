---
name: security-check
description: Pre-commit security review of agent subsystem changes against SI-1…SI-7 invariants (function-calling ReAct architecture)
---

You are performing a **pre-commit security review** of the agent subsystem changes in this repository.

## Your task

1. Run `git diff HEAD` to get the full diff of all staged and unstaged changes.
2. For each changed file that belongs to `fr.baretto.ollamassist.agent`, apply the checklist below.
3. Report findings grouped by invariant ID. For each finding state: file, line range, invariant violated, exact issue, and a one-line fix.
4. If no violations are found for an invariant, write a single green line: `SI-X — OK`.
5. End with a **VERDICT**: `PASS` (nothing blocking) or `BLOCK` (at least one violation must be fixed before commit).

---

## Security Invariants Checklist

### SI-1 — Fail-closed
- [ ] Every method that returns a boolean security decision returns `false`/`failure` on null input, I/O error, or missing configuration — never `true`.
- Watch for: `if (x == null) return true`, catch blocks that return `true`, missing null checks before security predicates.
- Includes: `ToolApprovalHelper.requestApproval()` settings-unavailable fallback must NOT auto-approve.

### SI-2 — Path confinement
- [ ] Every tool that accepts a file path calls `FilePathGuard` or performs `toRealPath()` + `startsWith(root)` before any I/O.
- [ ] A path that escapes the project root produces `ToolResult.failure` — not a silent fallback to project root.
- Watch for: `new File(userInput)` without validation, silent catch blocks that return `projectRoot`.

### SI-3 — Subprocess argument whitelist
- [ ] No external argument is passed to a subprocess without an explicit whitelist check.
- [ ] Rejected arguments produce `ToolResult.failure` with the rejected value named.
- Watch for: `split("\\s+")` fed directly to `command.add(...)`, `replaceAll` sanitisation attempts.

### SI-4 — Prompt injection defence
- [ ] Every tool output injected into an LLM prompt passes through `PromptSanitizer.sanitize()`.
- [ ] No raw string concatenation of tool output into a prompt string.
- Watch for: `"Context: " + result.getOutput()` in prompt or system-message builders without sanitization.

### SI-5 — Blast radius bounded (function-calling architecture)
- [ ] Every new `@Tool` method in `AgentToolProvider` calls `checkAborted()` as its first line.
- [ ] Every new `@Tool` method calls `rateLimiter.tryAcquire(toolId)` before executing the underlying tool.
- [ ] Every new MUTATING or DESTRUCTIVE `@Tool` method publishes `FileApprovalRequestNotifier` before writing to disk.
- [ ] `FunctionCallingAgentService.MAX_TOOL_CALLS_PER_EXECUTION` has not been raised without justification.
- Watch for: new `@Tool` methods missing `checkAborted()`, missing `tryAcquire`, or skipping approval.

### SI-6 — Rate limits reset per execution
- [ ] `ToolRateLimiter.reset()` is called at the start of each `FunctionCallingAgentService.execute()` call.
- [ ] `AgentToolProvider.resetAbort()` is called at the start of each execution.
- [ ] Both resets happen BEFORE the agent starts, not at construction time.
- Watch for: reset calls moved to constructors, or removed entirely.

### SI-7 — Truncation strategy
- [ ] Any new truncation of tool output uses first + last strategy (never head-only).
- [ ] The split ratio keeps at least 30% for the tail.
- Watch for: `output.substring(0, MAX) + "..."` without preserving the tail.

---

## Additional checks (not invariants, but flag if found)

- A new `@Tool` method has no unit test in `AgentToolProviderTest` → flag as WARNING.
- A new `@Tool` method has no adversarial input test (path traversal, null param, rate limit) → flag as WARNING.
- A new `AgentTool` implementation has no test for missing required params → flag as WARNING.
- A security method has 0 test coverage for the failure path → flag as WARNING.

---

## Output format

```
## Security Check — <date>

### SI-1 — Fail-closed
[OK | FINDING: file:line — description — fix]

### SI-2 — Path confinement
...

---
VERDICT: PASS | BLOCK
Reason: <one sentence if BLOCK>
```

Be precise about line numbers. Do not invent issues. If you cannot determine whether a pattern is safe without more context, say so explicitly rather than reporting a false positive.
