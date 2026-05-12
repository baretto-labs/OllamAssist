# INTROSPECTION.md — Self-Improvement Rules

When a user corrects, refuses, or asks to modify a deliverable, treat it as a signal.
Extract the underlying rule and persist it immediately so the same mistake never recurs.

---

## When to trigger

Trigger introspection when any of the following occurs:

- The user rejects or modifies a proposed implementation ("non, pas comme ça", "change X")
- The user corrects an assumption ("tu avais tort sur…")
- The user asks for the same thing a second time (signals the first delivery missed something)
- A `/security-check` or audit produces a finding on code just written in this session
- A test written by Claude fails because the implementation was wrong, not the test

Do not wait to be asked. Introspection is part of completing the task.

---

## What to extract

Ask: **what rule, if it had existed before, would have prevented this?**

| Signal | Where to persist |
|---|---|
| Behavioral preference ("réponds toujours en français", "pas d'emojis") | `~/.claude/projects/.../memory/` as `feedback_*.md` |
| Project-specific constraint not yet documented | `CLAUDE.md` or the relevant `MEMORY.md` entry |
| Recurring architectural pattern or anti-pattern | `.claude/rules/` — extend an existing rule file or create a new one |
| Security invariant gap | `.claude/rules/ARCH_SECURITY.md` — add a new rule Ax |
| Missing test category | `.claude/rules/TDD.md` — add a row to the Security Testing Requirements table |
| Repeated workflow step that should be automated | `.claude/skills/` — create or extend a skill |
| Stack constraint (version, library, pattern) | `.claude/rules/TECH_STACK.md` |

If the rule fits an existing file, extend that file rather than creating a new one.
Only create a new file when the topic has no natural home in the existing set.

---

## How to write the rule

A rule extracted from a correction must answer three questions:

1. **What** — the constraint itself, stated as an imperative ("always", "never", "must")
2. **Why** — the incident or reasoning that motivated it (one sentence)
3. **How to detect** — a concrete signal that would catch a violation before delivery

Example of a well-formed extracted rule:

```
## Rule: truncation preserves the tail

Always use first + last strategy when truncating outputs injected into LLM prompts.
Why: head-only truncation silently drops stack traces and error root causes,
     causing the Critic to make recovery decisions on incomplete information.
     (Found in audit v6, CRITICAL-4.)
Detection: grep for `substring(0, MAX)` followed by string concat in prompt builders.
```

---

## Persistence is immediate

Do not defer. Write the rule before moving on to the next task.

If multiple rules can be extracted from a single correction, write all of them.
A correction that fixes one symptom but leaves the pattern unaddressed will recur.

---

## After persisting

Confirm to the user what was extracted and where it was saved.
One sentence per rule: "J'ai ajouté une règle dans `ARCH_SECURITY.md` : …"

Do not ask for permission to persist — do it, then report it.
