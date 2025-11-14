package fr.baretto.ollamassist.chat.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.messages.MessageBusConnection;
import dev.langchain4j.model.chat.response.ChatResponse;
import fr.baretto.ollamassist.events.ConversationNotifier;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.function.Consumer;

public class MessagesPanel extends JPanel {
    private final JPanel container = new JPanel(new GridBagLayout());
    private final JBScrollPane scrollPane;
    private OllamaMessage latestOllamaMessage;
    private transient Context context;
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

    private void clearAll() {
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
        container.add(userMessagePanel, createGbc(container.getComponentCount()));
        scrollToBottom();
        container.revalidate();
        container.repaint();
    }

    public void addNewAIMessage() {
        latestOllamaMessage = new OllamaMessage(context);
        container.add(latestOllamaMessage, createGbc(container.getComponentCount()));
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

    public void cancelMessage() {
        if (latestOllamaMessage != null) {
            latestOllamaMessage.cancel();
        }
    }

    public void stopMessageSilently() {
        if (latestOllamaMessage != null) {
            latestOllamaMessage.stopSilently();
        }
    }

    public void finalizeMessage(ChatResponse chatResponse) {
        latestOllamaMessage.finalizeResponse(chatResponse);
        latestOllamaMessage = null;
    }

    public void addApprovalRequest(String title, String filePath, String content, Consumer<Boolean> onDecision) {
        SwingUtilities.invokeLater(() -> {
            ApprovalMessage approvalMessage = new ApprovalMessage(title, filePath, content, onDecision);
            container.add(approvalMessage, createGbc(container.getComponentCount()));
            scrollToBottom();
            container.revalidate();
            container.repaint();
        });
    }

    private GridBagConstraints createGbc(int gridy) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = gridy;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        return gbc;
    }

}