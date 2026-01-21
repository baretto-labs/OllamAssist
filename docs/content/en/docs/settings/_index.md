---
title: "Settings & Configuration"
description: "Configure OllamAssist to your needs"
weight: 40
---

## Accessing Settings

1. Open **Settings/Preferences** (Cmd+, / Ctrl+Alt+S)
2. Navigate to **OllamAssist**
3. Choose a tab to configure

**Screenshot:** [Settings Panel](../images/20-settings-panel.png)

## Ollama Tab

Configure connection and model settings.

### URLs Configuration

**Chat Ollama URL**
- Default: `http://localhost:11434`
- URL to your Ollama instance
- Used for chat requests
- Example: `https://ollama.example.com:11434`

**Completion Ollama URL**
- Default: Same as Chat URL
- Separate instance for code completion (optional)
- Allows optimization for faster responses

**Embedding Ollama URL**
- Default: Same as Chat URL
- Used for document embeddings
- Can use fallback to Ollama if DJL fails

### Model Selection

**Chat Model**
- Default: `llama2`
- Used for conversations in chat window
- Recommended: `llama2`, `mistral`, `neural-chat`
- Models: Click dropdown to fetch available models from Ollama

**Completion Model**
- Default: `mistral`
- Smaller models recommended for speed
- Used for autocomplete suggestions
- Keep it under 7B parameters for best performance

**Embedding Model**
- Default: `nomic-embed-text`
- Used for RAG document indexing
- Fallback: Ollama's nomic-embed-text if DJL fails
- Auto-detected from your Ollama instance

### Connection Settings

**Timeout Duration**
- Default: 300 seconds (5 minutes)
- Maximum wait time for responses
- Increase for slower systems
- Decrease to fail fast on connection issues

**Authentication** (Optional)
- Username: Leave empty if no auth required
- Password: For Basic Auth against Ollama
- Encoded as Base64 automatically

**Screenshot:** [Ollama Settings](../images/21-ollama-settings.png)

## RAG Tab

Control document indexing and retrieval.

### Document Indexing

**Indexed Document Sources**
- Default: `src,lib,config,docs`
- Comma-separated paths to index
- Examples:
  ```
  src,lib,test,docs,config
  ```
- Indexing respects `.gitignore`

**Maximum Indexed Documents**
- Default: 1000
- Limit for memory efficiency
- Increase for large projects
- Decrease for faster indexing

### Retrieval Settings

**Top-K Results**
- Default: 2
- Number of documents returned for context
- Higher = more context (slower)
- Lower = faster (less context)

**Minimum Similarity Score**
- Default: 0.85
- Filter out low-relevance documents
- Range: 0.0 to 1.0
- Lower = include more results

### Features

**Enable RAG**
- Toggle RAG integration on/off
- Disable to use chat without context

**Enable Web Search**
- Default: Disabled
- Augment results with web search
- Uses DuckDuckGo (privacy-focused)
- Adds 1-2 seconds to queries

**Clear Local Storage**
- Wipes all indexed documents
- Requires re-indexing
- Keep when upgrading

**Clean All Database**
- Full reset of RAG system
- Use when index is corrupted
- Restart plugin after cleaning

**Screenshot:** [RAG Settings](../images/22-rag-settings.png)

## Actions Tab

Configure AI tools and file handling.

### AI Tools (Function Calling)

**Enable AI Tools**
- Default: Disabled
- Allows AI to create files
- Requires compatible model
- ⚠️ Warning: May create unwanted files

**Recommended Models for Tools:**
- ✅ `qwen2.5:14b+` - Excellent tool support
- ✅ `gpt-oss` - Good tool calling
- ❌ `llama3.1` - Unreliable tools
- ❌ `llama3.2` - Unreliable tools

### File Creation Settings

**Auto-approve File Creation**
- Default: Disabled
- When enabled: Files are created without asking
- Disable for confirmation dialog

**Use Cases:**
- Enable only when you trust the model
- Use smaller, tested models first
- Test in safe projects first

**Screenshot:** [Actions Settings](../images/23-actions-settings.png)

## Prompts Tab

Customize AI system prompts.

