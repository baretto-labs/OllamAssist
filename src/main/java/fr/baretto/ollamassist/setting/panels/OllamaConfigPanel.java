package fr.baretto.ollamassist.setting.panels;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.util.ui.JBUI;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModels;
import fr.baretto.ollamassist.auth.AuthenticationHelper;
import fr.baretto.ollamassist.setting.OllamaSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.util.*;
import java.util.List;

import static fr.baretto.ollamassist.chat.rag.RAGConstants.DEFAULT_EMBEDDING_MODEL;
import static fr.baretto.ollamassist.setting.OllamaSettings.DEFAULT_URL;

public class OllamaConfigPanel extends JBPanel<OllamaConfigPanel> {

    private final JBTextField chatOllamaUrl = new JBTextField(OllamaSettings.getInstance().getChatOllamaUrl());
    private final JBTextField completionOllamaUrl = new JBTextField(OllamaSettings.getInstance().getCompletionOllamaUrl());
    private final JBTextField embeddingOllamaUrl = new JBTextField(OllamaSettings.getInstance().getEmbeddingOllamaUrl());
    private final JBTextField username = new JBTextField(OllamaSettings.getInstance().getUsername());
    private final JBTextField password = new JBTextField(OllamaSettings.getInstance().getPassword());
    private final ComboBox<String> chatModel;
    private final ComboBox<String> completionModel;
    private final ComboBox<String> embeddingModel;
    private final JBTextField timeout = new IntegerField(null, 0, Integer.MAX_VALUE);

    public OllamaConfigPanel(Project project) {
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

        add(createLabeledField("Username:", username, "Username for basic authentication (optional)."));
        add(createLabeledField("Password:", password, "Password for basic authentication (optional)."));

        add(createLabeledField("Response timeout:", timeout, "The total number of seconds allowed for a response."));
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

    private List<OllamaModel> fetchAvailableModels(JBTextField ollamaUrl) {
        try {
            OllamaModels.OllamaModelsBuilder builder = OllamaModels.builder()
                    .baseUrl(ollamaUrl.getText().isEmpty() ? DEFAULT_URL : ollamaUrl.getText());

            if (AuthenticationHelper.isAuthenticationConfigured()) {
                Map<String, String> customHeaders = new HashMap<>();
                customHeaders.put("Authorization", "Basic " + AuthenticationHelper.createBasicAuthHeader());
                builder.customHeaders(customHeaders);
            }

            return builder.build()
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

    // Getters for ConfigurationPanel compatibility
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

    public String getUsername() {
        return username.getText().trim();
    }

    public void setUsername(String usernameValue) {
        username.setText(usernameValue.trim());
    }

    public String getPassword() {
        return password.getText().trim();
    }

    public void setPassword(String passwordValue) {
        password.setText(passwordValue.trim());
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

    public JBTextField getChatOllamaUrlField() {
        return chatOllamaUrl;
    }

    public JBTextField getCompletionOllamaUrlField() {
        return completionOllamaUrl;
    }

    public JBTextField getEmbeddingOllamaUrlField() {
        return embeddingOllamaUrl;
    }

    public JBTextField getUsernameField() {
        return username;
    }

    public JBTextField getPasswordField() {
        return password;
    }

    public JBTextField getTimeoutField() {
        return timeout;
    }

    public ComboBox<String> getChatModelComboBox() {
        return chatModel;
    }

    public ComboBox<String> getCompletionModelComboBox() {
        return completionModel;
    }

    public ComboBox<String> getEmbeddingModelComboBox() {
        return embeddingModel;
    }
}
