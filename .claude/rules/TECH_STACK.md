# TECH_STACK.md — Technical Stack Rules

Constraints on the technology stack used in OllamAssist.
These rules are **binding**. They prevent version drift, API misuse, and architectural decay.

---

## Language — Java 21

The codebase targets Java 21. Use modern Java idioms — they exist to reduce boilerplate
and improve correctness, not for style points.

**Use when applicable:**

| Feature | Use for |
|---|---|
| `record` | Value objects (`ToolResult`, `AgentStep`, `RagSource`, …) |
| Sealed classes + `switch` expression | Exhaustive matching on domain enums |
| Pattern matching (`instanceof`) | Replace `if (x instanceof Foo) { Foo f = (Foo) x; }` |
| Text blocks (`"""`) | Multi-line prompt strings, JSON fixtures in tests |
| `var` | Local variables where the type is obvious from context — not on fields |
| `Stream` + collectors | Replace explicit loops that build collections |

**Never use:**

- Raw types (`List` instead of `List<Step>`)
- Checked exceptions for domain errors — use `ToolResult.failure(...)` or `Optional`
- `null` as a return value from public methods — return `Optional` or a typed failure

---

## LangChain4j — stay on the current minor series

**Current versions (update this block when bumping):**

```
langchain4jVersion      = 1.13.1
langchain4jEasyRag      = 1.13.1-beta23
```

**Rules:**

- **Update at least every 8 weeks.** LangChain4j releases frequently; staying behind more
  than two minor versions means missing structured-output fixes that directly affect agent
  reliability on local models.
- Before bumping, run `./gradlew test` and `./gradlew benchmark` to catch regressions.
- **`@Tool` / function-calling APIs** (`dev.langchain4j.agent.tool.*`) are **not used** in
  this codebase. The agent is Plan-then-Execute: one planning call, Java dispatch. See
  `AGENT_ARCH.md`. Do not reintroduce them to reach a tool.
- Always exclude `org.slf4j` and `org.apache.lucene` from every LangChain4j dependency
  to avoid conflicts with IntelliJ's bundled versions. Pattern:
  ```kotlin
  implementation("dev.langchain4j:langchain4j-xxx:$version") {
      exclude(group = "org.slf4j")
      exclude(group = "org.apache.lucene")
  }
  ```
- LangChain4j types (`TextSegment`, `Embedding`, `ChatMessage`, `AiMessage`) must not
  appear outside `chat/service/` and `chat/rag/`. Convert to domain types at the boundary
  (`RagSource`, `ConversationMessage`). See `DDD.md` Anti-Corruption Boundaries.

---

## Architecture — Hexagonal (Ports & Adapters)

The long-term target architecture is hexagonal. The domain must be isolatable from
IntelliJ Platform, LangChain4j, and the filesystem without changing business logic.

### The three zones

```
┌─────────────────────────────────────────────────┐
│  Driving side (UI, actions, MessageBus)         │  IntelliJ Platform, Swing
│  calls Ports →                                  │
├─────────────────────────────────────────────────┤
│  Domain (pure Java, no framework imports)       │  business logic, value objects
│  ← implements / defines Ports                   │
├─────────────────────────────────────────────────┤
│  Driven side (LLM, filesystem, Lucene, git)     │  adapters to external systems
│  ← called by Domain through secondary Ports     │
└─────────────────────────────────────────────────┘
```

### Practical rules

**Domain classes must not import:**
- `com.intellij.*`
- `dev.langchain4j.*`
- `java.io.File` / `java.nio.file.*` (use ports instead)
- Any persistence framework

Domain classes that currently violate this are **known technical debt** (see `DDD.md`
Known Violations). Do not add new violations.

**Adapters live in `ui/`, `service/`, or dedicated adapter sub-packages.**
An adapter converts between a domain type and a framework type. It does not contain
business logic. If you find yourself writing an `if` or a `for` loop in an adapter,
the logic belongs in the domain.

**Ports are Java interfaces.**
A secondary port (driven side) is an interface defined in the domain package and
implemented by an adapter. Example:
```java
// Domain package — no framework import
public interface ConversationRepository {
    void save(Conversation conversation);
    Optional<Conversation> findById(String id);
}

// Adapter package — IntelliJ / filesystem allowed
public class JsonConversationRepository implements ConversationRepository { ... }
```

**New services follow the port-first pattern:**
1. Define the interface in the domain package.
2. Write unit tests against the interface with a fake implementation.
3. Write the adapter that implements the interface.
4. Wire the adapter via IntelliJ `@Service` registration.

---

## IntelliJ Platform — 2024.3 (build 243) minimum

- `sinceBuild = "243"` — do not lower this constraint.
- Never use deprecated IntelliJ APIs marked for removal in 2025+.
- Background work: always `ApplicationManager.getApplication().executeOnPooledThread(...)`.
  Never `new Thread(...)` directly.
- EDT writes: always `WriteCommandAction.runWriteCommandAction(...)` — provides undo support.
- Service access: `project.getService(Foo.class)` (project-scoped) or
  `ApplicationManager.getApplication().getService(Foo.class)` (application-scoped).
  Never `ServiceManager.getService(...)` (deprecated).

### Never show a modal dialog from a startup activity

