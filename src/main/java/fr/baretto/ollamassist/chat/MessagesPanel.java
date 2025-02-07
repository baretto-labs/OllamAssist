package fr.baretto.ollamassist.chat;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.messages.MessageBusConnection;
import fr.baretto.ollamassist.ai.OllamaService;
import fr.baretto.ollamassist.events.ConversationNotifier;
import fr.baretto.ollamassist.events.ModelAvailableNotifier;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

public class MessagesPanel extends JPanel {
    private final JPanel container = new JPanel();
    private final JBScrollPane scrollPane;
    private OllamaMessage latestOllamaMessage;
    private Context context;
    private PresentationPanel presentationPanel = new PresentationPanel();
    private boolean autoScrollEnabled = true;

    public MessagesPanel() {
        super(new BorderLayout());
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        scrollPane = new JBScrollPane(container);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); // Pas de scrollbar horizontale

        add(scrollPane, BorderLayout.CENTER);
        container.add(presentationPanel);

        JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
        verticalScrollBar.addAdjustmentListener(new AdjustmentListener() {
            private int lastValue = verticalScrollBar.getValue();

            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                int currentValue = verticalScrollBar.getValue();
                int maxValue = verticalScrollBar.getMaximum() - verticalScrollBar.getVisibleAmount();

                if (currentValue < lastValue) {
                    autoScrollEnabled = false;
                }

                if (currentValue == maxValue) {
                    autoScrollEnabled = true;
                }
                lastValue = currentValue;
            }
        });

        MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus()
                .connect();
        connection.subscribe(ConversationNotifier.TOPIC, (ConversationNotifier) this::clearAll);
    }

    private void clearAll(){
        container.removeAll();
        container.repaint();
        container.revalidate();
    }
    public void addUserMessage(final String userMessage) {
        if (presentationPanel != null) {
            container.remove(presentationPanel);
            presentationPanel = null;
        }
        UserMessage userMessagePanel = new UserMessage(userMessage);
        container.add(userMessagePanel);
        scrollToBottom();
        container.revalidate();
        container.repaint();
    }

    public void addNewAIMessage() {
        latestOllamaMessage = new OllamaMessage(context);
        container.add(latestOllamaMessage);
        scrollToBottom();
        container.revalidate();
        container.repaint();
    }

    public void appendToken(String token) {
        latestOllamaMessage.append(token);
        scrollToBottom();
    }

    public void addContexte(Context context) {
        this.context = context;
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            Timer timer = new Timer(100, e -> {
                if (autoScrollEnabled) {
                    JScrollBar vertical = scrollPane.getVerticalScrollBar();
                    vertical.setValue(vertical.getMaximum());
                }
            });
            timer.setRepeats(false);
            timer.start();
        });
    }


}