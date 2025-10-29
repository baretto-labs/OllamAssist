package fr.baretto.ollamassist.chat.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import dev.langchain4j.model.chat.response.ChatResponse;
import fr.baretto.ollamassist.core.agent.AgentTaskNotifier;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.ui.ActionProposalCard;
import fr.baretto.ollamassist.events.ConversationNotifier;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.List;

@Slf4j
public class MessagesPanel extends JBPanel<MessagesPanel> {
    private final JBPanel<?> container = new JBPanel<>(new GridBagLayout());
    private final JBScrollPane scrollPane;
    private OllamaMessage latestOllamaMessage;
    private transient Context context;
    private PresentationPanel presentationPanel = new PresentationPanel();
    private boolean autoScrollEnabled = true;
    private MessageBusConnection messageBusConnection;
    private fr.baretto.ollamassist.component.PromptPanel promptPanel;

    public MessagesPanel() {
        super(new BorderLayout());
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(UIUtil.getPanelBackground());
        container.setBorder(JBUI.Borders.empty(8));

        scrollPane = new JBScrollPane(container);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(JBUI.Borders.empty());

        setBackground(UIUtil.getPanelBackground());
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

    public void setPromptPanel(fr.baretto.ollamassist.component.PromptPanel promptPanel) {
        this.promptPanel = promptPanel;
    }

    public void addContexteAndPrompt(Context context, fr.baretto.ollamassist.component.PromptPanel promptPanel) {
        this.context = context;
        this.promptPanel = promptPanel;

        // S'abonner aux notifications d'agent pour afficher les réponses
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }

        messageBusConnection = context.project().getMessageBus().connect();
        messageBusConnection.subscribe(AgentTaskNotifier.TOPIC, new AgentTaskNotifier() {
            private OllamaMessage currentStreamingMessage;

            @Override
            public void taskStarted(Task task) {
                // Pas besoin d'afficher pour l'instant
            }

            @Override
            public void taskCompleted(Task task, fr.baretto.ollamassist.core.agent.task.TaskResult result) {
                // Pas besoin d'afficher pour l'instant
            }

            @Override
            public void taskProgress(Task task, String progressMessage) {
                // Pas besoin d'afficher pour l'instant
            }

            @Override
            public void taskCancelled(Task task) {
                // Pas besoin d'afficher pour l'instant
            }

            @Override
            public void agentProcessingStarted(String userRequest) {
                log.error("DEBUG: MessagesPanel.agentProcessingStarted called with: {}", userRequest);
                SwingUtilities.invokeLater(() -> {
                    // Nettoyer le presentation panel comme dans addUserMessage()
                    if (presentationPanel != null) {
                        container.remove(presentationPanel);
                        presentationPanel = null;
                    }

                    // Afficher le message utilisateur
                    UserMessage userMessage = new UserMessage(userRequest);
                    container.add(userMessage, createGbc(container.getComponentCount()));

                    // Créer et ajouter le message de l'agent pour le streaming
                    currentStreamingMessage = new OllamaMessage(context);
                    container.add(currentStreamingMessage, createGbc(container.getComponentCount()));

                    scrollToBottom();
                    container.revalidate();
                    container.repaint();

                    log.error("DEBUG: UI components added and refreshed");
                });
            }

            @Override
            public void agentStreamingToken(String token) {
                SwingUtilities.invokeLater(() -> {
                    if (currentStreamingMessage != null) {
                        currentStreamingMessage.append(token);
                        scrollToBottom();
                    }
                });
            }

            @Override
            public void agentProcessingCompleted(String userRequest, String response) {
                log.error("DEBUG: MessagesPanel.agentProcessingCompleted called with response: {}", response);
                SwingUtilities.invokeLater(() -> {
                    // Si c'était du streaming, finaliser le message
                    if (currentStreamingMessage != null) {
                        latestOllamaMessage = currentStreamingMessage;
                        currentStreamingMessage = null;
                        log.error("DEBUG: Finalized streaming message");
                    } else {
                        // Sinon c'était une action - créer et afficher le message de réponse
                        log.error("DEBUG: Creating new response message for action");
                        OllamaMessage responseMessage = new OllamaMessage(context);
                        responseMessage.append(response);
                        container.add(responseMessage, createGbc(container.getComponentCount()));
                        latestOllamaMessage = responseMessage;

                        container.revalidate();
                        container.repaint();
                        scrollToBottom();
                    }

                    // Réactiver le prompt après la réponse
                    if (promptPanel != null) {
                        promptPanel.toggleGenerationState(false);
                    }

                    log.error("DEBUG: agentProcessingCompleted finished");
                });
            }

            @Override
            public void agentProcessingFailed(String userRequest, String errorMessage) {
                SwingUtilities.invokeLater(() -> {
                    // Si streaming en cours, l'arrêter et afficher l'erreur
                    if (currentStreamingMessage != null) {
                        currentStreamingMessage.append("\n\n" + errorMessage);
                        currentStreamingMessage = null;
                    } else {
                        // Sinon créer un nouveau message d'erreur
                        OllamaMessage errorMsg = new OllamaMessage(context);
                        errorMsg.append("" + errorMessage);
                        container.add(errorMsg, createGbc(container.getComponentCount()));
                        container.revalidate();
                        container.repaint();
                    }

                    scrollToBottom();

                    // Réactiver le prompt même en cas d'erreur
                    if (promptPanel != null) {
                        promptPanel.toggleGenerationState(false);
                    }
                });
            }

            @Override
            public void agentProposalRequested(String userRequest, List<Task> proposedTasks, fr.baretto.ollamassist.core.agent.ui.ActionProposalCard.ActionValidator validator) {
                SwingUtilities.invokeLater(() -> {
                    log.error("DEBUG: MessagesPanel.agentProposalRequested called with {} tasks", proposedTasks.size());

                    // Finaliser le streaming en cours s'il y en a un
                    if (currentStreamingMessage != null) {
                        currentStreamingMessage = null;
                    }

                    // Afficher la proposition d'actions
                    displayActionProposal(proposedTasks, validator);

                    // Réactiver le prompt
                    if (promptPanel != null) {
                        promptPanel.toggleGenerationState(false);
                    }

                    log.error("DEBUG: Action proposal displayed successfully");
                });
            }
        });
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

    public void finalizeMessage(ChatResponse chatResponse) {
        latestOllamaMessage.finalizeResponse(chatResponse);
        latestOllamaMessage = null;
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

    // Méthodes pour l'intégration agent
    public void displayActionProposal(List<Task> proposedTasks, ActionProposalCard.ActionValidator actionValidator) {
        ActionProposalCard actionCard = new ActionProposalCard(proposedTasks, actionValidator);
        container.add(actionCard, createGbc(container.getComponentCount()));

        if (autoScrollEnabled) {
            scrollToBottom();
        }

        container.revalidate();
        container.repaint();
    }

    public void removeActionProposal(List<Task> tasks) {
        Component[] components = container.getComponents();
        for (Component component : components) {
            if (component instanceof ActionProposalCard card) {
                if (card.getTasks().equals(tasks)) {
                    container.remove(component);
                    break;
                }
            }
        }
        container.revalidate();
        container.repaint();
    }

    // Ces méthodes ne sont plus nécessaires car on utilise le chat unifié

    // Getters pour les tests
    public JBPanel<?> getContainer() {
        return container;
    }

    public boolean isAutoScrollEnabled() {
        return autoScrollEnabled;
    }

    public void setAutoScrollEnabled(boolean autoScrollEnabled) {
        this.autoScrollEnabled = autoScrollEnabled;
    }

}