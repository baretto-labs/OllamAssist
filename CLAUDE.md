# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Communication Guidelines

- Respond to chat questions in French
- All code, comments, documentation, and commit messages must be in English
- Do not use emojis in responses

## Project Overview

OllamAssist is a JetBrains IDE plugin that integrates Ollama-powered AI capabilities directly into the development workflow. The plugin provides:
- In-IDE chat with Ollama models
- RAG (Retrieval-Augmented Generation) with workspace context
- Smart code autocompletion
- AI-powered commit message generation
- Code refactoring suggestions

## Build and Development Commands

### Building the Plugin
```bash
./gradlew build
```
This runs tests, benchmark tests, and builds the plugin distribution.

### Running Tests
```bash
# Run unit tests only
./gradlew test

# Run benchmark tests
./gradlew benchmark

# Run all tests
./gradlew check
```

### Running the Plugin in Development
```bash
./gradlew runIde
```
This launches a sandboxed IntelliJ IDEA instance with the plugin installed.

### Building Plugin Distribution
```bash
./gradlew buildPlugin
```
Output is in `build/distributions/`.

### Code Quality
```bash
./gradlew sonar
```
Requires `SONAR_TOKEN` environment variable.

## Architecture Overview

### Core Components

**IntelliJ Platform Integration:**
- `OllamAssistStartup` - Project startup activity that performs async prerequisite checks
- `OllamaWindowFactory` - Registers the chat tool window on the right panel
- Services use `@Service(Service.Level.PROJECT)` or `@Service(Service.Level.APPLICATION)`
- Plugin configuration in `src/main/resources/META-INF/plugin.xml`

**Service Architecture:**
- **Application Services:** `OllamAssistSettings`, `PrerequisiteService`, `IndexRegistry` (singleton across all projects)
- **Project Services:** `OllamaService`, `DocumentIndexingPipeline`, `LuceneEmbeddingStore`, `WorkspaceContextRetriever` (per-project instances)

### RAG Implementation

The plugin implements a custom RAG system for workspace-aware AI responses:

**Document Indexing Pipeline (`DocumentIndexingPipeline`):**
- Batch processor with async queue (batch size: 10, sync: 100)
- Scheduled executor runs every 30 seconds
- Thread-safe with `ReentrantLock` and `Phaser` synchronization
- Retry mechanism (max 3 retries per file)
- Use `flush()` for immediate indexing

**Embedding Store (`LuceneEmbeddingStore`):**
- Custom Apache Lucene-backed store
- Storage location: `{project}/.ollamassist/database/knowledge_index/`
- Thread-safe with read-write locks
- Stores vector embeddings, text content, and metadata

**Context Retrieval (`ContextRetriever`):**
- Composite retriever that parallelizes three sources:
  1. **EmbeddingStoreContentRetriever** - Semantic search (top-2, min score 0.85)
  2. **WorkspaceContextRetriever** - Current file context (5000-char window)
  3. **DuckDuckGoContentRetriever** - Web search (if enabled)
- Global timeout: 2 seconds
- Uses `CompletableFuture` for parallel execution

**File Monitoring (`ProjectFileListener`):**
- Monitors file creation, modification, deletion via `VirtualFileListener`
- Debounces changes with 1-minute window
- Updates embedding store automatically
- Respects `.gitignore` patterns

### Ollama Integration

**OllamaService (Project Service):**
- Creates `OllamaStreamingChatModel` from LangChain4j library
- Model parameters: temperature=0.7, topK=50, topP=0.85, timeout=300s
- Supports separate Ollama URLs for chat/completion/embedding models
- Optional Basic Auth via `AuthenticationHelper`
- Chat memory: `MessageWindowChatMemory` (max 25 messages)

**Assistant Interface:**
- Built using LangChain4j's `AiServices` framework
- System prompt enforces "Tree of Thoughts" reasoning
- Streaming responses via `TokenStream`
- Integrates with custom `ContextRetriever`

### Event System

The plugin uses IntelliJ's `MessageBus` for loose coupling:

| Event | When Published | Purpose |
|-------|----------------|---------|
| `ModelAvailableNotifier` | Models initialized | Shows UI when ready |
| `NewUserMessageNotifier` | User sends chat message | Triggers AI response |
| `ConversationNotifier` | Settings change | Clears chat history |
| `ChatModelModifiedNotifier` | Model changed | Reloads assistant |
| `StoreNotifier` | Files indexed | Updates embedding store |
| `PrerequisteAvailableNotifier` | Ollama checked | Shows prerequisite status |

### Chat System

**Message Flow:**
1. User input in `PromptPanel`
2. `AskToChatAction` publishes `NewUserMessageNotifier`
3. `OllamaContent` subscribes and creates `ChatThread`
4. `ChatThread` streams tokens from `Assistant.chat()`
5. Tokens render in real-time in `MessagesPanel`
6. Code blocks highlighted with `RSyntaxTextArea`