A `ProjectActivity` / `postStartupActivity` must never open a `DialogWrapper` with
`setModal(true)` or a `Messages.show*` dialog — not even through `invokeLater`.
Startup UI must be a balloon (`NotificationGroupManager` + the `OllamAssist` group);
a dialog is opened only from an explicit user action on that balloon.

Why: a modal dialog shown at startup parks the EDT inside `Dialog.show()` until the user
closes it. The JetBrains Marketplace automated verification (`InstallPluginTest`) opens the
IDE headlessly with no one to click OK, so it times out after 5 minutes
(`Timeout(5m): Failed: Indicators with waitSmartLongEnough=true`) and the upload is rejected
with "Plugin must not remove the IDE Trial widget". Caused by
`DialogNotificationDisplayer` on the 2026-08-17 upload.

Any displayer that shows UI must also bail out early when
`application.isUnitTestMode() || application.isHeadlessEnvironment()`.

Detection: `grep -rn "postStartupActivity" src/main/resources/META-INF/plugin.xml`, then
check that no transitive call from those classes reaches `DialogWrapper.show()` or
`Messages.show*`.

---

## Test stack — versions are fixed

| Library | Version | Notes |
|---|---|---|
| JUnit Jupiter | 5.11 | Default engine for all unit tests |
| Mockito | 5.19 | Mock collaborators, never the class under test |
| AssertJ | 3.27 | All assertions — never use `assertEquals` |
| Testcontainers | 1.21 | Integration tests that need a real process |

**Do not add new test frameworks** (Spock, Kotest, Truth, etc.) without a team discussion.
Consistency in the test codebase matters more than individual framework preferences.

---

## Lombok — restricted usage

Lombok is a compile-time convenience, not a design tool. Use it narrowly.

**Allowed:**
- `@Slf4j` — logging field injection
- `@NoArgsConstructor(access = AccessLevel.PRIVATE)` — utility classes
- `@Builder` — complex constructors where named parameters matter

**Forbidden:**
- `@Data` on domain objects — generates mutable `equals`/`hashCode`/`toString`,
  breaks value-object semantics. Use `record` instead.
- `@EqualsAndHashCode` on mutable classes — cache-poisoning risk.
- `@SneakyThrows` — hides checked exceptions, violates the fail-closed rule (A1).
- `@Getter` as the *only* accessor on a `PersistentStateComponent.State` class —
  see the persistence rule below. It compiles, it reads fine, and it silently disables
  persistence.

---

## PersistentStateComponent — state fields must be public

Every field of a `PersistentStateComponent.State` class must be **public, non-final,
non-static**. Do not make them private and expose them through Lombok `@Getter`.

Why: IntelliJ's `XmlSerializer` binds a field only if it is public, or if the class exposes
a matching getter **and** setter (`PropertyCollector` is configured with
`COLLECT_PRIVATE_FIELDS = false`, and `isAcceptableProperty` rejects a getter with a null
setter unless the return type is a `Collection`/`Map` carrying a store annotation).
A private field with only a `@Getter` is bound by neither path: it is never written to the
XML file and never read back, so the setting silently reverts to its field initializer on
every IDE restart. This shipped as issue #170 — "Chat System Prompt resets to default on
CLion restart" — and affected `PromptSettings` and `ActionsSettings` identically.

Detection: for every class implementing `PersistentStateComponent`, its `State` class must
have zero occurrences of `private ` among its field declarations. Public fields plus a
`@Getter` is fine (the read-only property is discarded, the field binding wins) — that is
what `RAGSettings` and `OllamaSettings` do.

### Corollary — state values must be BMP-only

`XmlSerializer` silently drops characters it cannot store in an XML attribute: ASCII control
characters and everything outside the Basic Multilingual Plane (emoji are surrogate pairs).
`"a🔧b"` round-trips as `"ab"`, with no error and no log line.

Never put emoji in a default value that gets persisted — a user who edits the text around it
persists the whole string and gets it back mangled. `DEFAULT_CHAT_SYSTEM_PROMPT` contained
`🔧` and `👋`; both were removed in the #170 fix.

Detection: the state round-trip test asserts the default values survive
`XmlSerializer.serialize` → `deserialize` verbatim
(`SettingsStatePersistenceTest`). Any new persisted default is covered by adding a case there.

---

## Jackson — 2.20+

- Use `record` types with `@JsonProperty` on the canonical constructor parameters.
- Add `@JsonIgnoreProperties(ignoreUnknown = true)` on every class that is
  deserialised from LLM output or persisted to disk — forward compatibility.
- Do not use `ObjectMapper.readValue` directly in domain classes; wrap it in a
  repository or serializer adapter.
- `SerializationFeature.INDENT_OUTPUT` in development / persistence paths;
  disabled for audit log JSONL (one record per line, no indentation).

---

## Dependency hygiene

- **No new transitive dependencies** without explicit `implementation(...)` declaration.
  If a class you need comes from a transitive dependency, add it explicitly.
- **Exclude SLF4J and Lucene** from every third-party dependency that bundles them
  (LangChain4j, DJL, Tika). IntelliJ bundles both; duplicates cause class-loading issues.
- **Do not add `spring-*` or `quarkus-*` dependencies.** This is an IDE plugin, not a server.
  Framework auto-wiring conflicts with IntelliJ's class loader.
- Run `./gradlew dependencies` and review the output before adding any new library.
