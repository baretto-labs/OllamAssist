# ARCH_SECURITY.md — Security Architecture Rules

Cross-cutting security rules for the OllamAssist codebase.
These rules are **binding**. They apply to every component that touches untrusted input,
file paths, subprocess arguments, LLM prompts, or shared mutable state.

For agent-specific invariants (SI-1…SI-7) see `AGENT_ARCH.md`.
For security test requirements per component category see `TDD.md`.

---

## Rule A1 — Security decisions are fail-closed, always

Any method whose purpose is to verify, validate, or classify must produce the **restrictive**
outcome when it cannot complete normally.

```java
// Wrong — fail-open
catch (IOException e) { return true; }
if (key == null) return true;

// Correct — fail-closed
catch (IOException e) { log.warn(...); return false; }
if (key == null) { log.warn(...); return false; }
```

This applies to: HMAC verification, path guards, secret detection, command classification,
approval decisions, and any future boolean security predicate.

**The cost of a false negative (letting something through) always exceeds the cost of a
false positive (blocking something safe).** Default to block.

---

## Rule A2 — External input is validated by whitelist, not sanitised

Do not attempt to escape, strip, or transform untrusted input before passing it to an
external system (subprocess, filesystem, LLM). Sanitisation is fragile and bypassable.
Maintain an explicit whitelist of known-safe values and reject everything else.

```java
// Wrong — sanitisation
arg = arg.replaceAll("[^a-zA-Z0-9_\\-]", "");
command.add(arg);

// Correct — whitelist
if (!ALLOWED.contains(arg)) return ToolResult.failure("Argument not allowed: " + arg);
command.add(arg);
```

Applies to: subprocess arguments (`GitDiffTool`, `RunCommandTool`), LLM-provided tool IDs
(validated against `ToolRegistry.KNOWN_TOOL_IDS`), user-supplied file paths (validated
against project root), any future input that reaches an external boundary.

---

## Rule A3 — File paths are resolved and confined before use

Every code path that takes a user- or LLM-supplied file path must:

1. Resolve the path with `toRealPath()` to follow symlinks.
2. Verify it starts with the project root (`resolved.startsWith(root)`).
3. Return an explicit failure if it does not — **never** silently fall back to a safe path,
   which would hide the rejection from the caller.

```java
// Wrong — silent fallback
if (!resolved.startsWith(root)) return projectRoot; // caller never knows

// Correct — explicit failure
if (!resolved.startsWith(root)) return ToolResult.failure("Path escapes project root: " + path);
```

Use `FilePathGuard` where available. If writing a new component that manipulates paths,
prefer `FilePathGuard` over reimplementing the check inline.

---

## Rule A4 — LLM-generated content is data, not instructions

Content originating from an LLM response or from a file read by the agent must be treated
as untrusted data. Before injecting it into another LLM prompt it must pass through
`PromptSanitizer.sanitize()`, which:

- Wraps content in non-XML bracket delimiters.
- Escapes those delimiters if they appear in the content.
- Strips ASCII control characters and Unicode bidi-override sequences.

**No raw string concatenation of tool output into prompt strings.** If you find yourself
writing `"Context: " + result.getOutput()` in a prompt builder, that is a violation.

---

## Rule A5 — Truncation of outputs preserves the tail

When an output string must be truncated before injection into a prompt or stored in a log,
use a **first + last** strategy, not head-only truncation.

```java
// Wrong — root cause is at the end and gets dropped
return output.substring(0, MAX) + "...";

// Correct — keep beginning (context) and end (root cause / error)
int head = (int)(MAX * 0.6);
int tail = MAX - head;
return output.substring(0, head)
    + "\n...[" + omitted + " chars omitted]...\n"
    + output.substring(output.length() - tail);
```

Applies to: step output truncation, Critic context truncation, audit log error truncation.
Minimum tail ratio: 30% of the allowed budget.

---

## Rule A6 — Blast radius is bounded at every layer

A single agent execution must not be able to produce unbounded side effects. Three
independent guards must all be in place:

| Layer | Guard | Reset point |
|---|---|---|
| Per-tool call count | `ToolRateLimiter` per-tool limit | start of execution |
| Total tool invocations | `ToolRateLimiter.MAX_TOTAL_INVOCATIONS` | start of execution |
| Destructive step count | `validatePlan` MAX_DELETE_STEPS + Critic blast-radius check | plan validation + every ADAPT |

If you add a new tool that performs mutations (write, delete, network call), ensure it is
counted by `countDestructiveSteps` in `AgentOrchestrator` and assigned the correct tier
in `ToolRegistry`.

---

## Rule A7 — Security guards are never bypassed under time pressure

Do not add `// TODO: add validation later` stubs, optional flags, or disabled-by-default
guards when deadlines are tight. A component without its guard is not done — it is a
vulnerability with a missing test. Ship later, not unsafe.

---

## Anti-Patterns Reference

The following patterns are **never acceptable**. Cite the rule ID in the code review.

| Pattern | Rule |
|---|---|
| `catch (Exception e) { return true; }` in a security predicate | A1 |
| `if (key == null) return true;` | A1 |
| `arg.replaceAll(...)` before passing to subprocess | A2 |
| `new File(userInput)` without `toRealPath()` + root check | A3 |
| `return projectRoot` as fallback when path escapes root | A3 |
| `"Prompt: " + result.getOutput()` without `PromptSanitizer` | A4 |
| `output.substring(0, MAX) + "..."` (head-only) | A5 |
| New mutating tool not added to `countDestructiveSteps` | A6 |