**Key Classes:**
- `ChatThread` - Manages streaming lifecycle
- `OllamaMessage` / `UserMessage` - Message UI components
- `SyntaxHighlighterPanel` - Code syntax highlighting
- `Context` - Conversation state holder

### Code Completion System

**Completion Pipeline (Shift+Space):**
1. `InlineCompletionAction` triggered
2. `EnhancedCompletionService` with:
   - `CompletionDebouncer` (300ms delay)
   - `SuggestionCache` (LRU cache)
   - `EnhancedContextProvider` (extracts code context)
3. `LightModelAssistant` generates completions
4. `MultiSuggestionManager` handles multiple options
5. `InlayRenderer` displays inline suggestions

**Navigation:**
- Enter: Accept suggestion
- Tab: Next suggestion
- Shift+Tab: Previous suggestion

### Settings System

**OllamAssistSettings (PersistentStateComponent):**
- Stored in `OllamAssist.xml` in IDE config directory
- Configuration includes:
  - Separate Ollama URLs for chat/completion/embedding models
  - Model names for each task
  - Timeout duration
  - RAG/web search toggles
  - Authentication credentials

**ConfigurationPanel:**
- Settings UI with model dropdowns (auto-populated from Ollama)
- Change notifications trigger reindexing and assistant reload

## Important Patterns

### Async Processing
Heavy use of `CompletableFuture` and background tasks prevents UI blocking. Always use:
```java
ApplicationManager.getApplication().executeOnPooledThread(() -> {
    // Background work
});
```

### Thread Safety
- Use `ReentrantLock` for critical sections
- `AtomicReference` for single-value updates
- IntelliJ's read/write actions for PSI access

### Service Access
```java
// Application service
OllamAssistSettings settings = ApplicationManager.getApplication()
    .getService(OllamAssistSettings.class);

// Project service
OllamaService service = project.getService(OllamaService.class);
```

### Publishing Events
```java
project.getMessageBus()
    .syncPublisher(NewUserMessageNotifier.NEW_MESSAGE_TOPIC)
    .newMessage(message);
```

## Dependencies

Key libraries (see `build.gradle.kts` for versions):
- **LangChain4j** (`langchain4j-core`, `langchain4j-ollama`, `langchain4j-easy-rag`) - AI service orchestration
- **Apache Lucene** (via LangChain4j) - Embedding storage
- **DJL** (`ai.djl:api`, `ai.djl.huggingface:tokenizers`) - Deep learning and tokenization
- **RSyntaxTextArea** - Code syntax highlighting
- **Jackson** - JSON serialization
- **IntelliJ Platform SDK 2024.3** - IDE framework

**Important:** Lucene and SLF4J are explicitly excluded from LangChain4j dependencies to avoid conflicts with IntelliJ's bundled versions.

## Testing

- Unit tests in `src/test/java/`
- Benchmark tests in `src/benchmark/java/`
- Use JUnit Jupiter (v5) for new tests
- Mockito for mocking
- AssertJ for fluent assertions

### Running a Single Test
```bash
./gradlew test --tests "ClassName.testMethodName"
```

## File Locations

- **Plugin sources:** `src/main/java/fr/baretto/ollamassist/`
- **Resources:** `src/main/resources/`
- **Plugin descriptor:** `src/main/resources/META-INF/plugin.xml`
- **RAG index:** `{project}/.ollamassist/database/knowledge_index/`
- **Settings:** `OllamAssist.xml` in IDE config directory

## Common Development Scenarios

### Adding a New Action
1. Create action class extending `AnAction` in `fr.baretto.ollamassist.actions`
2. Register in `plugin.xml` under `<actions>`
3. Implement `actionPerformed(AnActionEvent e)`
4. Get project: `e.getProject()`

### Modifying RAG Behavior
- **Indexing logic:** `DocumentIndexingPipeline` and `ProjectFileListener`
- **Retrieval logic:** `ContextRetriever` and `WorkspaceContextRetriever`
- **Embedding store:** `LuceneEmbeddingStore`
- **File filtering:** `ShouldBeIndexed` and `FilesUtil`

### Adding New Settings
1. Add field to `OllamAssistSettings`
2. Add UI component to `ConfigurationPanel`
3. Use `SettingsBindingHelper` for automatic binding
4. Publish notification if setting affects runtime behavior

### Customizing Chat Prompts
- System prompt in `Assistant` interface (created by `OllamaService`)
- User prompt transformation in `PromptPanel`
- Context augmentation in `ContextRetriever`

## Known Constraints

- Minimum IntelliJ Platform version: 2024.3 (build 243)
- Java version: 21
- Kotlin version: 2.1.20
- Requires running Ollama instance (checked at startup)
- RAG indexing respects file size limits from settings
- Completion requests debounced to 300ms to reduce API load