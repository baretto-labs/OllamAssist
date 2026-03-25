---
title: "Getting Started"
description: "Set up OllamAssist in 5 minutes"
weight: 20
---

## Installation & Setup (5 Minutes)

Follow these simple steps to get OllamAssist running in your IDE.

### Step 1: Download and Install Ollama

1. Visit [ollama.com/download](https://ollama.com/download)
2. Download Ollama for your operating system
3. Install and start the application
4. Open a terminal and run:

```bash
ollama run llama2
```

This downloads and starts the Llama 2 model (about 4GB). You can choose a different model if preferred.

**Terminal Output:**
```
Pulling layers [===>]
Setting up the ollama process...
Done!
```

{{< alert >}}
**Note:** Ollama requires 8GB+ RAM for most models. Check the [official requirements](https://ollama.com/download#requirements).
{{< /alert >}}

### Step 2: Install OllamAssist Plugin

1. Open your JetBrains IDE (IntelliJ IDEA, Rider, PyCharm, etc.)
2. Go to **Plugins > Marketplace**
3. Search for "OllamAssist"
4. Click **Install**
5. Click **Restart IDE** when prompted

**Screenshots:**
- [Plugin Installation Screenshot](../images/01-plugin-installation.png)
- [Plugin Marketplace Screenshot](../images/02-plugin-marketplace.png)

### Step 3: Configure the Plugin

1. After restart, go to **Settings/Preferences > OllamAssist**
2. Navigate to the **Ollama** tab
3. Verify the Ollama URL is set to `http://localhost:11434` (default)
4. Select your model from the dropdown (should auto-detect `llama2`)
5. Click **Apply**

**Screenshots:**
- [Settings - Ollama Tab](../images/03-settings-ollama.png)
- [Model Selection](../images/04-model-selection.png)

### Step 4: Start Using OllamAssist

1. Look for the **OllamAssist** tool window on the right side of your IDE
2. Type a question in the chat box:
   ```
   How would you refactor this function for better performance?
   ```
3. The AI will respond with context-aware suggestions

**Screenshots:**
- [Chat Window](../images/05-chat-window.png)
- [Response Example](../images/06-response-example.png)

## Quick Tips

### Using the Chat
- Type your question and press Enter
- Add files to context using the file selector panel
- Clear chat history: Click the refresh icon in the chat toolbar

### Code Selection Chat
1. Select code in the editor
2. Right-click > **OllamAssist > Add to Context**
3. Ask a question in the chat

**Screenshot:** [Code Selection Menu](../images/07-context-menu.png)

### Smart Autocomplete
- Position cursor in code
- Press **Shift+Space** to trigger AI completion
- Press **Enter** to accept or any other key to dismiss

**Screenshot:** [Autocomplete Suggestion](../images/08-autocomplete.png)

### Generate Commit Messages
1. Open the commit dialog (**Cmd+K** / **Ctrl+K**)
2. Click **Write Commit Message** button
3. OllamAssist generates a message following Conventional Commits

**Screenshot:** [Commit Message Generation](../images/09-commit-message.png)

## Troubleshooting

### "Ollama Connection Failed"
- Verify Ollama is running: Open [http://localhost:11434](http://localhost:11434) in your browser
- Check the URL in Settings matches your Ollama instance
- Ensure firewall isn't blocking port 11434

**Video:** [Troubleshooting Connection Issues](../videos/troubleshooting-connection.mp4)

### "Model Not Found"
- Verify the model is downloaded: Run `ollama list` in terminal
- Download a model: `ollama run llama2` (or your preferred model)
- Refresh the plugin: Restart IDE

### Slow Performance
- Check RAM usage: Ollama needs 8GB+ for most models
- Try a smaller model: `ollama run mistral` (7B model)
- Enable fewer RAG sources: Settings > RAG > Sources

**Video:** [Performance Optimization Tips](../videos/performance-tips.mp4)

## Next Steps

- Read about [Features](../features/)
- Explore [Settings & Configuration](../settings/)
- Check [FAQs](../faq/)
- Visit [GitHub Issues](https://github.com/baretto-labs/OllamAssist) for support

## Video Tutorials

- [Full Setup Walkthrough](../videos/setup-walkthrough.mp4) - 10 minutes
- [First Chat Interaction](../videos/first-chat.mp4) - 5 minutes
- [RAG Context Usage](../videos/rag-context.mp4) - 8 minutes
