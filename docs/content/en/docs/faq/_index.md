---
title: "Frequently Asked Questions"
description: "Answers to common questions"
weight: 50
---

## Installation & Setup

### Q: Which IDEs are supported?

**A:** OllamAssist works with all JetBrains IDEs:
- IntelliJ IDEA (Community & Ultimate)
- PyCharm
- WebStorm
- Rider
- CLion
- GoLand
- RubyMine
- PhpStorm
- And more...

Any IDE based on IntelliJ Platform 2024.3+ is supported.

### Q: Do I need to install Ollama on the same machine?

**A:** No, Ollama can run on a different machine. Configure the URL in settings:
```
http://192.168.1.100:11434
```

However, local installation is recommended for:
- Best privacy (data never leaves your network)
- Lowest latency
- No network dependencies

### Q: What are the system requirements?

**A:** Minimum requirements:
- **RAM:** 8GB (16GB recommended)
- **Storage:** 20GB (for models and cache)
- **Processor:** Modern CPU with at least 4 cores
- **Disk Speed:** SSD recommended

For smaller models (7B), 8GB RAM suffices.
For larger models (13B+), 16GB RAM recommended.

See [Ollama requirements](https://ollama.com/download#requirements) for details.

### Q: Can I use it without internet?

**A:** Yes! OllamAssist is designed for offline use:
- Local Ollama instance required
- No cloud API calls
- No data transmission
- Web search is optional and disabled by default

Perfect for:
- Secure/classified work
- Travel
- Network-restricted environments

### Q: How much disk space do models take?

**A:** Varies by model:
- **7B models** (e.g., mistral): 4-7GB
- **13B models** (e.g., llama2): 7-8GB
- **34B models** (e.g., llama2-uncensored): 20GB+

Download with `ollama run <model-name>`.

## Usage & Features

### Q: How does RAG work?

**A:** RAG (Retrieval-Augmented Generation) works in 4 steps:

1. **Indexing:** OllamAssist scans your project files and creates embeddings
2. **Storage:** Embeddings stored locally in `.ollamassist/database/`
3. **Retrieval:** When you ask a question, relevant files are retrieved
4. **Generation:** The AI generates answers using both the question and retrieved context

**Result:** The AI understands your actual codebase, not just general knowledge.

### Q: Why isn't my code being indexed?

**A:** Check these settings in Settings > RAG:

1. **Sources field** includes your code directories
   ```
   src,lib,tests,app,components
   ```

2. **Document count limit** not reached (default: 1000)

3. **File size limit** - Very large files may be skipped

4. **.gitignore** - Files in .gitignore are excluded intentionally

Check the prerequisite panel for indexing status.

### Q: Can I index files outside my project?

**A:** Currently, only project files are indexed. To include external libraries:
1. Copy them to your project
2. Add path to "Sources" in RAG settings
3. Trigger re-indexing

### Q: How do I clear the chat history?

**A:** Click the refresh/reset icon in the chat toolbar. This clears:
- All messages in current conversation
- Chat memory
- Context window

Does NOT clear indexed documents (use Settings > RAG for that).

### Q: Can I have multiple conversations?

**A:** Currently, OllamAssist maintains one active conversation per project. To switch contexts:
1. Clear chat history (refresh button)
2. Start a new conversation

Future versions may support multiple conversation tabs.

### Q: Why is autocomplete slow?

**A:** Possible causes and solutions:

1. **Model too large**
   - Change to smaller model (7B instead of 13B)
   - Example: Use `mistral` instead of `llama2`

2. **System overloaded**
   - Close other applications
   - Reduce RAG top-K results (Settings > RAG)
   - Increase debounce timeout

3. **Ollama not optimized**
   - Increase `OLLAMA_NUM_PARALLEL` environment variable
   - Run Ollama on GPU if available

4. **Network latency**
   - If using remote Ollama, check network latency
   - Ping your Ollama instance

### Q: How do I make Ollama run faster?

**A:** Performance optimization:

1. **Use GPU acceleration**
   ```bash
   export OLLAMA_GPU=1
   ollama run llama2
   ```

2. **Use smaller models**
   - 7B models much faster than 13B
   - Still good quality for most tasks

3. **Reduce context window**
   - Fewer RAG results = faster responses
   - Settings > RAG > Top-K: 2-3

4. **Upgrade hardware**
   - Upgrade to latest GPU
   - Increase RAM to 16GB+
   - Use NVMe SSD

5. **Configure Ollama**
   ```bash
   export OLLAMA_NUM_PARALLEL=4
   export OLLAMA_POLLING_DURATION=5s
   ```

## Models & Configuration

### Q: Which model should I choose?

**A:** Recommendations by use case:

| Use Case | Model | Size | Speed |
|----------|-------|------|-------|
| Chat/Discussion | llama2 | 13B | Medium |
| Quick Answers | mistral | 7B | Fast |
| Code Analysis | neural-chat | 7B | Fast |
| Advanced Reasoning | orca-mini | 7B | Medium |
| Best Quality | llama2-uncensored | 13B | Slow |

Start with `mistral` if unsure.

### Q: Can I use models from Hugging Face?

**A:** Only if they're available through Ollama. Currently, Ollama supports:
- [ollama.ai/library](https://ollama.ai/library) models
- Custom models via `modelfile`

To use Hugging Face models:
1. Convert to GGUF format
2. Create a `modelfile` for Ollama
3. Import into Ollama

### Q: Why doesn't my model support tool calling?

**A:** Tool calling (file creation) requires:
- Model architecture that supports functions
- Specific training for function definitions

**Supported models:**
- ✅ `qwen2.5:14b+`
- ✅ `gpt-oss`
- ❌ `llama3.1` - unreliable
- ❌ `llama3.2` - unreliable
- ❌ `mistral` - no function support

### Q: Can I run multiple models simultaneously?

**A:** Yes, configure separate URLs for each task:

```yaml
Chat: http://ollama1:11434
Completion: http://ollama2:11434
Embedding: http://ollama3:11434
```

Requires multiple Ollama instances or distributed setup.

### Q: What about model licensing?

**A:** Models have different licenses:
- **llama2:** Meta Community License
- **mistral:** Apache 2.0
- **neural-chat:** Apache 2.0
- **openchat:** Open source

Check model pages for licensing details before commercial use.

## Privacy & Security

### Q: Where is my data stored?

**A:** All data stays local:
- Chat history: `~/.idea/OllamAssist_chathistory`
- RAG embeddings: `~/.ollamassist/database/knowledge_index/`
- Settings: `~/.idea/OllamAssist.xml`

**No data sent to:**
- Cloud servers
- Baretto servers
- Third-party services (except optional web search)

### Q: Is my code secure?

**A:** Maximum security:

✅ **No external transmission** - Everything processes locally
✅ **No internet required** - Works completely offline
✅ **No tracking** - No analytics or logging to cloud
✅ **Open source** - Code publicly auditable ([GitHub](https://github.com/baretto-labs/OllamAssist))
✅ **MIT License** - Free to use and inspect

Perfect for:
- Classified/confidential projects
- HIPAA/GDPR regulated work
- Enterprise/government use

### Q: Can I disable web search?

**A:** Yes, it's disabled by default. To verify:
1. Settings > OllamAssist > RAG
2. "Enable Web Search" is unchecked

Web search is optional and only used when explicitly enabled.

### Q: What data does web search send?

**A:** When enabled:
- Your query is sent to DuckDuckGo
- DuckDuckGo doesn't track searches
- Results return to your IDE
- Results are not stored

See [DuckDuckGo privacy](https://duckduckgo.com/privacy) for details.

## Troubleshooting

### Q: "Ollama connection failed" error

**A:** Troubleshooting steps:

1. **Verify Ollama is running**
   ```bash
   curl http://localhost:11434/api/tags
   ```
   Should return JSON with model list.

2. **Check firewall**
   - Port 11434 must be open
   - Check Mac/Windows firewall settings

3. **Verify URL in settings**
   - Default: `http://localhost:11434`
   - Should match your Ollama instance

4. **Restart Ollama**
   ```bash
   # Kill any running Ollama
   killall ollama
   # Restart
   ollama serve
   ```

### Q: Responses are very slow

**A:** See "How do I make Ollama run faster?" above.

Common causes:
- Using 13B model (try 7B)
- No GPU acceleration
- Low system resources
- Network latency

### Q: Out of memory errors

**A:** Solutions:

1. **Reduce model size**
   - 7B instead of 13B
   - `mistral` instead of `llama2`

2. **Increase swap space**
   ```bash
   # Linux
   sudo swapon -s
   ```

3. **Close other applications**

4. **Upgrade RAM**
   - Minimum 8GB
   - Recommended 16GB

### Q: RAG documents not updated

**A:** Trigger re-indexing:

1. Settings > RAG
2. Click "Clear Local Storage"
3. Restart IDE
4. Indexing starts automatically

Or force re-index:
```bash
# Delete index folder
rm -rf ~/.ollamassist/database/knowledge_index/
# Restart IDE
```

### Q: Plugin crashes on startup

**A:** Try these steps:

1. **Clear plugin cache**
   ```bash
   rm -rf ~/.idea/system/cache
   ```

2. **Reinstall plugin**
   - Uninstall from Plugins menu
   - Restart IDE
   - Reinstall from Marketplace

3. **Clear RAG database**
   - Settings > RAG > "Clean All Database"
   - Restart IDE

4. **Check IDE version**
   - OllamAssist requires 2024.3+
   - Update IDE if older

## Contributing & Support

### Q: How do I report a bug?

**A:** Create an issue on GitHub:
1. Go to [GitHub Issues](https://github.com/baretto-labs/OllamAssist/issues)
2. Click "New Issue"
3. Describe the problem with screenshots
4. Include IDE version and OllamAssist version

### Q: How do I request a feature?

**A:** Use GitHub Discussions:
1. Go to [GitHub Discussions](https://github.com/baretto-labs/OllamAssist/discussions)
2. Click "New Discussion"
3. Tag as "Ideas" category
4. Describe the feature and use case

### Q: Can I contribute?

**A:** Yes! Contributions welcome:
- **Code:** Submit pull requests
- **Documentation:** Fix and improve docs
- **Translations:** Help translate to other languages
- **Testing:** Report bugs and edge cases

See [CONTRIBUTING.md](https://github.com/baretto-labs/OllamAssist/blob/main/CONTRIBUTING.md) on GitHub.

### Q: Is there a community?

**A:** Join us:
- **GitHub Discussions:** Ask questions, share ideas
- **GitHub Issues:** Report bugs and suggestions
- **Discord** (coming soon)

## More Help

Still have questions?

- 📖 [Read the full documentation](/docs/)
- 🐛 [Check open issues](https://github.com/baretto-labs/OllamAssist/issues)
- 💬 [Start a discussion](https://github.com/baretto-labs/OllamAssist/discussions)
- 📧 Email: contact@baretto.fr
