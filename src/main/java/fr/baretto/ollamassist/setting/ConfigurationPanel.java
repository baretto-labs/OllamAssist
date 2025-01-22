package fr.baretto.ollamassist.setting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.events.StoreNotifier;

import javax.swing.*;
import java.awt.*;

public class ConfigurationPanel extends JPanel {

    private final JBTextField chatModel = new JBTextField();
    private final JBTextField completionModel = new JBTextField();
    private final JBTextField sources = new JBTextField();


    public ConfigurationPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(JBUI.Borders.empty(10)); // Ajouter des marges globales

        add(createLabeledField("Chat model:", chatModel, "The model should be loaded before use."));

        add(createLabeledField("Completion model:", completionModel, "The model should be loaded before use."));

        add(createLabeledField("Indexed Folders:", sources, "Separated by ';'"));

        add(createClearEmbeddingButton());
    }

    private JPanel createLabeledField(String label, JBTextField textField, String message) {
        // Conteneur principal vertical pour le champ et le message
        JPanel panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); // Alignement vertical
        panel.setBorder(JBUI.Borders.empty(5, 0)); // Marges globales pour la section

        // Label principal
        JBLabel fieldLabel = new JBLabel(label);
        fieldLabel.setAlignmentX(Component.LEFT_ALIGNMENT); // Alignement à gauche
        panel.add(fieldLabel);

        panel.add(Box.createVerticalStrut(5)); // Espacement entre le label et le champ de texte

        textField.setPreferredSize(new Dimension(200, 24));
        textField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        textField.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(textField);

        if (message != null) {
            JBLabel infoLabel = new JBLabel(message);
            infoLabel.setFont(infoLabel.getFont().deriveFont(Font.ITALIC));
            infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(Box.createVerticalStrut(3));
            panel.add(infoLabel);
        }

        return panel;
    }

    private JPanel createClearEmbeddingButton() {
        JPanel panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(10, 0));
        JButton clearButton = new JButton("Clear Embedding Store");
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
                sources.setText("");
                ApplicationManager.getApplication()
                        .getMessageBus()
                        .syncPublisher(StoreNotifier.TOPIC)
                        .clearEmbeddingStore();
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

    public String getChatModel() {
        return chatModel.getText().trim();
    }

    public String getCompletionModel() {
        return completionModel.getText().trim();
    }

    public String getSources() {
        return sources.getText().trim();
    }

    public void setSources(String sources) {
        this.sources.setText(sources.trim());
    }

    public void setChatModelName(String chatModelName) {
        chatModel.setText(chatModelName.trim());
    }

    public void setCompletionModelName(String completionModelName) {
        completionModel.setText(completionModelName.trim());
    }

}