### Chat System Prompt

Controls the AI's behavior in conversations.

**Default Prompt:**
```
You are a helpful programming assistant.
Provide clear, concise answers.
Use code examples when relevant.
Follow best practices and modern patterns.
```

**Customization Examples:**

*For Senior Developers:*
```
You are a senior software architect.
Assume the user has deep technical knowledge.
Provide detailed architectural discussions.
Focus on performance and scalability.
```

*For Learning:*
```
You are a patient coding tutor.
Explain concepts in simple terms.
Provide step-by-step examples.
Ask clarifying questions.
```

### Refactor Prompt

Controls refactoring suggestions.

**Available Variables:**
- `{{code}}` - The selected code
- `{{language}}` - Programming language

**Default Prompt:**
```
Refactor the following {{language}} code for better readability and performance:

{{code}}

Provide:
1. Refactored code
2. Explanation of changes
3. Performance impact (if any)
```

### Reset to Defaults

- Click "Reset Chat Prompt" to restore default chat prompt
- Click "Reset Refactor Prompt" to restore default refactor prompt
- Click "Reset All Prompts" to reset everything

**Screenshot:** [Prompt Configuration](../images/24-prompt-settings.png)

## UI Tab

Customize user interface appearance.

### Font Size Multiplier

**Slider: 50% - 200%**
- Default: 100%
- Adjust font sizes across entire UI
- Real-time preview in panel
- Changes apply immediately

**Use Cases:**
- HiDPI/2K displays: 125%-150%
- Large monitors: 100%-125%
- Vision accessibility: 150%-200%
- Compact setup: 75%-90%

**Affected Components:**
- Chat messages (headers and body)
- Code syntax highlighting blocks
- Settings panels
- Notifications
- All UI labels and buttons

**Screenshot:** [UI Font Settings](../images/25-ui-font-settings.png)

## Configuration Best Practices

### For Performance
```yaml
Completion Model: mistral (7B)
Embedding Model: nomic-embed-text
Top-K Results: 2
Timeout: 300 seconds
```

### For Accuracy
```yaml
Chat Model: llama2 (13B)
Top-K Results: 5
Similarity Score: 0.80
Enable Web Search: true
```

### For Privacy
```yaml
Ollama URL: Local instance only
Enable Web Search: false
Authentication: Enabled
```

### For Development
```yaml
Enable AI Tools: true (with trusted models)
Auto-approve: false
Timeout: 120 seconds
```

## Troubleshooting

### Settings Not Saved
- Click "Apply" button explicitly
- Restart IDE if needed
- Check file permissions in `.idea` folder

### Model Not Found in Dropdown
- Verify Ollama is running
- Check Ollama URL is correct
- Run `ollama list` to verify model downloaded
- Try refreshing: Close and reopen settings

### Slow Completions
- Increase timeout if using slow models
- Use smaller models (7B instead of 13B)
- Reduce top-K results (2 instead of 5)
- Check CPU/RAM usage

### RAG Not Working
- Verify indexing completed in prerequisite panel
- Check "Sources" includes relevant directories
- Try "Clean All Database" and re-index
- Check document count under limit

**Video:** [Settings Overview](../videos/settings-overview.mp4)

## Advanced Configuration

### Custom Ollama Instance

To use a remote or non-standard Ollama setup:

1. Update Ollama URLs in settings
2. Ensure network connectivity
3. Set authentication if needed
4. Test connection in prerequisite panel

### Multiple Versions

If running multiple Ollama instances:
- Chat: `http://ollama1:11434`
- Completion: `http://ollama2:11434`
- Embedding: `http://ollama3:11434`

This allows load distribution and model isolation.

### Export/Import Settings

Settings are stored in:
- **IntelliJ on macOS:** `~/Library/Application Support/IntelliJ IDEA/OllamAssist.xml`
- **IntelliJ on Linux:** `~/.config/JetBrains/IntelliJ IDEA/OllamAssist.xml`
- **IntelliJ on Windows:** `%APPDATA%\JetBrains\IntelliJ IDEA\OllamAssist.xml`

You can:
- Backup settings before updates
- Share configurations with team
- Version control custom prompts
