# DDD.md — Domain-Driven Design Rules

This file defines the domain rules, ubiquitous language, and bounded context boundaries
for OllamAssist. These rules are **binding** for all implementation work.

---

## Ubiquitous Language

These are the canonical terms for this project. Use them consistently in class names,
method names, variable names, comments, commit messages, and documentation.
**Never invent synonyms.** If a term is missing, add it here before using it.

### Core Domain Terms

| Term | Definition | Wrong alternatives |
|------|-----------|-------------------|
| **Conversation** | A named, persistent thread of messages between the user and the assistant | Session, Chat, Thread, Dialog |
| **Message** | A single exchange unit in a Conversation, authored by a Role | Line, Entry, Turn |
| **Role** | The author of a Message: `USER` or `ASSISTANT` | Sender, Actor, Side |
| **Goal** | The natural-language intent a user submits to the Agent | Task, Request, Command, Input |
| **Plan** | The structured decomposition of a Goal into Phases and Steps | Roadmap, Schedule, Execution plan |
| **Phase** | A logical group of Steps inside a Plan, evaluated by the Critic as a unit | Stage, Block, Group |
| **Step** | A single atomic execution unit inside a Phase, dispatched to one Tool | Action, Operation, Job |
| **Tool** | A self-contained capability the Agent can invoke (file read, git status, etc.) | Function, Command, Handler |
| **ToolResult** | The outcome of a Tool execution: success flag, output, error message | Response, Output, Return |
| **Critic** | The LLM agent that evaluates Phase results and decides to continue, adapt, or abort | Reviewer, Evaluator, Validator |
| **CriticDecision** | Structured output of the Critic: `OK`, `ADAPT`, or `ABORT` with reasoning | Verdict, Judgment, Outcome |
| **Suggestion** | An inline code completion proposed to the developer inside the editor | Completion, Hint, Proposal |
| **CompletionContext** | The editor state snapshot used to generate a Suggestion | Context, EditorState, Snippet |
| **RagSource** | A retrieved knowledge fragment with its origin: `INDEX`, `WORKSPACE`, or `WEB` | Result, Match, Document, Hit |
| **KnowledgeIndex** | The Lucene-backed vector store of the project's indexed files | Database, Store, Corpus, Vector DB |
| **EmbeddingModel** | The model used to encode text into vectors for semantic retrieval | Embedding, Model, Encoder |
| **ChatModel** | The Ollama model used for the main conversational assistant | LLM, Model, Assistant model |
| **CompletionModel** | The lightweight Ollama model used for inline code suggestions | Light model, Fast model |
| **Assistant** | The LangChain4j-backed AI service that responds to chat messages | Bot, AI, Model, LLM |
| **Prerequisite** | A runtime dependency that must be satisfied before the plugin operates (Ollama reachability, embedding model availability) | Requirement, Dependency, Check |
| **Approval** | An explicit user confirmation required before a mutating or destructive operation | Confirmation, Permission, Consent |
| **CommandTier** | The security classification of a terminal command: `READ_ONLY`, `MUTATING`, or `DESTRUCTIVE` | Level, Category, Risk |
| **IndexingPipeline** | The async batch processor that ingests workspace files into the KnowledgeIndex | Indexer, Ingestion, Pipeline |
| **Workspace** | The IntelliJ project directory and its files as seen by the plugin | Project, Repo, Codebase |

---

## Bounded Contexts

Each context owns its domain objects. Cross-context references must go through
published events or explicit anti-corruption interfaces — never by reaching into
another context's internals.

### 1. Chat Context
**Responsibility:** Manage the conversational exchange between user and assistant.

**Owns:** `Conversation`, `ConversationMessage`, `Role`, `Assistant`, `ChatThread`

**Produces events:** `NewUserMessageNotifier`, `ConversationNotifier`, `ConversationSwitchedNotifier`

**Consumes:** `RagSource` (read-only, from RAG Context via `ContextRetriever`)

**Rules:**
- A `Conversation` is the aggregate root. All messages belong to exactly one Conversation.
- The `ChatThread` manages streaming lifecycle only — it does not hold business logic.
- UI components (`OllamaMessage`, `UserMessage`, `MessagesPanel`) must not contain domain logic.
  Domain logic belongs in `ChatThread`, `OllamaService`, or `ConversationService`.

---

