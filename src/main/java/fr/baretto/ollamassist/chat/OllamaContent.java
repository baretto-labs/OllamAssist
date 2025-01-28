package fr.baretto.ollamassist.chat;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.messages.MessageBusConnection;
import fr.baretto.ollamassist.ai.OllamaService;
import fr.baretto.ollamassist.events.ModelAvailableNotifier;
import fr.baretto.ollamassist.events.UIAvailableNotifier;
import fr.baretto.ollamassist.setting.SettingsListener;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


public class OllamaContent {

    private final Context context;
    @Getter
    private final JPanel contentPanel = new JPanel();
    private final PromptPanel promptInput = new PromptPanel();
    private final MessagesPanel outputPanel = new MessagesPanel();
    private final AskToChatAction askToChatAction;
    private boolean isAvailable = false;

    public OllamaContent(@NotNull ToolWindow toolWindow) {
        this.context = new Context(toolWindow.getProject());
        askToChatAction = new AskToChatAction(promptInput, outputPanel, context);
        promptInput.addActionMap(askToChatAction);
        outputPanel.addContexte(context);
        contentPanel.add(new LoadingPanel(contentPanel.getPreferredSize()));


        MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus()
                .connect();

        connection.subscribe(ModelAvailableNotifier.TOPIC, (ModelAvailableNotifier) () -> {
            if (!isAvailable) {
                SwingUtilities.invokeLater(() -> {
                    contentPanel.removeAll();
                    initUI();
                    contentPanel.revalidate();
                    contentPanel.repaint();
                    ApplicationManager.getApplication().getService(OllamaService.class).init(context);
                });
            }
        });

        connection
                .subscribe(SettingsListener.TOPIC, (SettingsListener) newState -> ApplicationManager.getApplication().getService(OllamaService.class).forceInit(context));

        ApplicationManager.getApplication()
                .getMessageBus()
                .syncPublisher(UIAvailableNotifier.TOPIC)
                .onUIAvailable();

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
        submitPanel.add(createActionButtonsPanel(), BorderLayout.SOUTH);
        return submitPanel;
    }

    private JPanel createActionButtonsPanel() {
        JPanel buttonsPanel = new JPanel(new BorderLayout());
        buttonsPanel.setPreferredSize(new Dimension(30, 30));
        JButton executePrompt = new JButton(ImageUtil.SUBMIT);
        executePrompt.setOpaque(true);
        executePrompt.setBorderPainted(false);
        executePrompt.addActionListener(askToChatAction);
        executePrompt.setContentAreaFilled(false);

        executePrompt.setPressedIcon(ImageUtil.SUBMIT_PRESSED);

        executePrompt.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                executePrompt.setLocation(executePrompt.getX() + 2, executePrompt.getY() + 2);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                executePrompt.setLocation(executePrompt.getX() - 2, executePrompt.getY() - 2);
            }
        });


        buttonsPanel.add(executePrompt, BorderLayout.EAST);
        return buttonsPanel;
    }


    private JPanel createConversationPanel() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        JPanel conversationPanel = new JPanel(new BorderLayout());
        conversationPanel.setLayout(new BoxLayout(conversationPanel, BoxLayout.Y_AXIS));

        conversationPanel.setPreferredSize(new Dimension(0, 20));
        JBScrollPane scrollPane = new JBScrollPane(container);

        conversationPanel.add(scrollPane, BorderLayout.CENTER);
        return conversationPanel;
    }

}
