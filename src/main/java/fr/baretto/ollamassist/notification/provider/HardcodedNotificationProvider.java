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
