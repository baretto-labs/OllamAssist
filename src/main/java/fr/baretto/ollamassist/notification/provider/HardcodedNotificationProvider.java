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
                // v1.11.0 - Conversation Management
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

                                <h4>✨ What's New:</h4>
                                <ul>
                                  <li><b>Persistent conversations</b> - Each conversation is saved per project under <code>.ollamassist/conversations/</code></li>
                                  <li><b>Multiple conversations</b> - Create as many conversations as you need and switch between them</li>
                                  <li><b>Auto-generated titles</b> - Conversation titles are automatically derived from your first message</li>
                                  <li><b>Delete conversations</b> - Remove conversations you no longer need (with confirmation)</li>
                                  <li><b>Resume anywhere</b> - Reopen the IDE and pick up exactly where you left off</li>
                                </ul>

                                <h4>🔧 How to Use:</h4>
                                <p>Use the dropdown at the top of the chat panel to switch conversations, or click <b>+</b> to start a new one.</p>

                                <p style='font-size: 0.9em; color: #666;'>
                                💡 <i>Tip: Keep separate conversations for different tasks or topics within the same project!</i>
                                </p>
                                </body>
                                </html>
                                """)
                        .dismissible(true)
                        .createdAt(LocalDateTime.of(2026, 3, 25, 0, 0))
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
