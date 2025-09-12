# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Communication Language

**IMPORTANT**: Always communicate with the user in French, but write and comment code in English.

## Project Overview

OllamAssist is a JetBrains IntelliJ IDEA plugin that integrates with Ollama to provide AI-powered development assistance. It's written in Java with a Gradle Kotlin DSL build system and leverages LangChain4J for AI model integration.

## Build System and Commands

### Prerequisites
- Java 21 (JDK 21)
- Ollama installed and running locally

### Core Commands

**Build the plugin:**
```bash
./gradlew build
```

**Run tests:**
```bash
./gradlew test
```

**Run benchmarks:**
```bash
./gradlew benchmark
```

**Build plugin distribution:**
```bash
./gradlew buildPlugin
```

**Clean build artifacts:**
```bash
./gradlew clean
```

**Run all checks (includes tests, benchmarks):**
```bash
./gradlew check
```

**SonarCloud analysis (requires SONAR_TOKEN):**
```bash
./gradlew sonar
```

### Testing Strategy
- Unit tests: Located in `src/test/java/`
- Benchmark tests: Located in `src/benchmark/java/`
- JUnit Jupiter engine for unit tests
- AssertJ for assertions
- Mockito for mocking

## Architecture Overview

### Core Components

**Plugin Entry Points:**
- `OllamAssistStartup` - Plugin initialization and startup activities
- `OllamaWindowFactory` - Creates the main tool window UI

**AI/Chat System:**
- `OllamaService` - Handles communication with Ollama backend
- `Assistant` - Main AI conversation coordinator
- `LightModelAssistant` - Lightweight model for completions

**RAG (Retrieval-Augmented Generation):**
- `LuceneEmbeddingStore` - Vector storage using Apache Lucene
- `DocumentIndexingPipeline` - Processes workspace files for indexing
- `WorkspaceContextRetriever` - Retrieves relevant context from indexed files
- `FilesUtil` - File processing utilities for RAG

**Code Completion:**
- `InlineCompletionAction` - Triggered via Shift+Space
- `SuggestionManager` - Manages completion suggestions
- `InlayRenderer` - Renders inline suggestions in editor

**Git Integration:**
- `CommitMessageGenerator` - AI-generated commit messages
- `DiffGenerator` - Generates diffs for context
- `MyersDiff` - Myers diff algorithm implementation

**Refactoring:**
- `RefactorAction` - Code refactoring suggestions
- `ApplyRefactoringAction` - Applies suggested refactorings
- `RefactoringInlayRenderer` - Displays refactoring suggestions

**UI Components:**
- `MessagesPanel` - Chat conversation display
- `PresentationPanel` - Main plugin UI
- `PromptPanel` - User input area
- `SyntaxHighlighterPanel` - Code syntax highlighting

### Key Services

**Application Services:**
- `PrerequisiteService` - Checks system prerequisites
- `OllamAssistSettings` - Plugin settings management
- `IndexRegistry` - Manages document indices

**Project Services:**
- `DocumentIngestFactory` - Creates document processors
- `SelectionGutterIcon` - Code selection UI
- `OverlayPromptPanelFactory` - Creates overlay prompts

### Configuration and Settings

**Settings Panel:**
- Accessible via File → Settings → OllamAssist
- Configure Ollama model selection
- RAG and indexing settings
- Model-specific Ollama instance configuration

**Plugin Actions:**
- **Shift+Space**: Trigger AI autocompletion
- **Project View → OllamAssist → Add to Context**: Add files to chat context
- **Editor → OllamAssist → Refactor**: Get refactoring suggestions
- **VCS → Write Commit Message**: Generate commit messages

### Dependencies and Libraries

**Core AI Libraries:**
- LangChain4J (`dev.langchain4j`) - AI model integration
- LangChain4J Easy RAG - Document retrieval and processing

**Search and Indexing:**
- Apache Lucene - Vector storage and search
- DJL (Deep Java Library) - Tokenization

**IntelliJ Platform:**
- IntelliJ IDEA Community 2024.3
- Git4Idea plugin dependency

**Utilities:**
- RSyntaxTextArea - Syntax highlighting
- Jackson - JSON processing
- Jsoup - HTML parsing
- Plexus Utils - File utilities

### Project Structure

```
src/main/java/fr/baretto/ollamassist/
├── chat/                    # Chat and conversation logic
│   ├── rag/                # RAG implementation
│   ├── ui/                 # Chat UI components
│   └── service/            # Chat services
├── completion/             # Code completion features
├── component/              # Reusable UI components  
├── git/                    # Git integration
├── setting/                # Settings and configuration
├── prerequiste/            # System requirements checking
├── events/                 # Event system and notifications
└── actions/refactor/       # Refactoring functionality
```

### File Indexing and RAG

The plugin indexes workspace files for context-aware responses:

- **Indexed File Types**: Determined by `ShouldBeIndexed` filter
- **Storage**: Lucene-based vector store
- **Retrieval**: Semantic search for relevant code context
- **Web Search**: Optional DuckDuckGo integration for external context

### Development Notes

- Plugin targets IntelliJ Platform 243+ (2024.3+)
- Uses Kotlin Gradle DSL for build configuration  
- Java 21 source and target compatibility
- Lombok for boilerplate reduction
- Configuration cache and build cache enabled for performance