### 2. Agent Context
**Responsibility:** Autonomously execute a Goal through a Plan, with human-in-the-loop validation.

**Owns:** `Goal`, `Plan`, `Phase`, `Step`, `Tool`, `ToolResult`, `CriticDecision`, `CommandTier`

**Produces events:** `AgentProgressNotifier`, `FileApprovalNotifier`

**Consumes:** Chat Context (renders plan inline in `MessagesPanel`), RAG Context (`SearchKnowledgeBaseTool`)

**Rules:**
- The LLM plans. Java executes. No LLM tool calling. See `AGENT_ARCH.md` Rule 1.
- A `Step` is immutable once the Plan is validated. If the Critic requests adaptation,
  a new `Phase` is produced — the original Step is never mutated.
- `CommandTier` classification is always performed by `CommandClassifier` (Java regex),
  never inferred from LLM output.
- Every Tool must be registered in `ToolRegistry` with its `CommandTier` before use.

---

### 3. RAG Context
**Responsibility:** Index workspace knowledge and retrieve relevant fragments for augmentation.

**Owns:** `KnowledgeIndex`, `RagSource`, `IndexingPipeline`, `EmbeddingModel`

**Produces events:** `StoreNotifier`

**Consumes:** Workspace file system events (via `ProjectFileListener`)

**Rules:**
- `RagSource.sourceType` distinguishes `INDEX` (Lucene), `WORKSPACE` (live file), and `WEB` (DuckDuckGo).
  Never conflate these three origins.
- The `KnowledgeIndex` is per-project. Application-level singletons must not hold project-scoped state.
- Retrieval score thresholds (min score, top-K) are configuration, not hardcoded constants scattered in retriever classes.
  They belong in `RAGConstants` or `RAGSettings`.

---

### 4. Completion Context
**Responsibility:** Provide inline code Suggestions within the editor.

**Owns:** `Suggestion`, `CompletionContext`, `CompletionModel`

**Produces:** inlay hints via `InlayRenderer`

**Consumes:** editor state only — no dependency on Chat or Agent contexts

**Rules:**
- A `Suggestion` is ephemeral. It is never persisted.
- `CompletionContext` is a value object: built once, immutable, discarded after use.
- The `CompletionModel` is always a separate Ollama model from the `ChatModel`.
  Never reuse the `ChatModel` for completion — latency requirements differ.

---

### 5. Settings Context
**Responsibility:** Store and expose user configuration for all other contexts.

**Owns:** `OllamaSettings`, `RAGSettings`, `PromptSettings`, `ActionsSettings`, `OllamAssistUISettings`

**Rules:**
- Settings are passive data holders. They do not call services or publish events directly.
- When a setting change must trigger runtime behavior (e.g., model reload), publish the appropriate
  domain event (`ChatModelModifiedNotifier`, `StoreNotifier`) from the UI panel — not from the settings class itself.
- `OllamAssistSettings` is a legacy aggregator. Do not add new fields to it.
  Add new settings to the appropriate typed settings class (`OllamaSettings`, `RAGSettings`, etc.).

---

### 6. Conversation Context
**Responsibility:** Persist, load, and switch between named Conversations.

**Owns:** `Conversation` (aggregate root), `ConversationMessage`, `ConversationRepository`, `ConversationService`

**Rules:**
- `Conversation` title is auto-generated from the first message (max 60 characters).
  The user may not rename it in v1.
- `ConversationMessage` is a value object: immutable once appended to a Conversation.
- Persistence is file-based JSON under `.ollamassist/conversations/`. No external DB.

---

### 7. Notification Context
**Responsibility:** Deliver version-aware plugin notifications to the user.

**Owns:** `Notification`, `NotificationManager`, `NotificationStorage`

**Rules:**
- `Notification` is identified by `(id, version)`. A notification with the same id but a higher
  version replaces the previous one.
- This context is entirely internal. It does not consume domain events from other contexts.

---

## Naming Rules

### Classes

