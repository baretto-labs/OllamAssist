package fr.baretto.ollamassist.prerequiste;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import fr.baretto.ollamassist.chat.service.OllamaService;
import fr.baretto.ollamassist.chat.ui.IconUtils;
import fr.baretto.ollamassist.component.ComponentCustomizer;
import fr.baretto.ollamassist.core.service.ModelAssistantService;
import fr.baretto.ollamassist.events.ModelAvailableNotifier;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.CompletableFuture;

public class PrerequisitesPanel extends SimpleToolWindowPanel {

    private final PrerequisiteService prerequisiteService = ApplicationManager.getApplication().getService(PrerequisiteService.class);
    private final JBLabel ollamaLabel = new JBLabel();
    private final JBLabel chatModelLabel = new JBLabel();
    private final JBLabel autocompleteModelLabel = new JBLabel();
    private final JBLabel embeddingModelLabel = new JBLabel();
    private final JPanel restartPanel = new JPanel();
    private final JBLabel restartMessage = new JBLabel();
    private final JButton restartButton = new JButton();
    private final JBLabel ollamaDownloadLink = new JBLabel();
    private final JBTextField chatModelCommandField = new JBTextField();
    private final JBTextField autocompleteModelCommandField = new JBTextField();
    private final JBTextField embeddingModelCommandField = new JBTextField();
    private final JButton copyChatCommandButton = new JButton("Copy");
    private final JButton copyAutocompleteCommandButton = new JButton("Copy");
    private final JButton copyEmbeddingCommandButton = new JButton("Copy");
    private final JPanel ollamaHelpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    private final JPanel chatModelHelpPanel = new JPanel(new BorderLayout(5, 0));
    private final JPanel autocompleteModelHelpPanel = new JPanel(new BorderLayout(5, 0));
    private final JPanel embeddingModelHelpPanel = new JPanel(new BorderLayout(5, 0));
    private final JPanel loadingLabel = new LoadingPanel("OllamAssist starting ... ");
    private final Project project;

    public PrerequisitesPanel(Project project) {
        super(true, true);
        this.project = project;
        initPanel();
        ComponentCustomizer.applyHoverEffect(restartButton);
        ComponentCustomizer.applyHoverEffect(copyAutocompleteCommandButton);
        ComponentCustomizer.applyHoverEffect(copyChatCommandButton);
        ComponentCustomizer.applyHoverEffect(copyEmbeddingCommandButton);
    }

