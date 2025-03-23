package fr.baretto.ollamassist.setting;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.util.ui.JBUI;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModels;
import fr.baretto.ollamassist.component.ComponentCustomizer;
import fr.baretto.ollamassist.events.StoreNotifier;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.util.Collections;
import java.util.List;

import static fr.baretto.ollamassist.setting.OllamAssistSettings.DEFAULT_URL;

public class ConfigurationPanel extends JPanel {

    private final JBTextField ollamaUrl = new JBTextField(DEFAULT_URL);
    private final TextFieldWithAutoCompletion<String> chatModel;
    private final TextFieldWithAutoCompletion<String> completionModel;
    private final TextFieldWithAutoCompletion<String> embeddingModel;
    private final JBTextField timeout = new IntegerField(null, 0, Integer.MAX_VALUE);
    private final JBTextField sources = new JBTextField();
    private final IntegerField maxDocuments = new IntegerField(null, 1, 100000);
    private final Project project;


    public ConfigurationPanel(Project project) {
        this.project = project;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(JBUI.Borders.empty(10));

        add(createLabeledField("Ollama URL:", ollamaUrl, "The URL of the Ollama server."));
        chatModel = TextFieldWithAutoCompletion.create(project, Collections.emptyList(), true, null);
        add(createLabeledField("Chat model:", chatModel, "The model should be loaded before use."));
        completionModel = TextFieldWithAutoCompletion.create(project, Collections.emptyList(), true, null);
        add(createLabeledField("Completion model:", completionModel, "The model should be loaded before use."));
        embeddingModel = TextFieldWithAutoCompletion.create(project, Collections.emptyList(), true, null);
        add(createLabeledField("Embedding model:", embeddingModel, "Model loaded by Ollama, used for transformation into Embeddings; it must be loaded before use." +
                " For example: nomic-embed-text. " +
                "By default, the BgeSmallEnV15QuantizedEmbeddingModel embedded in the application is used."));
        add(createLabeledField("Response timeout:", timeout, "The total number of seconds allowed for a response."));
        add(createLabeledField("Indexed Folders:", sources, "Separated by ';'"));
        add(createLabeledField("Maximum number of documents indexed at once", maxDocuments, "The maximum number of documents indexed during a batch indexation"));
        add(createClearEmbeddingButton());

        ollamaUrl.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                new Thread(() -> updateAvailableModelsAutoCompletions()).start();
            }

            @Override
            public void focusGained(FocusEvent e) {
                ollamaUrl.setBackground(UIManager.getColor("TextField.background"));
            }
        });
    }

    private JPanel createLabeledField(String label, JComponent textField, String message) {
        JPanel panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(5, 0));

        JBLabel fieldLabel = new JBLabel(label);
        fieldLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(fieldLabel);

        panel.add(Box.createVerticalStrut(5));

        textField.setPreferredSize(new Dimension(200, 30));
        textField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        textField.setAlignmentX(Component.LEFT_ALIGNMENT);
        textField.setAlignmentY(Component.CENTER_ALIGNMENT);
        panel.add(textField);

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

    public String getOllamaUrl() {
        return ollamaUrl.getText().trim();
    }

    public void setOllamaUrl(String url) {
        ollamaUrl.setText(url.trim());
        updateAvailableModelsAutoCompletions();
    }

    public int getMaxDocuments() {
        return maxDocuments.getValue();
    }

    public void setMaxDocuments(int maxDocumentsValue) {
        maxDocuments.setValue(maxDocumentsValue);
    }

    public String getChatModel() {
        return chatModel.getText().trim();
    }

    public String getCompletionModel() {
        return completionModel.getText().trim();
    }

    public String getEmbeddingModel() {
        return embeddingModel.getText().trim();
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
        chatModel.setText(chatModelName.trim());
    }

    public void setCompletionModelName(String completionModelName) {
        completionModel.setText(completionModelName.trim());
    }

    public void setEmbeddingModelName(String embeddingModelName) {
        embeddingModel.setText(embeddingModelName.trim());
    }

    private List<OllamaModel> fetchAvailableModels() {
        List<OllamaModel> models = OllamaModels.builder()
                .baseUrl(ollamaUrl.getText() == null ? DEFAULT_URL : ollamaUrl.getText())
                .build()
                .availableModels()
                .content();

        models.add(OllamaModel.builder().model("").model("").build());

        return models;
    }

    private void updateAvailableModelsAutoCompletions() {
        try {
            List<OllamaModel> availableModels = fetchAvailableModels();
            chatModel.setVariants(availableModels.stream().map(OllamaModel::getName).toList());
            completionModel.setVariants(availableModels.stream().map(OllamaModel::getName).toList());
            embeddingModel.setVariants(availableModels.stream().map(OllamaModel::getName).toList());
            ollamaUrl.setForeground(UIManager.getColor("TextField.foreground"));
        } catch (RuntimeException e) {
//            Messages.showErrorDialog("Could not connect to the Ollama server. Please check the URL.", "Connection Error");
            chatModel.setVariants(Collections.emptyList());
            completionModel.setVariants(Collections.emptyList());
            embeddingModel.setVariants(Collections.emptyList());
            ollamaUrl.setForeground(
                    JBColor.RED);
        }
    }

    public void triggerCleanAllDatabase() {
        project.getMessageBus()
                .syncPublisher(StoreNotifier.TOPIC)
                .indexCorrupted();
    }
}