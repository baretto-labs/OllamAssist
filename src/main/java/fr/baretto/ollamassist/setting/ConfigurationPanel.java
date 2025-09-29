package fr.baretto.ollamassist.setting;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.util.ui.JBUI;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModels;
import fr.baretto.ollamassist.component.ComponentCustomizer;
import fr.baretto.ollamassist.events.StoreNotifier;
import fr.baretto.ollamassist.setting.agent.AgentModeSettings;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static fr.baretto.ollamassist.chat.rag.RAGConstants.DEFAULT_EMBEDDING_MODEL;
import static fr.baretto.ollamassist.setting.OllamAssistSettings.DEFAULT_URL;

public class ConfigurationPanel extends JPanel {

    private final JBTextField chatOllamaUrl = new JBTextField(OllamAssistSettings.getInstance().getChatOllamaUrl());
    private final JBTextField completionOllamaUrl = new JBTextField(OllamAssistSettings.getInstance().getCompletionOllamaUrl());
    private final JBTextField embeddingOllamaUrl = new JBTextField(OllamAssistSettings.getInstance().getEmbeddingOllamaUrl());
    private final ComboBox<String> chatModel;
    private final ComboBox<String> completionModel;
    private final ComboBox<String> embeddingModel;
    private final JBTextField timeout = new IntegerField(null, 0, Integer.MAX_VALUE);
    private final JBTextField sources = new JBTextField();
    private final IntegerField maxDocuments = new IntegerField(null, 1, 100000);

    // === Champs pour le mode agent ===
    private final JBCheckBox agentModeEnabled = new JBCheckBox("Enable Agent Mode");
    private final ComboBox<AgentModeSettings.AgentSecurityLevel> agentSecurityLevel = new ComboBox<>(AgentModeSettings.AgentSecurityLevel.values());
    private final IntegerField agentMaxTasksPerSession = new IntegerField(null, 1, 100);
    private final JBCheckBox agentAutoApprovalEnabled = new JBCheckBox("Enable Auto-approval for Safe Actions");

    private final transient Project project;
    private final List<Consumer<Boolean>> changeListeners = new ArrayList<>();

    public ConfigurationPanel(Project project) {
        this.project = project;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(JBUI.Borders.empty(10));

        chatModel = createComboBox();
        add(createOllamaUrlField("Chat Ollama URL:", chatOllamaUrl, "The URL of the Ollama server for chat.", chatModel, false));
        add(createLabeledField("Chat model:", chatModel, "The model should be loaded before use."));

        completionModel = createComboBox();
        add(createOllamaUrlField("Completion Ollama URL:", completionOllamaUrl, "The URL of the Ollama server for completion.", completionModel, false));
        add(createLabeledField("Completion model:", completionModel, "The model should be loaded before use."));

        embeddingModel = createComboBox();
        add(createOllamaUrlField("Embedding Ollama URL:", embeddingOllamaUrl, "The URL of the Ollama server for embedding.", embeddingModel, true));
        add(createLabeledField("Embedding model:", embeddingModel,
                "Model loaded by Ollama, used for transformation into Embeddings; it must be loaded before use. " +
                        "For example: nomic-embed-text. " +
                        "By default, the BgeSmallEnV15QuantizedEmbeddingModel embedded in the application is used."));

        add(createLabeledField("Response timeout:", timeout, "The total number of seconds allowed for a response."));
        add(createLabeledField("Indexed Folders:", sources, "Separated by ';'"));
        add(createLabeledField("Maximum number of documents indexed at once", maxDocuments,
                "The maximum number of documents indexed during a batch indexation"));
        add(createClearEmbeddingButton());

        // === Section Mode Agent ===
        add(Box.createVerticalStrut(15));
        add(createAgentSectionSeparator());
        add(createAgentModeCheckbox());
        add(createLabeledField("Security Level:", agentSecurityLevel,
                "STRICT: All actions require approval | STANDARD: Only risky actions require approval | EXPERT: Only high-risk actions require approval"));
        add(createLabeledField("Max Tasks per Session:", agentMaxTasksPerSession,
                "Maximum number of tasks the agent can execute in one session (1-100)"));
        add(createAgentAutoApprovalCheckbox());

        initializeListeners();
    }

    public void addChangeListener(Consumer<Boolean> listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Consumer<Boolean> listener) {
        changeListeners.remove(listener);
    }

    private void notifyChangeListeners() {
        changeListeners.forEach(listener -> listener.accept(true));
    }

