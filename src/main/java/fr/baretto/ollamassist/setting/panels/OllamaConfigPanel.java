package fr.baretto.ollamassist.setting.panels;

import com.intellij.openapi.application.ApplicationManager;
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

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BASIC_AUTH_FORMAT = "Basic %s";
    private static final String LATEST_TAG = ":latest";
    private static final String COLON = ":";
    private static final String LOADING_MODELS_TEXT = "Loading models...";

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

        // Load models asynchronously on panel creation
        loadAllModelsAsync();
    }

    /**
     * Loads all models asynchronously when panel is created.
     * This method handles the async execution, calling the synchronous fetch methods in background.
     */
    private void loadAllModelsAsync() {
        OllamaSettings settings = OllamaSettings.getInstance();

        loadModelsAsync(chatOllamaUrl, chatModel, settings.getChatModelName(), false);
        loadModelsAsync(completionOllamaUrl, completionModel, settings.getCompletionModelName(), false);
        loadModelsAsync(embeddingOllamaUrl, embeddingModel, settings.getEmbeddingModelName(), true);
    }

    /**
     * Loads models asynchronously for a specific ComboBox.
     * Shows loading indicator, fetches in background, updates UI on EDT.
     */
    private void loadModelsAsync(JBTextField urlField, ComboBox<String> comboBox, String configuredModel, boolean isEmbedding) {
        // Show loading indicator immediately
        SwingUtilities.invokeLater(() -> {
            DefaultComboBoxModel<String> loadingModel = new DefaultComboBoxModel<>();
            loadingModel.addElement(LOADING_MODELS_TEXT);
            comboBox.setModel(loadingModel);
        });

        // Fetch models in background thread
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String url = urlField.getText().isEmpty() ? DEFAULT_URL : urlField.getText();
            List<OllamaModel> models = fetchAvailableModels(url);

            // Update UI on EDT
            SwingUtilities.invokeLater(() -> {
                List<String> modelNames = models.stream()
                        .map(OllamaModel::getName)
                        .toList();

                List<String> allModels = new ArrayList<>(modelNames);
                if (isEmbedding && !allModels.isEmpty()) {
                    allModels.add(DEFAULT_EMBEDDING_MODEL);
                }

                updateComboBox(comboBox, allModels, configuredModel);
            });
        });
    }

    private JPanel createOllamaUrlField(String label, JBTextField ollamaUrl, String message, ComboBox<String> modelComboBox, boolean isEmbedding) {
        JPanel panel = createLabeledField(label, ollamaUrl, message);
        ollamaUrl.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                String configuredModel = getConfiguredModelForComboBox(modelComboBox);
                loadModelsAsync(ollamaUrl, modelComboBox, configuredModel, isEmbedding);
            }

            @Override
            public void focusGained(FocusEvent e) {
                ollamaUrl.setBackground(UIManager.getColor("TextField.background"));
            }
        });
        return panel;
    }

    /**
     * Gets the configured model name from settings based on which ComboBox.
     */
    private String getConfiguredModelForComboBox(ComboBox<String> comboBox) {
        OllamaSettings settings = OllamaSettings.getInstance();
        if (comboBox == chatModel) {
            return settings.getChatModelName();
        } else if (comboBox == completionModel) {
            return settings.getCompletionModelName();
        } else if (comboBox == embeddingModel) {
            return settings.getEmbeddingModelName();
        }
        return null;
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

    /**
     * Fetches available models from Ollama server synchronously.
     * This is a simple synchronous method - the async handling is done by the caller.
     */
    private List<OllamaModel> fetchAvailableModels(String url) {
        try {
            OllamaModels.OllamaModelsBuilder builder = OllamaModels.builder().baseUrl(url);

            if (AuthenticationHelper.isAuthenticationConfigured()) {
                Map<String, String> customHeaders = new HashMap<>();
                customHeaders.put(AUTHORIZATION_HEADER, String.format(BASIC_AUTH_FORMAT, AuthenticationHelper.createBasicAuthHeader()));
                builder.customHeaders(customHeaders);
            }

            return builder.build().availableModels().content();
        } catch (Exception e) {
            return Collections.emptyList();
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
    }

    public String getCompletionOllamaUrl() {
        return completionOllamaUrl.getText().trim();
    }

    public void setCompletionOllamaUrl(String url) {
        completionOllamaUrl.setText(url.trim());
    }

    public String getEmbeddingOllamaUrl() {
        return embeddingOllamaUrl.getText().trim();
    }

    public void setEmbeddingOllamaUrl(String url) {
        embeddingOllamaUrl.setText(url.trim());
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
        String selected = (String) chatModel.getSelectedItem();
        // Don't return invalid values - return configured value from settings instead
        if (isInvalidModelSelection(selected)) {
            return OllamaSettings.getInstance().getChatModelName();
        }
        return selected;
    }

    public String getCompletionModel() {
        String selected = (String) completionModel.getSelectedItem();
        // Don't return invalid values - return configured value from settings instead
        if (isInvalidModelSelection(selected)) {
            return OllamaSettings.getInstance().getCompletionModelName();
        }
        return selected;
    }

    public String getEmbeddingModel() {
        String selected = (String) embeddingModel.getSelectedItem();
        // Don't return invalid values - return configured value from settings instead
        if (isInvalidModelSelection(selected)) {
            return OllamaSettings.getInstance().getEmbeddingModelName();
        }
        return selected;
    }

    /**
     * Checks if the selected value is invalid (null, empty, or loading indicator).
     */
    private boolean isInvalidModelSelection(String selected) {
        return selected == null ||
               selected.trim().isEmpty() ||
               LOADING_MODELS_TEXT.equals(selected);
    }

    public String getTimeout() {
        return timeout.getText().trim();
    }

    public void setTimeout(String timeout) {
        this.timeout.setText(timeout.trim());
    }

    public void setChatModelName(String chatModelName) {
        if (chatModelName != null && !chatModelName.isEmpty()) {
            String trimmedName = chatModelName.trim();

            // Try exact match first
            chatModel.setSelectedItem(trimmedName);

            // If not found and name doesn't have a tag, try with :latest
            if (chatModel.getSelectedItem() == null || !chatModel.getSelectedItem().equals(trimmedName)) {
                if (!trimmedName.contains(COLON)) {
                    chatModel.setSelectedItem(trimmedName + LATEST_TAG);
                }
            }
        }
    }

    public void setCompletionModelName(String completionModelName) {
        if (completionModelName != null && !completionModelName.isEmpty()) {
            String trimmedName = completionModelName.trim();

            // Try exact match first
            completionModel.setSelectedItem(trimmedName);

            // If not found and name doesn't have a tag, try with :latest
            if (completionModel.getSelectedItem() == null || !completionModel.getSelectedItem().equals(trimmedName)) {
                if (!trimmedName.contains(COLON)) {
                    completionModel.setSelectedItem(trimmedName + LATEST_TAG);
                }
            }
        }
    }

    public void setEmbeddingModelName(String embeddingModelName) {
        if (embeddingModelName != null && !embeddingModelName.isEmpty()) {
            String trimmedName = embeddingModelName.trim();

            // Try exact match first
            embeddingModel.setSelectedItem(trimmedName);

            // If not found and name doesn't have a tag, try with :latest
            if (embeddingModel.getSelectedItem() == null || !embeddingModel.getSelectedItem().equals(trimmedName)) {
                if (!trimmedName.contains(COLON)) {
                    embeddingModel.setSelectedItem(trimmedName + LATEST_TAG);
                }
            }
        }
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