| Concept | Suffix | Example |
|---------|--------|---------|
| Aggregate root or primary entity | none or domain noun | `Conversation`, `AgentPlan` |
| Value object | none or domain noun | `CriticDecision`, `RagSource`, `ToolResult` |
| Domain service | `Service` | `ConversationService`, `OllamaService` |
| Repository (persistence) | `Repository` | `ConversationRepository` |
| Application service / orchestrator | `Orchestrator` or `Pipeline` | `AgentOrchestrator`, `DocumentIndexingPipeline` |
| IntelliJ Platform Tool | `Tool` | `ReadFileTool`, `GitStatusTool` |
| Event listener interface (MessageBus) | `Notifier` | `ChatModelModifiedNotifier` |
| UI component (Swing) | `Panel`, `Renderer`, `Action` | `MessagesPanel`, `InlayRenderer`, `AskToChatAction` |
| Background task | `Task` | `InitEmbeddingStoreTask` |
| Factory | `Factory` | `DocumentIngestFactory` |
| Registry | `Registry` | `ToolRegistry`, `IndexRegistry` |

### Events (MessageBus Topics)

- Format: `{Trigger}{DomainObject}Notifier` — e.g., `ChatModelModifiedNotifier`, `ConversationSwitchedNotifier`
- The interface method must describe the event in past tense or present action: `onChatModelModified()`, `onConversationSwitched(Conversation)`
- Topic constant must be named `TOPIC` (not `NEW_MESSAGE_TOPIC`, `MY_TOPIC`, etc.) — use the interface
  itself as the discriminator.

### Packages

```
fr.baretto.ollamassist.
  agent/           → Agent Context
  chat/            → Chat Context (service/, rag/, ui/, tools/, askfromcode/)
  completion/      → Completion Context
  conversation/    → Conversation Context
  notification/    → Notification Context
  setting/         → Settings Context
  events/          → All MessageBus topic interfaces (cross-cutting)
  prerequisite/    → Bootstrap / health-check (note: fix the existing typo `prerequiste/` on next refactor)
  auth/            → Authentication helpers
  git/             → Git utilities (commit message generation, diff)
  component/       → Shared Swing components (used across contexts)
  utils/           → Pure stateless utilities
```

**Rules:**
- A class belongs to one package (one context). No circular package dependencies.
- `events/` is the only cross-cutting package. All other packages are context-local.
- UI classes (`Panel`, `Action`, `Renderer`) live in a `ui/` sub-package of their context.
  They never sit at the root of a context package.

---

## Entity vs Value Object Rules

**Entity** (has identity, mutable over time):
- `Conversation` — identified by `id`, messages accumulate over time
- `AgentOrchestrator` — stateful execution coordinator

**Value Object** (defined by its values, immutable):
- `ConversationMessage` — immutable once created; use factory methods `user(content)` / `assistant(content)`
- `AgentPlan`, `Phase`, `Step` — immutable once produced by the Planner
- `CriticDecision` — immutable evaluation result
- `ToolResult` — immutable execution result
- `RagSource` — immutable retrieval fragment
- `CompletionContext` — immutable snapshot of editor state

**Rule:** Value objects must not have setters. Use constructors or static factory methods.
If a value object needs to "change", produce a new instance.

---

## Anti-Corruption Boundaries

These boundaries prevent domain concepts from leaking across contexts:

1. **LangChain4j types** (`TextSegment`, `Embedding`, `ChatMessage`) must not appear outside
   the `chat/rag/` and `chat/service/` packages. Convert to domain types (`RagSource`,
   `ConversationMessage`) at the boundary.

2. **IntelliJ Platform types** (`VirtualFile`, `PsiFile`, `AnActionEvent`) must not appear
   in domain service classes. UI and platform integration belongs in `ui/` sub-packages
   and dedicated adapters.

3. **Ollama HTTP types** must not leak past `OllamaService` and `LightModelService`.

---

## Known Violations to Fix (Technical Debt)

| Violation | Location | Priority |
|-----------|----------|---------|
| Package typo `prerequiste/` | `fr.baretto.ollamassist.prerequiste` | Low (rename on next touch) |
| `Context` record ambiguity (UI context vs domain context) | `chat/ui/Context.java` | Medium (rename to `ChatUIContext`) |
| `OllamAssistSettings` accumulates all settings | `setting/OllamAssistSettings.java` | Medium (no new fields; migrate gradually) |
| `SuggestionManager` / `MultiSuggestionManager` naming inconsistency with `Completion` vocabulary | `completion/` | Low |
| `ToolCallDetector` / `ToolCallParser` share the word "Tool" with agent tools | `chat/tools/` | Low (rename to `LlmToolCall*`) |
| `PrerequisteAvailableNotifier` misspelling in Topic name | `events/` | Low (fix on next touch) |