# Video Tutorials Directory

Place all video tutorials here. The documentation references them using paths like:
```markdown
[Video Title](../videos/setup-walkthrough.mp4)
```

## Recommended Videos to Create

### Essential Tutorials
1. `setup-walkthrough.mp4` - Complete 10-minute setup guide
   - Download Ollama
   - Install plugin
   - Configure settings
   - First chat interaction

2. `first-chat.mp4` - 5-minute first chat interaction
   - Ask a question
   - Understand responses
   - Use context
   - Clear history

3. `rag-context.mp4` - 8-minute RAG context explanation
   - How indexing works
   - Selecting file context
   - Retrieving documents
   - AI understanding codebase

### Feature Videos
4. `chat-demo.mp4` - Chat feature demonstration
5. `autocomplete-demo.mp4` - Code completion walkthrough
6. `commit-demo.mp4` - Commit message generation
7. `refactoring-examples.mp4` - Refactoring suggestions demo

### Settings Videos
8. `settings-overview.mp4` - Settings panel tour
9. `rag-explanation.mp4` - Detailed RAG explanation

### Troubleshooting Videos
10. `troubleshooting-connection.mp4` - Fix connection issues
11. `performance-tips.mp4` - Performance optimization

## Video Guidelines

- **Duration:** 3-10 minutes per video
- **Format:** MP4 (h.264 codec) or WebM
- **Resolution:** 1280x720 (720p) or 1920x1080 (1080p)
- **Bitrate:** 1-5 Mbps (balance quality/size)
- **Frame rate:** 30fps
- **Audio:** Clear narration with background music optional
- **File size:** Keep under 50MB per video

## How to Create Videos

### Recommended Tools
- **macOS:** ScreenFlow, QuickTime, OBS Studio
- **Windows:** Camtasia, OBS Studio, ShareX
- **Linux:** OBS Studio, SimpleScreenRecorder
- **Cross-platform:** OBS Studio (free, open source)

### Recording Tips
1. **Clean screen:** Hide secrets, focus on relevant areas
2. **Good audio:** Use microphone with noise cancellation
3. **Consistent pacing:** Not too fast, not too slow
4. **Clear voice:** Speak clearly and at good volume
5. **Show cursor:** Make it visible for following along

### Editing & Compression
```bash
# Using ffmpeg to compress
ffmpeg -i input.mp4 -vcodec h264 -b:v 2500k -b:a 128k output.mp4

# Create thumbnail from first frame
ffmpeg -i input.mp4 -ss 00:00:01 -vframes 1 thumbnail.jpg
```

## Adding Videos to Documentation

Reference videos like this:
```markdown
**Video:** [Full Setup Walkthrough](../videos/setup-walkthrough.mp4) - 10 minutes
```

## Video Content Ideas

### Setup Walkthrough (10 min)
```
1. Show Ollama download (1 min)
2. Install and run Ollama (2 min)
3. Download a model (1 min)
4. Install plugin (1 min)
5. Configure settings (2 min)
6. First chat interaction (3 min)
```

### Chat Demo (5 min)
```
1. Open OllamAssist panel (30 sec)
2. Ask a question (1 min)
3. Wait for response (2 min)
4. Show streaming response (1 min)
5. Start another conversation (30 sec)
```

### RAG Explanation (8 min)
```
1. Show file selection (1 min)
2. Trigger indexing (1 min)
3. Ask question without context (2 min)
4. Show RAG results (1 min)
5. Explain how it works (2 min)
6. Demo with different contexts (1 min)
```

## Hosting & Embedding

Videos are embedded as:
```markdown
[Video Name](path/to/video.mp4)
```

Hugo with Docsy supports:
- Direct MP4 links
- YouTube embeds
- Vimeo embeds
- HTML5 video tags

For better UX, consider:
- **YouTube:** Easier distribution, doesn't count against bandwidth
- **Vimeo:** Better privacy, professional hosting
- **Direct hosting:** Best for private/enterprise docs

## Example Embedded Video

```markdown
{{< youtube "dQw4w9WgXcQ" >}}
```

Or HTML5:
```html
<video width="100%" controls>
  <source src="../videos/setup-walkthrough.mp4" type="video/mp4">
  Your browser does not support HTML5 video.
</video>
```
