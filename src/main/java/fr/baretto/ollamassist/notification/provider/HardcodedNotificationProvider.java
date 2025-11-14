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
                // v1.9.0 - Combined notification: Settings + File Creation
                Notification.builder()
                        .id("v1.9.0-release")
                        .version("1.9.0")
                        .type(Notification.NotificationType.FEATURE)
                        .priority(Notification.Priority.HIGH)
                        .title("What's New in OllamAssist 1.9.0")
                        .message("""
                                <html>
                                <body style='font-family: sans-serif; padding: 10px;'>
                                <h3>🎉 New Feature: AI-Powered File Creation</h3>

                                <p>You can now ask the AI to <b>create files automatically</b> in your workspace.
                                Simply ask something like: <i>"Create a HelloWorld class"</i> and the AI will generate
                                the code and create the file for you (with your approval).</p>

                                <p><b>⚠️ Important - Model Compatibility:</b></p>
                                <p>Not all models support this feature reliably. Small models (&lt;8B parameters)
                                like <code>llama3.1</code> don't have reliable function calling capabilities.</p>

                                <p><b>Recommended models for file actions:</b></p>
                                <ul>
                                  <li><code>qwen2.5:14b</code> or larger</li>
                                  <li><code>gpt-oss</code> (via OpenAI-compatible API)</li>
                                  <li>Any model with 8B+ parameters specifically trained for tool usage</li>
                                </ul>

                                <p style='color: gray; font-size: 0.9em;'><i>Note: You can continue using any model for
                                regular chat and code assistance - this limitation only affects automatic file creation actions.</i></p>

                                <hr style='margin: 15px 0; border: none; border-top: 1px solid #ccc;'>

                                <h3>💡 Improved Settings Panel</h3>

                                <p>The configuration is now organized into <b>three separate tabs</b> for better clarity:</p>
                                <ul>
                                  <li><b>Ollama</b> - Model selection, URLs, and connection settings</li>
                                  <li><b>RAG</b> - Document indexing and search configuration</li>
                                  <li><b>Actions</b> - AI action settings and preferences</li>
                                </ul>

                                <p>This makes it easier to find and configure specific features.</p>
                                </body>
                                </html>
                                """)
                        .dismissible(true)
                        .createdAt(LocalDateTime.of(2025, 1, 15, 0, 0))
                        .build()

                // Future notifications can be added here:
                // Notification.builder()
                //     .id("v1.10.0-new-rag")
                //     .version("1.10.0")
                //     ...
        );
    }
}