    private void initializeListeners() {
        DocumentListener documentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                notifyChangeListeners();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                notifyChangeListeners();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                notifyChangeListeners();
            }
        };

        chatOllamaUrl.getDocument().addDocumentListener(documentListener);
        completionOllamaUrl.getDocument().addDocumentListener(documentListener);
        embeddingOllamaUrl.getDocument().addDocumentListener(documentListener);
        timeout.getDocument().addDocumentListener(documentListener);
        sources.getDocument().addDocumentListener(documentListener);
        maxDocuments.getDocument().addDocumentListener(documentListener);

        // Listeners pour les composants agent
        agentModeEnabled.addActionListener(e -> notifyChangeListeners());
        agentSecurityLevel.addActionListener(e -> notifyChangeListeners());
        agentMaxTasksPerSession.getDocument().addDocumentListener(documentListener);
        agentAutoApprovalEnabled.addActionListener(e -> notifyChangeListeners());


        ItemListener itemListener = e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                notifyChangeListeners();
            }
        };
        chatModel.addItemListener(itemListener);
        completionModel.addItemListener(itemListener);
        embeddingModel.addItemListener(itemListener);
    }

    private JPanel createOllamaUrlField(String label, JBTextField ollamaUrl, String message, ComboBox<String> modelComboBox, boolean isEmbedding) {
        JPanel panel = createLabeledField(label, ollamaUrl, message);
        ollamaUrl.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                new Thread(() -> updateAvailableModels(ollamaUrl, modelComboBox, isEmbedding)).start();
            }

            @Override
            public void focusGained(FocusEvent e) {
                ollamaUrl.setBackground(UIManager.getColor("TextField.background"));
            }
        });
        return panel;
    }

    private ComboBox<String> createComboBox() {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.setEditable(false);
        comboBox.setPreferredSize(new Dimension(200, 30));
        comboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return comboBox;
    }

    private JPanel createLabeledField(String label, JComponent component, String message) {
        JPanel panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(5, 0));

        JBLabel fieldLabel = new JBLabel(label);
        fieldLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(fieldLabel);

        panel.add(Box.createVerticalStrut(5));

        component.setPreferredSize(new Dimension(200, 30));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setAlignmentY(Component.CENTER_ALIGNMENT);
        panel.add(component);

        if (message != null) {
            JTextArea infoText = new JTextArea(message);
            infoText.setEditable(false);
            infoText.setLineWrap(true);
            infoText.setWrapStyleWord(true);
            infoText.setBackground(panel.getBackground());
            infoText.setFont(infoText.getFont().deriveFont(Font.ITALIC));
            infoText.setForeground(UIManager.getColor("Label.disabledForeground"));
            infoText.setBorder(BorderFactory.createEmptyBorder());
            infoText.setFocusable(false);
            infoText.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoText.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

            panel.add(Box.createVerticalStrut(3));
            panel.add(infoText);
        }

        return panel;
    }

    private JPanel createClearEmbeddingButton() {
        JPanel panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(10, 0));
        JButton clearButton = new JButton("Clear Embedding Store");
        ComponentCustomizer.applyHoverEffect(clearButton);
        clearButton.setPreferredSize(new Dimension(200, 30));
        clearButton.setMaximumSize(new Dimension(200, 30));
        clearButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        clearButton.addActionListener(e -> {
            int result = Messages.showYesNoDialog(
                    "Are you sure you want to clear the embedding store? This action cannot be undone.",
                    "Clear Embedding Store",
                    Messages.getWarningIcon()
            );
            if (result == Messages.YES) {
                triggerClearLocalStorage();
            }
        });

        JBLabel infoLabel = new JBLabel("Use this button to clean the embedding store in case of database corruption.");
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.ITALIC));
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(clearButton);
        panel.add(Box.createVerticalStrut(5));
        panel.add(infoLabel);

        return panel;
    }

    public void triggerClearLocalStorage() {
        project.getMessageBus()
                .syncPublisher(StoreNotifier.TOPIC)
                .clear();
    }

    public String getChatOllamaUrl() {
        return chatOllamaUrl.getText().trim();
    }

    public void setChatOllamaUrl(String url) {
        chatOllamaUrl.setText(url.trim());
        updateAvailableModels(chatOllamaUrl, chatModel, false);
    }

    public String getCompletionOllamaUrl() {
        return completionOllamaUrl.getText().trim();
    }

    public void setCompletionOllamaUrl(String url) {
        completionOllamaUrl.setText(url.trim());
        updateAvailableModels(completionOllamaUrl, completionModel, false);
    }

    public String getEmbeddingOllamaUrl() {
        return embeddingOllamaUrl.getText().trim();
    }

    public void setEmbeddingOllamaUrl(String url) {
        embeddingOllamaUrl.setText(url.trim());
        updateAvailableModels(embeddingOllamaUrl, embeddingModel, true);
    }

    public int getMaxDocuments() {
        return maxDocuments.getValue();
    }

    public void setMaxDocuments(int maxDocumentsValue) {
        maxDocuments.setValue(maxDocumentsValue);
    }

    public String getChatModel() {
        return (String) chatModel.getSelectedItem();
    }

    public String getCompletionModel() {
        return (String) completionModel.getSelectedItem();
    }

    public String getEmbeddingModel() {
        return (String) embeddingModel.getSelectedItem();
    }

    public String getSources() {
        return sources.getText().trim();
    }

    public void setSources(String sources) {
        this.sources.setText(sources.trim());
    }

    public String getTimeout() {
        return timeout.getText().trim();
    }

    public void setTimeout(String timeout) {
        this.timeout.setText(timeout.trim());
    }

    public void setChatModelName(String chatModelName) {
        chatModel.setSelectedItem(chatModelName.trim());
    }

    public void setCompletionModelName(String completionModelName) {
        completionModel.setSelectedItem(completionModelName.trim());
    }

    public void setEmbeddingModelName(String embeddingModelName) {
        embeddingModel.setSelectedItem(embeddingModelName.trim());
    }

    private List<OllamaModel> fetchAvailableModels(JBTextField ollamaUrl) {
        try {
            return OllamaModels.builder()
                    .baseUrl(ollamaUrl.getText().isEmpty() ? DEFAULT_URL : ollamaUrl.getText())
                    .build()
                    .availableModels()
                    .content();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private void updateAvailableModels(JBTextField ollamaUrl, ComboBox<String> comboBox, boolean isEmbedding) {
        List<String> availableModels = fetchAvailableModels(ollamaUrl).stream()
                .map(OllamaModel::getName)
                .toList();

        if (isEmbedding) {
            List<String> availableModelsForEmbedding = new ArrayList<>(availableModels);
            availableModelsForEmbedding.add(DEFAULT_EMBEDDING_MODEL);
            updateComboBox(comboBox, availableModelsForEmbedding, (String) comboBox.getSelectedItem());
        } else {
            updateComboBox(comboBox, availableModels, (String) comboBox.getSelectedItem());
        }
    }


    private void updateComboBox(ComboBox<String> comboBox, List<String> items, String selectedValue) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        items.forEach(model::addElement);
        model.setSelectedItem(selectedValue);
        comboBox.setModel(model);
    }

    public void triggerCleanAllDatabase() {
        project.getMessageBus()
                .syncPublisher(StoreNotifier.TOPIC)
                .clearDatabaseAndRunIndexation();
    }

    // === Méthodes pour les composants Agent ===

    private JPanel createAgentSectionSeparator() {
        JPanel panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(5, 0));

        JBLabel sectionLabel = new JBLabel("Agent Mode Configuration");
        sectionLabel.setFont(sectionLabel.getFont().deriveFont(Font.BOLD, 14f));
        sectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(sectionLabel);

        panel.add(Box.createVerticalStrut(5));

        // Ligne de séparation
        JSeparator separator = new JSeparator();
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(separator);

        return panel;
    }

    private JPanel createAgentModeCheckbox() {
        JPanel panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(5, 0));

        agentModeEnabled.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(agentModeEnabled);

        JTextArea infoText = new JTextArea("Enable Agent Mode to allow autonomous task execution with user validation. " +
                "When enabled, action requests will be processed by the agent instead of the regular chat.");
        infoText.setEditable(false);
        infoText.setLineWrap(true);
        infoText.setWrapStyleWord(true);
        infoText.setBackground(panel.getBackground());
        infoText.setFont(infoText.getFont().deriveFont(Font.ITALIC));
        infoText.setForeground(UIManager.getColor("Label.disabledForeground"));
        infoText.setBorder(BorderFactory.createEmptyBorder());
        infoText.setFocusable(false);
        infoText.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(Box.createVerticalStrut(3));
        panel.add(infoText);

        return panel;
    }

    private JPanel createAgentAutoApprovalCheckbox() {
        JPanel panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(5, 0));

        agentAutoApprovalEnabled.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(agentAutoApprovalEnabled);

        JTextArea infoText = new JTextArea("When enabled, safe actions will be automatically approved based on the security level. " +
                "Use with caution - this reduces user control over agent actions.");
        infoText.setEditable(false);
        infoText.setLineWrap(true);
        infoText.setWrapStyleWord(true);
        infoText.setBackground(panel.getBackground());
        infoText.setFont(infoText.getFont().deriveFont(Font.ITALIC));
        infoText.setForeground(UIManager.getColor("Label.disabledForeground"));
        infoText.setBorder(BorderFactory.createEmptyBorder());
        infoText.setFocusable(false);
        infoText.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(Box.createVerticalStrut(3));
        panel.add(infoText);

        return panel;
    }

    // === Getters et setters pour les paramètres agent ===

    public boolean isAgentModeEnabled() {
        return agentModeEnabled.isSelected();
    }

    public void setAgentModeEnabled(boolean enabled) {
        agentModeEnabled.setSelected(enabled);
    }

    public AgentModeSettings.AgentSecurityLevel getAgentSecurityLevel() {
        return (AgentModeSettings.AgentSecurityLevel) agentSecurityLevel.getSelectedItem();
    }

    public void setAgentSecurityLevel(AgentModeSettings.AgentSecurityLevel level) {
        agentSecurityLevel.setSelectedItem(level);
    }

    public int getAgentMaxTasksPerSession() {
        return agentMaxTasksPerSession.getValue();
    }

    public void setAgentMaxTasksPerSession(int maxTasks) {
        agentMaxTasksPerSession.setValue(maxTasks);
    }

    public boolean isAgentAutoApprovalEnabled() {
        return agentAutoApprovalEnabled.isSelected();
    }

    public void setAgentAutoApprovalEnabled(boolean enabled) {
        agentAutoApprovalEnabled.setSelected(enabled);
    }
}