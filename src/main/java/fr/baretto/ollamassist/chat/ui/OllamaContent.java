package fr.baretto.ollamassist.chat.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.messages.MessageBusConnection;
import fr.baretto.ollamassist.chat.service.OllamaService;
import fr.baretto.ollamassist.component.PromptPanel;
import fr.baretto.ollamassist.events.ModelAvailableNotifier;
import fr.baretto.ollamassist.events.NewUserMessageNotifier;
import fr.baretto.ollamassist.prerequiste.LoadingPanel;
import fr.baretto.ollamassist.prerequiste.PrerequisitesPanel;
import fr.baretto.ollamassist.setting.SettingsListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


@Slf4j
public class OllamaContent {

    private final Context context;
    @Getter
    private final JPanel contentPanel = new JPanel();
    private final PromptPanel promptInput = new PromptPanel();
    private final MessagesPanel outputPanel = new MessagesPanel();
    private final PrerequisitesPanel prerequisitesPanel;
    private final AskToChatAction askToChatAction;
    private boolean isAvailable = false;

    public OllamaContent(@NotNull ToolWindow toolWindow) {
        this.context = new Context(toolWindow.getProject());
        askToChatAction = new AskToChatAction(promptInput, context.project());
        promptInput.addActionMap(askToChatAction);
        outputPanel.addContexte(context);


        prerequisitesPanel = new PrerequisitesPanel(toolWindow.getProject());
        promptInput.addActionMap(askToChatAction);
        outputPanel.addContexte(context);
        contentPanel.add(prerequisitesPanel);

        subscribeEvents(toolWindow);
    }

    private void subscribeEvents(@NotNull ToolWindow toolWindow) {
        MessageBusConnection connection = context.project().getMessageBus()
                .connect();

        connection.subscribe(ModelAvailableNotifier.TOPIC, (ModelAvailableNotifier) () -> {
            if (!isAvailable) {
                SwingUtilities.invokeLater(() -> {
                    contentPanel.removeAll();
                    initUI();
                    contentPanel.revalidate();
                    contentPanel.repaint();
                });
            }
        });

        connection.subscribe(NewUserMessageNotifier.TOPIC, (NewUserMessageNotifier) (message) -> {
            outputPanel.addUserMessage(message);
            outputPanel.addNewAIMessage();

            new Thread(() -> context.project()
                    .getService(OllamaService.class)
                    .getAssistant()
                    .chat(message)
                    .onNext(outputPanel::appendToken)
                    .onError(throwable-> log.error(throwable.getMessage()) )
                    .start()
            ).start();
            promptInput.clear();
        });


        connection.subscribe(SettingsListener.TOPIC, (SettingsListener) newState -> context.project()
                .getService(OllamaService.class)
                .forceInit(context));

        Disposer.register(toolWindow.getDisposable(), connection);
    }

    private void initUI() {
        this.isAvailable = true;
        contentPanel.setLayout(new BorderLayout());
        contentPanel.add(createConversationPanel(), BorderLayout.NORTH);
        contentPanel.add(createSplitter(), BorderLayout.CENTER);
    }

    private JPanel createSplitter() {
        OnePixelSplitter splitter = new OnePixelSplitter(true, 0.75f);
        splitter.setFirstComponent(outputPanel);
        splitter.setSecondComponent(createInputPanel());
        splitter.setHonorComponentsMinimumSize(true);
        return splitter;
    }

    private JComponent createInputPanel() {
        JPanel submitPanel = new JPanel(new BorderLayout());
        submitPanel.setMinimumSize(new Dimension(Integer.MAX_VALUE, 100));
        submitPanel.setPreferredSize(new Dimension(Integer.MAX_VALUE, 100));
        JPanel promptsPanel = new JPanel();//@TODO add context file management
        submitPanel.add(promptsPanel, BorderLayout.NORTH);
        JBScrollPane scrollPane = new JBScrollPane(promptInput);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); // Pas de scrollbar horizontale
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER); // Pas de scrollbar verticale
        submitPanel.add(scrollPane, BorderLayout.CENTER);
        return submitPanel;
    }

    private JPanel createConversationPanel() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        JPanel conversationPanel = new JPanel(new BorderLayout());
        conversationPanel.setLayout(new BoxLayout(conversationPanel, BoxLayout.Y_AXIS));

        conversationPanel.setPreferredSize(new Dimension(0, 24));
        JBScrollPane scrollPane = new JBScrollPane(container);

        ConversationSelectorPanel conversationSelectorPanel = new ConversationSelectorPanel();
        conversationPanel.add(conversationSelectorPanel, BorderLayout.NORTH);
        conversationPanel.add(scrollPane, BorderLayout.CENTER);
        return conversationPanel;
    }

}
