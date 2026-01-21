---
title: "Features"
description: "Explore OllamAssist capabilities"
weight: 30
---

## Core Features

### 1. In-IDE Chat with Ollama

Chat directly with AI models without leaving your IDE. Perfect for:
- Understanding code logic
- Getting refactoring suggestions
- Discussing architecture decisions
- Learning new concepts

**Key Capabilities:**
- Real-time streaming responses
- Chat history management
- Conversation context preservation
- Support for multiple models

**Screenshot:** [Chat Interface](../images/10-chat-interface.png)
**Video:** [Chat Demo](../videos/chat-demo.mp4)

### 2. RAG (Retrieval-Augmented Generation)

Your codebase becomes context for AI responses.

**How it works:**
1. OllamAssist indexes your project files
2. When you ask a question, relevant files are automatically retrieved
3. The AI uses this context to provide better answers
4. Responses are tailored to your actual codebase

**Benefits:**
- Context-aware responses
- References to your actual code
- Better architectural understanding
- Faster problem-solving

**Configuration:**
- Customize which files are indexed
- Set document count limits
- Choose embedding models
- Control search relevance threshold

**Screenshots:**
- [RAG Settings](../images/11-rag-settings.png)
- [Context File Selection](../images/12-context-files.png)

**Video:** [RAG Explanation](../videos/rag-explanation.mp4)

### 3. Smart Code Completion

Intelligent autocomplete powered by Ollama.

**How to use:**
1. Position cursor in code
2. Press **Shift+Space**
3. Review suggestion
4. Press **Enter** to accept or any other key to dismiss

**Features:**
- Context-aware suggestions
- Multi-line code completion
- Language-specific logic
- Inline rendering

**Supported Languages:**
- Java, Python, JavaScript, TypeScript
- C#, Go, Rust, C++
- SQL, HTML, CSS, and more

**Video:** [Autocomplete Demo](../videos/autocomplete-demo.mp4)

### 4. Commit Message Generation

Generate meaningful commit messages automatically.

**Workflow:**
1. Stage your changes (git)
2. Open commit dialog (**Cmd+K** / **Ctrl+K**)
3. Click "Write Commit Message"
4. OllamAssist analyzes your diff
5. Generates a conventional commit message

**Features:**
- Conventional Commits format
- Respects file selection in commit dialog
- Scans actual code changes
- Customizable via prompts

**Example:**
```
feat(chat): Add real-time message streaming

- Implement TokenStream integration
- Add loading indicators for long responses
- Support for cancellation mid-response
- Improves user experience for slow models
```

**Screenshot:** [Commit Message Dialog](../images/13-commit-message.png)
**Video:** [Commit Generation Demo](../videos/commit-demo.mp4)

### 5. Web Search Integration

Optional web search for additional context.

**Configuration:**
- Enable/disable in Settings > RAG
- Uses DuckDuckGo for privacy
- Augments workspace context with web results
- 2-second search timeout

**Use Cases:**
- Finding library documentation
- Searching for best practices
- Looking up API references
- Getting latest information

**Screenshot:** [Web Search Results](../images/14-web-search.png)

### 6. AI-Powered Refactoring

Ask OllamAssist to refactor code.

**How to use:**
1. Select code in editor
2. Right-click > **OllamAssist > Refactor**
3. Review suggestions
4. Apply changes manually or with AI assistance

**Examples:**
- Extract methods
- Simplify complex logic
- Improve naming
- Optimize performance
- Update to modern patterns

**Screenshot:** [Refactoring Interface](../images/15-refactoring.png)
**Video:** [Refactoring Examples](../videos/refactoring-examples.mp4)

## Advanced Features

### File Context Management

Control which files are available as context.

**Features:**
- Add files from context panel
- Automatic current file inclusion
- Remove files from context
- View file statistics
- Search indexed documents

**Screenshot:** [File Context Panel](../images/16-file-context.png)

### Customizable System Prompts

Define how the AI behaves.

**Prompts:**
- **Chat System Prompt** - Controls AI personality in chat
- **Refactor Prompt** - Customizes refactoring suggestions

**Example Prompt:**
```
You are a senior software architect with 20 years of experience.
Provide detailed explanations with code examples.
Always consider performance, maintainability, and best practices.
```

**Screenshot:** [Prompt Configuration](../images/17-custom-prompts.png)

### Multiple Model Configuration

Use different models for different tasks.

**Setup:**
- **Chat Model** - For conversations (e.g., llama2)
- **Completion Model** - For code completion (e.g., mistral)
- **Embedding Model** - For RAG indexing (e.g., nomic-embed-text)

**Benefits:**
- Optimize for different use cases
- Use smaller models for faster responses
- Fallback to Ollama if embeddings fail
- Independent Ollama instances supported

**Screenshot:** [Model Configuration](../images/18-model-config.png)

### UI Font Customization

Adjust font sizes for your display.

**Features:**
- Global font size slider (50% - 200%)
- Real-time preview
- HiDPI/2K display support
- Respects IDE font settings
- Semantic font levels (title, normal, small, code)

**Use Cases:**
- High-resolution displays
- Vision accessibility
- Personal comfort preferences
- Workspace setup

**Screenshot:** [Font Settings](../images/19-font-settings.png)

## Integration Features

### IDE Integration
- Syntax highlighting in code blocks
- Code insertion directly to editor
- Selection-based context
- Gutter icons for quick access

### Git Integration
- Commit message generation
- Current git diff analysis
- Conventional Commits format
- Partial file commit support

### Project Features
- Automatic file indexing
- .gitignore respects
- Incremental updates
- Custom file filters

## Performance Features

### Optimization
- Document batch processing
- Async indexing pipeline
- Token-aware responses
- Debounced completions (300ms)
- LRU caching for suggestions

### Resource Management
- Configurable document limits
- File size filtering
- Memory-efficient RAG storage
- Optional web search (disabled by default)

## Accessibility

### Universal Support
- Works offline completely
- HiDPI display support
- Font size customization
- High contrast themes
- Keyboard shortcuts

### Privacy
- No data sent to cloud
- No account required
- No tracking or analytics
- Open source (MIT licensed)

## What's Coming

Based on community requests:
- [ ] Advanced code refactoring tools
- [ ] Multi-file analysis
- [ ] Custom function definitions
- [ ] Integration marketplace
- [ ] Collaborative features

**Help shape the future:** [GitHub Discussions](https://github.com/baretto-labs/OllamAssist/discussions)
