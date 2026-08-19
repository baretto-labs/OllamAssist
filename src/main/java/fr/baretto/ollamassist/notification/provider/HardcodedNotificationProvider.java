package fr.baretto.ollamassist.notification.provider;

import fr.baretto.ollamassist.notification.core.Notification;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Provides hardcoded notifications bundled with the plugin.
 * Future: Can be extended with RemoteNotificationProvider for server-fetched notifications.
 */
public final class HardcodedNotificationProvider implements NotificationProvider {

    @Override
    public List<Notification> getAllNotifications() {
        return List.of(
                // v1.14.0 - Release notifications can be muted
                Notification.builder()
                        .id("v1.14.0-release")
                        .version("1.14.0")
                        .type(Notification.NotificationType.FEATURE)
                        .priority(Notification.Priority.MEDIUM)
                        .title("OllamAssist 1.14.0 — Release Notifications, Under Control")
                        .message("""
                                <html>
                                <body style='font-family: sans-serif; padding: 10px;'>
                                <h3>This is the last one you will see, if you want it to be</h3>

                                <p>This panel used to be unavoidable, and the balloon announcing it came back at \
                                every IDE start when it was left untouched. Both are fixed.</p>

                                <h4>What's new</h4>
                                <ul>
                                  <li><b>Don't show again</b> — the checkbox below this panel mutes release notifications for good</li>
                                  <li><b>Straight from the balloon</b> — the same choice is available without opening this dialog</li>
                                  <li><b>Reversible</b> — turn them back on in <b>Settings &rarr; OllamAssist &rarr; UI &rarr; Notifications</b></li>
                                </ul>

                                <h4>What is fixed</h4>
                                <ul>
                                  <li>The update balloon was only acknowledged when it expired — a balloon left in the \
                                  Notifications tool window never expires, so it reappeared at every start</li>
                                  <li>Changing a setting in the <b>UI</b> tab alone is now applied instead of being silently discarded</li>
                                </ul>
                                </body>
                                </html>
                                """)
                        .dismissible(true)
                        .createdAt(LocalDateTime.of(2026, 8, 19, 0, 0))
                        .build(),

                // v1.13.1 - Custom prompts are persisted
                Notification.builder()
                        .id("v1.13.1-release")
                        .version("1.13.1")
                        .type(Notification.NotificationType.INFO)
                        .priority(Notification.Priority.MEDIUM)
                        .title("OllamAssist 1.13.1 — Custom Prompts Are Persisted")
                        .message("""
                                <html>
                                <body style='font-family: sans-serif; padding: 10px;'>
                                <h3>Your customised prompts now survive a restart</h3>

                                <p>A customised <b>Chat System Prompt</b> reverted to the default every time the \
                                IDE was restarted. The prompt was never written to disk, so it was lost as soon as \
                                the IDE closed.</p>

                                <h4>What is fixed</h4>
                                <ul>
                                  <li><b>Chat System Prompt</b> and <b>Refactor User Prompt</b> are now saved and reloaded correctly</li>
                                  <li><b>Actions settings</b> — <i>auto-approve file creation</i> and <i>tools enabled</i> were silently affected by the same defect and are persisted as well</li>
                                  <li><b>Default chat prompt</b> — emoji were removed, as the settings format cannot store them and dropped them without warning</li>
                                </ul>

                                <h4>One last time</h4>
                                <p>Prompts customised before this release cannot be recovered — they were never \
                                stored. Re-enter yours once in <b>Settings → OllamAssist → Prompts</b> and it will \
                                stay from now on.</p>
                                </body>
                                </html>
                                """)
                        .dismissible(true)
                        .createdAt(LocalDateTime.of(2026, 8, 17, 0, 0))
                        .build(),

                // v1.13.0 - API Key / Bearer authentication
                Notification.builder()
                        .id("v1.13.0-release")
                        .version("1.13.0")
                        .type(Notification.NotificationType.FEATURE)
                        .priority(Notification.Priority.MEDIUM)
                        .title("OllamAssist 1.13.0 — API Key / Bearer Authentication")
                        .message("""
                                <html>
                                <body style='font-family: sans-serif; padding: 10px;'>
                                <h3>API Key / Bearer Authentication</h3>

                                <p>You can now connect OllamAssist to an Ollama endpoint that sits behind an \
                                authenticated proxy (e.g. <b>OpenWebUI</b>) using a <b>Bearer token / API key</b>, \
                                in addition to the existing Basic Auth.</p>

                                <h4>What's new</h4>
                                <ul>
                                  <li><b>Authentication mode selector</b> — choose <i>None</i>, <i>Basic Auth</i> or <i>API Key (Bearer)</i></li>
                                  <li><b>Applied everywhere</b> — the Authorization header now travels on every Ollama request, including the startup health checks</li>
                                  <li><b>Backward compatible</b> — existing Basic Auth setups keep working with no reconfiguration</li>
                                </ul>

                                <h4>How to access</h4>
                                <p><b>Settings → OllamAssist → Ollama → Authentication</b></p>
                                </body>
                                </html>
                                """)
                        .dismissible(true)
                        .createdAt(LocalDateTime.of(2026, 6, 17, 0, 0))
                        .build(),

                // v1.12.0 - Agent Mode (Alpha)
                Notification.builder()
                        .id("v1.12.0-release")
                        .version("1.12.0")
                        .type(Notification.NotificationType.FEATURE)
                        .priority(Notification.Priority.HIGH)
                        .title("OllamAssist 1.12.0 — Agent Mode (Alpha)")
                        .message("""
                                <html>
                                <body style='font-family: sans-serif; padding: 10px;'>
                                <h3>Agent Mode is here — Alpha</h3>

                                <p>Give the agent a goal in plain English. It will explore your project, \
                                generate a step-by-step plan and ask for your approval before touching any file.</p>

                                <h4>What it can do</h4>
                                <ul>
                                  <li><b>Create, edit, delete and append</b> files</li>
                                  <li><b>Discover context</b> — searches your codebase before planning</li>
                                  <li><b>One approval</b> for the full plan — review everything before execution starts</li>
                                  <li><b>Step progress</b> — counter shown next to the Stop button during execution</li>
                                  <li><b>Execution history</b> — review past runs via the clock icon</li>
                                </ul>

                                <h4>Not yet supported</h4>
                                <ul>
                                  <li>Running build commands or tests (coming in a future release)</li>
                                </ul>

                                <h4>Model recommendation</h4>
                                <p>Agent mode requires a model with reliable structured output (JSON).<br>
                                <b>Recommended:</b> <code>qwen2.5:14b+</code>, <code>mistral-nemo</code>, <code>deepseek-coder:33b</code><br>
                                <i>Small models (llama3.1:8b, llama3.2) may produce invalid plans.</i></p>

                                <hr style='margin: 12px 0; border: none; border-top: 1px solid #ccc;'>

                                <p style='font-size: 0.9em; color: #888;'>
                                Alpha release — always read the full plan before approving. \
                                The Stop button cancels execution between steps.
                                </p>
                                </body>
                                </html>
                                """)
                        .dismissible(true)
                        .createdAt(LocalDateTime.of(2026, 5, 12, 0, 0))
                        .build(),

                // v1.11.0 - Conversation Management + RAG Sources + Hybrid Retrieval
                Notification.builder()
                        .id("v1.11.0-release")
                        .version("1.11.0")
                        .type(Notification.NotificationType.FEATURE)
                        .priority(Notification.Priority.HIGH)
                        .title("What's New in OllamAssist 1.11.0")
                        .message("""
                                <html>
                                <body style='font-family: sans-serif; padding: 10px;'>
                                <h3>💬 Conversation Management</h3>

                                <p>Your chat history is now <b>persisted across IDE sessions</b>. No more starting from scratch every time you reopen IntelliJ!</p>

                                <ul>
                                  <li><b>Persistent conversations</b> - Saved per project under <code>.ollamassist/conversations/</code></li>
                                  <li><b>Multiple conversations</b> - Create, switch, and delete conversations from the chat panel</li>
                                  <li><b>Auto-generated titles</b> - Derived automatically from your first message</li>
                                  <li><b>Resume anywhere</b> - Pick up exactly where you left off after restarting the IDE</li>
                                </ul>

                                <hr style='margin: 12px 0; border: none; border-top: 1px solid #ccc;'>

                                <h3>🔍 RAG Source Transparency</h3>

                                <p>Each AI response now shows the <b>code chunks it was based on</b>.</p>

                                <ul>
                                  <li><b>Sources panel</b> - Collapsed "N sources" indicator below each response</li>
                                  <li><b>Clickable links</b> - Click any source to navigate directly to the file in the editor</li>
                                  <li><b>Native icons</b> - File type icons match IntelliJ's own icons for each extension</li>
                                </ul>

                                <hr style='margin: 12px 0; border: none; border-top: 1px solid #ccc;'>

                                <h3>⚡ Smarter Code Retrieval</h3>

                                <ul>
                                  <li><b>Hybrid search</b> - Combines semantic (KNN) and keyword (BM25) search via Reciprocal Rank Fusion</li>
                                  <li><b>PSI-aware chunking</b> - Java files are split by class and method for higher precision</li>
                                  <li><b>Cross-IDE support</b> - Graceful fallback in PyCharm, CLion, and other JetBrains products</li>
                                  <li><b>Index versioning</b> - Stale indexes are detected and rebuilt automatically on upgrade</li>
                                </ul>

                                <p style='font-size: 0.9em; color: #666;'>
                                💡 <i>Tip: Expand the sources panel after a response to understand exactly what context the AI used!</i>
                                </p>
                                </body>
                                </html>
                                """)
                        .dismissible(true)
                        .createdAt(LocalDateTime.of(2026, 3, 26, 0, 0))
                        .build(),

                // v1.10.4 - UI Font Size Customization
                Notification.builder()
                        .id("v1.10.4-release")
                        .version("1.10.4")
                        .type(Notification.NotificationType.FEATURE)
                        .priority(Notification.Priority.MEDIUM)
                        .title("OllamAssist 1.10.4 - Better UI Font Sizing")
                        .message("""
                                <html>
                                <body style='font-family: sans-serif; padding: 10px;'>
                                <h3>🔤 Customizable UI Font Size</h3>

                                <p>Finally! You can now <b>adjust font sizes across the entire UI</b> to match your preferences and display resolution.</p>

                                <h4>✨ What's New:</h4>
                                <ul>
                                  <li><b>Font Size Slider</b> - Adjust from 50% to 200% in the UI settings</li>
                                  <li><b>Real-time Preview</b> - See changes instantly in the settings panel</li>
                                  <li><b>IDE Font Respect</b> - All UI components respect your IDE's default font settings</li>
                                  <li><b>HiDPI Support</b> - Better scaling on high-resolution displays (2K, 4K)</li>
                                </ul>

                                <h4>🎯 Perfect For:</h4>
                                <ul>
                                  <li>✅ Users on high-resolution displays (2K, 4K monitors)</li>
                                  <li>✅ Vision-impaired developers who need larger fonts</li>
                                  <li>✅ Personalizing the IDE to your comfort level</li>
                                </ul>

                                <h4>🔧 How to Access:</h4>
                                <p><b>Settings → OllamAssist → UI → Font Size Multiplier</b></p>

                                <hr style='margin: 15px 0; border: none; border-top: 1px solid #ccc;'>

                                <h4>📊 Affected Components:</h4>
                                <ul>
                                  <li>Chat messages (headers and body text)</li>
                                  <li>Code syntax highlighting blocks</li>
                                  <li>Settings panels and notifications</li>
                                  <li>All UI labels and buttons</li>
                                </ul>

                                <p style='font-size: 0.9em; color: #666;'>
                                💡 <i>Tip: Try increasing to 125% on a 2K display for better readability!</i>
                                </p>
                                </body>
                                </html>
                                """)
                        .dismissible(true)
                        .createdAt(LocalDateTime.of(2026, 1, 21, 0, 0))
                        .build(),

                // v1.10.2 - IndexWriterConfig Fix
                Notification.builder()
                        .id("v1.10.2-release")
                        .version("1.10.2")
                        .type(Notification.NotificationType.INFO)
                        .priority(Notification.Priority.MEDIUM)
                        .title("OllamAssist 1.10.2 - Stability Fix")
                        .message("""
                                <html>
                                <body style='font-family: sans-serif; padding: 10px;'>
                                <h3>🔧 Stability Improvement</h3>

                                <p>This release addresses a critical Lucene configuration issue that could cause plugin startup failures.</p>

                                <h4>🐛 Fixed: PluginException on Startup</h4>
                                <ul>
                                  <li><b>Problem:</b> "do not share IndexWriterConfig instances across IndexWriters" exception</li>
                                  <li><b>Cause:</b> IndexWriterConfig reuse during index recovery process</li>
                                  <li><b>Fix:</b> Each IndexWriter now receives its own dedicated configuration instance</li>
                                  <li>✅ More reliable plugin initialization</li>
                                </ul>

                                <p style='font-size: 0.9em; color: #666;'>
                                💡 <i>This fix improves the reliability of the automatic index recovery mechanism introduced in v1.10.1.</i>
                                </p>
                                </body>
                                </html>
                                """)
                        .dismissible(true)
                        .createdAt(LocalDateTime.of(2025, 1, 11, 0, 0))
                        .build(),

                // v1.10.1 - Critical Bugfixes
                Notification.builder()
                        .id("v1.10.1-release")
                        .version("1.10.1")
                        .type(Notification.NotificationType.INFO)
                        .priority(Notification.Priority.HIGH)
                        .title("OllamAssist 1.10.1 - Critical Bugfixes")
                        .message("""
                                <html>
                                <body style='font-family: sans-serif; padding: 10px;'>
                                <h3>🔧 Critical Fixes</h3>

                                <p>This release fixes two major issues affecting plugin stability:</p>

                                <h4>🐛 Issue #146: Startup Freeze After Upgrade</h4>
                                <ul>
                                  <li><b>Problem:</b> Plugin would freeze at "OllamAssist running..." after upgrading from v1.9.0</li>
                                  <li><b>Cause:</b> Incompatible Lucene index format from previous version</li>
                                  <li><b>Fix:</b> Automatic detection and recreation of incompatible indexes</li>
                                  <li>✅ Seamless upgrades between versions</li>
                                </ul>

                                <h4>🐛 Issue #145: Windows Native Library Error</h4>
                                <ul>
                                  <li><b>Problem:</b> Plugin crashed on Windows with UnsatisfiedLinkError</li>
                                  <li><b>Cause:</b> Missing DJL native libraries for local embedding model</li>
                                  <li><b>Fix:</b> Automatic fallback to Ollama's nomic-embed-text model</li>
                                  <li>✅ Plugin now works on all platforms</li>
                                </ul>

                                <hr style='margin: 15px 0; border: none; border-top: 1px solid #ccc;'>

                                <h4>✨ Enhanced Prerequisite Panel</h4>
                                <p>The prerequisite check now shows detailed status for embedding models with automatic fallback detection and clear installation instructions.</p>

                                <p style='font-size: 0.9em; color: #666;'>
                                💡 <i>If you see a warning about using Ollama fallback, the plugin will work normally with slightly slower performance.</i>
                                </p>
                                </body>
                                </html>
                                """)
                        .dismissible(true)
                        .createdAt(LocalDateTime.of(2025, 1, 6, 0, 0))
                        .build(),

                // v1.10.0 - Customizable Prompts & Async Loading
                Notification.builder()
                        .id("v1.10.0-release")
                        .version("1.10.0")
                        .type(Notification.NotificationType.FEATURE)
                        .priority(Notification.Priority.HIGH)
                        .title("What's New in OllamAssist 1.10.0")
                        .message("""
                                <html>
                                <body style='font-family: sans-serif; padding: 10px;'>
                                <h3>✨ Customizable AI System Prompts</h3>

                                <p>Take full control of how OllamAssist responds! You can now <b>customize the AI system prompts</b> to match your coding style and preferences.</p>

                                <h4>🎯 What You Can Do:</h4>
                                <ul>
                                  <li><b>Chat System Prompt</b> - Define the AI's personality and behavior in the chat window</li>
                                  <li><b>Refactor Prompt</b> - Customize how the AI handles code refactoring requests</li>
                                  <li><b>Easy Reset</b> - Restore default prompts anytime with one click</li>
                                </ul>

                                <h4>🔧 How to Access:</h4>
                                <p><b>Settings → OllamAssist → Prompts</b></p>

                                <hr style='margin: 15px 0; border: none; border-top: 1px solid #ccc;'>

                                <h3>⚡ Instant Settings Panel Opening</h3>

                                <p>Settings now open <b>instantly</b> with asynchronous model loading in the background. No more waiting!</p>

                                <ul>
                                  <li><b>Instant Display</b> - Settings panel appears immediately</li>
                                  <li><b>Background Loading</b> - Model lists load asynchronously without blocking</li>
                                  <li><b>Protected Settings</b> - Your configurations are never corrupted during loading</li>
                                </ul>

                                <hr style='margin: 15px 0; border: none; border-top: 1px solid #ccc;'>

                                <p style='font-size: 0.9em; color: #666;'>
                                💡 <i>Tip: Try making the AI more formal for documentation, or more casual for quick experiments!</i>
                                </p>
                                </body>
                                </html>
                                """)
                        .dismissible(true)
                        .createdAt(LocalDateTime.of(2025, 1, 26, 0, 0))
                        .build(),

                // v1.9.0 - Settings organization
                Notification.builder()
                        .id("v1.9.0-release")
                        .version("1.9.0")
                        .type(Notification.NotificationType.FEATURE)
                        .priority(Notification.Priority.HIGH)
                        .title("What's New in OllamAssist 1.9.0")
                        .message("""
                                <html>
                                <body style='font-family: sans-serif; padding: 10px;'>
                                <h3>💡 Improved Settings Panel</h3>

                                <p>The configuration is now organized into <b>three separate tabs</b> for better clarity:</p>
                                <ul>
                                  <li><b>Ollama</b> - Model selection, URLs, and connection settings</li>
                                  <li><b>RAG</b> - Document indexing and search configuration</li>
                                  <li><b>Actions</b> - AI action settings and preferences</li>
                                </ul>

                                <p>This makes it easier to find and configure specific features.</p>

                                <hr style='margin: 15px 0; border: none; border-top: 1px solid #ccc;'>

                                <h3>🚀 Coming Soon: AI File Creation (Experimental)</h3>

                                <p>Ask the AI to <b>create files</b> in your workspace: <i>"Create a HelloWorld class"</i></p>

                                <p><b>⚠️ Model Requirements:</b> Requires tool/function calling capability</p>
                                <ul style='margin-top: 5px;'>
                                  <li>❌ <b>Avoid:</b> <code>llama3.1</code>, <code>llama3.2</code> (unreliable)</li>
                                  <li>✅ <b>Use:</b> <code>qwen2.5:14b+</code>, <code>gpt-oss</code></li>
                                </ul>

                                <p><b>🔧 How to Enable:</b></p>
                                <p><b>Settings → OllamAssist → Actions → Enable AI Tools</b></p>
                                <p style='font-size: 0.9em;'>(Disabled by default - enable only with compatible models)</p>
                                </body>
                                </html>
                                """)
                        .dismissible(true)
                        .createdAt(LocalDateTime.of(2025, 1, 15, 0, 0))
                        .build()

        );
    }
}
