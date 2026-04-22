# TDD.md — Test-Driven Development Rules

All non-trivial implementation work must follow the classic TDD cycle.
These rules are binding. Do not write production code without a failing test first.

---

## The Cycle

```
RED   → Write the smallest failing test that describes one behavior
GREEN → Write the minimum production code to make it pass (no more)
BLUE  → Refactor if needed, keeping all tests green
        → then back to RED for the next behavior
```

Never skip steps. Never write two behaviors in one cycle.

---

## RED — Write a Failing Test First

- The test must fail before any production code is written or modified.
- Test one behavior at a time. If the test description needs "and", split it.
- Name the test after the behavior, not the implementation:
  - Good: `shouldReturnEmptyWhenConversationHasNoMessages()`
  - Bad: `testGetMessages()`, `test1()`
- Use the Arrange / Act / Assert structure, one assertion per concept.
- The test must be runnable and actually fail (compile errors count as red).

---

## GREEN — Make It Pass, Nothing More

- Write the simplest code that turns the test green.
- Hardcoding a return value is acceptable at this stage if it makes the test pass.
- Do not anticipate future requirements. Do not add parameters, overloads,
  or abstractions that no test requires yet.
- If the production code feels wrong, that is fine — refactor comes next.

---

## BLUE — Refactor Under Green Tests

- Refactor only when all tests are green.
- Target: remove duplication, clarify names, simplify structure.
- Do not add behavior during refactor. If a refactor idea implies new behavior, stop,
  write a test for it first (RED), then implement.
- After refactoring, run the full test suite. If anything breaks, revert the refactor
  and approach it differently.

---

## Cycle Discipline

- One cycle = one commit (or one logical unit of work). Keep cycles short.
- If a cycle takes more than ~15 minutes, the test covers too much — split it.
- Never batch multiple behaviors into a single RED → GREEN → BLUE pass.
- The commit message must reference what behavior was added or fixed, not the file changed.

---

## Test Scope Rules

**Unit tests** (`src/test/java/`):
- Test a single class in isolation.
- Mock collaborators with Mockito. Do not mock the class under test.
- Do not mock IntelliJ Platform services — use lightweight fakes or the `BasePlatformTestCase`
  test fixture when platform integration is needed.
- Run in milliseconds. No filesystem, no network, no Ollama.

**Integration tests** (also `src/test/java/`, suffixed `IT` or in a dedicated package):
- Test a slice of the system end-to-end (e.g., indexing pipeline + retrieval).
- Use real infrastructure (real Lucene index on a temp directory).
- Clearly separated from unit tests so they can be excluded from fast feedback loops.

**Benchmark tests** (`src/benchmark/java/`):
- Performance only. Not a substitute for correctness tests.

---

## What Not To Test

- Private methods — test through the public API. If a private method feels complex enough
  to test directly, it signals a missing class.
- Framework wiring (Spring DI, IntelliJ service registration) — trust the framework.
- Trivial getters and setters with no logic.
- UI rendering details — test the behavior triggered by the UI, not pixel positions.

---

## Test Quality Rules

- A test that never fails is not a test. Verify the RED step every time.
- Tests must be deterministic. No `Thread.sleep`, no random seeds, no order dependency.
- Tests must be independent. Each test sets up its own state; no shared mutable state.
- Use AssertJ for assertions (`assertThat(...).isEqualTo(...)`) — not JUnit `assertEquals`.
- Use `@DisplayName` to write human-readable test descriptions when the method name is not enough.

---

## Application to This Codebase

| Area | Strategy |
|------|----------|
| Domain value objects (`ToolResult`, `CriticDecision`, `ConversationMessage`) | Pure unit tests, no mocks needed |
| Services (`ConversationService`, `AgentOrchestrator`) | Unit tests with mocked repositories and notifiers |
| Tools (`ReadFileTool`, `GitStatusTool`) | Integration tests with real temp filesystem or git repo |
| RAG pipeline (`DocumentIndexingPipeline`, `HybridRetriever`) | Integration tests with real Lucene temp index |
| UI components (`MessagesPanel`, `PromptPanel`) | Avoid; test the action/service they delegate to |
| LLM responses (`PlannerAgent`, `CriticAgent`) | Unit tests with mocked `StreamingChatModel` |

---

## Security Testing Requirements

Security properties are not optional behaviors — they are invariants.
Each category below lists the tests that **must exist** before a class in that category is
considered done. A TDD cycle for a security-sensitive class is incomplete without them.
Write the adversarial test in RED before writing the guard in GREEN.

### Any class that accepts a file path as input
(`ReadFileTool`, `WriteFileTool`, `EditFileTool`, `DeleteFileTool`, `RunCommandTool`, `GoalContextResolver`, …)

| Required test | Input | Expected outcome |
|---|---|---|
| Path traversal blocked | `../../etc/passwd` | `ToolResult.failure` or exception |
| Symlink escape blocked | path that symlinks outside project root | `ToolResult.failure` or exception |
| Relative path confined | `src/../../../tmp/evil` | confined to project root or rejected |
| Null/blank path handled | `null` or `""` | graceful failure, no NPE |

### Any class that accepts external arguments fed to a subprocess
(`GitDiffTool`, `GitStatusTool`, future shell-wrapper tools)

| Required test | Input | Expected outcome |
|---|---|---|
| Unknown argument rejected | `"--format=malicious"` | `ToolResult.failure` |
| Shell metacharacter rejected | `"; rm -rf /"` | `ToolResult.failure` |
| Valid argument accepted | `"HEAD~1"` | passes through |
| Empty/null argument ignored | `null` or `""` | no argument appended |

### Any method that returns a boolean security decision
(`SecretDetector.detect`, `verifyHmac`, `CommandClassifier.classify`, `FilePathGuard.*`)

| Required test | Scenario | Expected outcome |
|---|---|---|
| Fail-closed on null input | key is `null` | returns `false` / `failure` — never `true` |
| Fail-closed on I/O error | file unreadable | returns `false` / `failure` — never `true` |
| Known-bad input detected | hardcoded real pattern | returns non-null label / `true` |
| Known-good input passes | normal source file | returns `null` / `false` |

### Any method that injects external content into an LLM prompt
(`PromptSanitizer`, `GoalContextResolver`, `AgentMemoryService.recentContextSummary`)

| Required test | Input | Expected outcome |
|---|---|---|
| Delimiter injection escaped | content containing `<<TOOL_DATA>>` | delimiter replaced with escaped variant |
| Bidi control characters stripped | content with `\u202E` (RTLO) | characters removed |
| Truncation preserves tail | 10 000-char string truncated to 2 000 | last chars present in output |
| Null input handled | `null` | returns safe empty placeholder, no NPE |

### Any class that manages shared mutable state across threads
(`ToolRateLimiter`, `AgentMemoryService`, `AuditLogger`, `AgentOrchestrator`)

| Required test | Scenario | Expected outcome |
|---|---|---|
| Reset is atomic | `reset()` called during `tryAcquire()` | no stale count after reset |
| Limit enforced under concurrent calls | N threads call `tryAcquire` past limit | exactly `limit` calls allowed |
| Dispose is idempotent | `dispose()` called twice | no exception |

### Any new `AgentTool` implementation

Before merging a new tool, all of the following must have a failing test written first:

1. Happy-path execution returns `ToolResult.success` with expected output.
2. Missing required param returns `ToolResult.failure` (not NPE).
3. At least one adversarial input test from the relevant category above.
4. Tool is registered in `ToolRegistry` — test that `ToolRegistry.get(toolId)` is non-null.

If a tool falls into multiple categories above, all relevant adversarial tests are required.