    private void initPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        JScrollPane scrollPane = new JBScrollPane(mainPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        setContent(scrollPane);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new GridBagLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(2, 5);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JBLabel title = new JBLabel("Prerequisites Check:");
        title.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, 14f));
        contentPanel.add(title, gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        contentPanel.add(new JBLabel("Ollama:"), gbc);

        gbc.gridy++;
        contentPanel.add(ollamaLabel, gbc);

        gbc.gridy++;
        ollamaHelpPanel.add(new JBLabel("Install:"));
        configureDownloadLink();
        ollamaHelpPanel.add(ollamaDownloadLink);
        contentPanel.add(ollamaHelpPanel, gbc);

        String chatModelName = OllamAssistSettings.getInstance().getChatModelName();
        gbc.gridy++;
        gbc.insets.top = 15;
        contentPanel.add(new JBLabel(chatModelName + " (Chat):"), gbc);

        gbc.gridy++;
        gbc.insets.top = 2;
        contentPanel.add(chatModelLabel, gbc);

        gbc.gridy++;
        chatModelHelpPanel.add(new JBLabel("Install with:"));
        JPanel chatCommandPanel = createCommandPanel(chatModelCommandField, copyChatCommandButton, chatModelName);
        chatModelHelpPanel.add(chatCommandPanel);
        contentPanel.add(chatModelHelpPanel, gbc);

        String autocompleteModelName = OllamAssistSettings.getInstance().getCompletionModelName();
        gbc.gridy++;
        gbc.insets.top = 15;
        contentPanel.add(new JBLabel(autocompleteModelName + " (Autocomplete):"), gbc);

        gbc.gridy++;
        gbc.insets.top = 2;
        contentPanel.add(autocompleteModelLabel, gbc);

        gbc.gridy++;
        autocompleteModelHelpPanel.add(new JBLabel("Install with:"));
        JPanel autocompleteCommandPanel = createCommandPanel(autocompleteModelCommandField, copyAutocompleteCommandButton, autocompleteModelName);
        autocompleteModelHelpPanel.add(autocompleteCommandPanel);
        contentPanel.add(autocompleteModelHelpPanel, gbc);

        String embeddingModelName = OllamAssistSettings.getInstance().getEmbeddingModelName();
        gbc.gridy++;
        gbc.insets.top = 15;
        contentPanel.add(new JBLabel(embeddingModelName + " (Embedding):"), gbc);

        gbc.gridy++;
        gbc.insets.top = 2;
        contentPanel.add(embeddingModelLabel, gbc);

        gbc.gridy++;
        embeddingModelHelpPanel.add(new JBLabel("Install with:"));
        JPanel embeddingCommandPanel = createCommandPanel(embeddingModelCommandField, copyEmbeddingCommandButton, embeddingModelName);
        embeddingModelHelpPanel.add(embeddingCommandPanel);
        contentPanel.add(embeddingModelHelpPanel, gbc);

        gbc.gridy++;
        gbc.insets.top = 30;
        restartPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));
        restartMessage.setIcon(IconUtils.INFORMATION);
        restartMessage.setText("<html>Please rerun check  after installing prerequisites</html>");
        restartMessage.setForeground(JBColor.ORANGE);
        restartButton.setIcon(IconUtils.RESTART);
        restartButton.addActionListener(e -> checkPrerequisitesAsync());
        restartPanel.add(restartMessage);
        restartPanel.add(restartButton);
        contentPanel.add(restartPanel, gbc);

        gbc.gridy++;
        loadingLabel.setVisible(false);
        contentPanel.add(loadingLabel, gbc);

        mainPanel.add(contentPanel, BorderLayout.NORTH);
        styleComponents();
        checkPrerequisitesAsync();
    }

    private JPanel createCommandPanel(JBTextField field, JButton button, String modelName) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        field.setEditable(false);
        field.setText("ollama pull " + modelName);

        JScrollPane scrollPane = new JScrollPane(field);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);

        panel.add(scrollPane, BorderLayout.CENTER);
        button.addActionListener(e -> {
            StringSelection selection = new StringSelection(field.getText());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
        });
        panel.add(button, BorderLayout.EAST);
        return panel;
    }

    private void configureDownloadLink() {
        ollamaDownloadLink.setText("<html><a href=''>Download Ollama</a></html>");
        ollamaDownloadLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        ollamaDownloadLink.setForeground(JBColor.BLUE);
        ollamaDownloadLink.addMouseListener(downLoadLinkMouseAdapter());
    }

    private @NotNull MouseAdapter downLoadLinkMouseAdapter() {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                BrowserUtil.browse("https://ollama.ai/download");
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                ollamaDownloadLink.setText("<html><a style='text-decoration:underline;'>Download Ollama</a></html>");
            }

            @Override
            public void mouseExited(MouseEvent e) {
                ollamaDownloadLink.setText("<html><a>Download Ollama</a></html>");
            }
        };
    }

    private void styleComponents() {
        ollamaDownloadLink.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        chatModelCommandField.setBackground(UIUtil.getPanelBackground());
        chatModelCommandField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        chatModelCommandField.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        autocompleteModelCommandField.setBackground(UIUtil.getPanelBackground());
        autocompleteModelCommandField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        autocompleteModelCommandField.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        embeddingModelCommandField.setBackground(UIUtil.getPanelBackground());
        embeddingModelCommandField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        embeddingModelCommandField.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        copyChatCommandButton.setIcon(IconUtils.COPY);
        copyChatCommandButton.setText("");
        copyChatCommandButton.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        copyAutocompleteCommandButton.setIcon(IconUtils.COPY);
        copyAutocompleteCommandButton.setText("");
        copyAutocompleteCommandButton.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        copyEmbeddingCommandButton.setIcon(IconUtils.COPY);
        copyEmbeddingCommandButton.setText("");
        copyEmbeddingCommandButton.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        restartButton.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
    }

    private void checkPrerequisitesAsync() {
        OllamAssistSettings settings = OllamAssistSettings.getInstance();

        CompletableFuture<Boolean> ollamaRunningFuture = prerequisiteService.isOllamaRunningAsync(settings.getChatOllamaUrl());
        CompletableFuture<Boolean> chatModelFuture = prerequisiteService.isChatModelAvailableAsync(settings.getChatOllamaUrl(), settings.getChatModelName());
        CompletableFuture<Boolean> autocompleteModelFuture = prerequisiteService.isAutocompleteModelAvailableAsync(settings.getCompletionOllamaUrl(), settings.getCompletionModelName());
        CompletableFuture<Boolean> embeddingModelFuture = prerequisiteService.isEmbeddingModelAvailableAsync(settings.getEmbeddingOllamaUrl(), settings.getEmbeddingModelName());

        CompletableFuture.allOf(ollamaRunningFuture, chatModelFuture, autocompleteModelFuture, embeddingModelFuture)
                .thenAccept(v -> {
                    boolean ollamaReady = ollamaRunningFuture.join();
                    boolean chatModelReady = chatModelFuture.join();
                    boolean autocompleteModelReady = autocompleteModelFuture.join();
                    boolean embeddingModelReady = embeddingModelFuture.join();

                    ApplicationManager.getApplication().invokeLater(() ->
                            updateUI(ollamaReady, chatModelReady, autocompleteModelReady, embeddingModelReady));

                    if (ollamaReady && chatModelReady && autocompleteModelReady && embeddingModelReady) {
                        project.getService(OllamaService.class).init();
                        ApplicationManager.getApplication().getService(ModelAssistantService.class);

                        ApplicationManager.getApplication()
                                .getMessageBus()
                                .syncPublisher(ModelAvailableNotifier.TOPIC)
                                .onModelAvailable();
                    }
                });
    }


    private void updateUI(boolean ollamaReady, boolean chatModelReady, boolean autocompleteModelReady, boolean embeddingModelReady) {
        updateLabel(ollamaLabel, ollamaReady, ollamaReady ? "Ollama running" : "Ollama unavailable");
        updateLabel(chatModelLabel, chatModelReady, chatModelReady ? "Model available" : "Model not found");
        updateLabel(autocompleteModelLabel, autocompleteModelReady, autocompleteModelReady ? "Model available" : "Model not found");
        updateLabel(embeddingModelLabel, embeddingModelReady, embeddingModelReady ? "Model available" : "Model not found");

        ollamaHelpPanel.setVisible(!ollamaReady);
        chatModelHelpPanel.setVisible(!chatModelReady);
        autocompleteModelHelpPanel.setVisible(!autocompleteModelReady);
        embeddingModelHelpPanel.setVisible(!embeddingModelReady);

        boolean allReady = ollamaReady && chatModelReady && autocompleteModelReady && embeddingModelReady;
        restartPanel.setVisible(!allReady);
        loadingLabel.setVisible(allReady);
    }

    private void updateLabel(JBLabel label, boolean status, String text) {
        label.setIcon(status ? IconUtils.VALIDATE : IconUtils.ERROR);
        label.setForeground(status ? JBColor.GREEN : JBColor.RED);
        label.setText(text);
    }
}