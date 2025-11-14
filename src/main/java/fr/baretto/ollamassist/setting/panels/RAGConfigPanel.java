package fr.baretto.ollamassist.setting.panels;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.component.ComponentCustomizer;
import fr.baretto.ollamassist.events.StoreNotifier;

import javax.swing.*;
import java.awt.*;

public class RAGConfigPanel extends JBPanel<RAGConfigPanel> {

    private final JBTextField sources = new JBTextField();
    private final IntegerField maxDocuments = new IntegerField(null, 1, 100000);
    private final transient Project project;

    public RAGConfigPanel(Project project) {
        this.project = project;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(JBUI.Borders.empty(10));

        add(createLabeledField("Indexed Folders:", sources, "Separated by ';'"));
        add(createLabeledField("Maximum number of documents indexed at once", maxDocuments,
                "The maximum number of documents indexed during a batch indexation"));

        add(createClearEmbeddingButton());
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

    public void triggerCleanAllDatabase() {
        project.getMessageBus()
                .syncPublisher(StoreNotifier.TOPIC)
                .clearDatabaseAndRunIndexation();
    }

    // Getters and setters
    public String getSources() {
        return sources.getText().trim();
    }

    public void setSources(String sources) {
        this.sources.setText(sources.trim());
    }

    public int getMaxDocuments() {
        return maxDocuments.getValue();
    }

    public void setMaxDocuments(int maxDocumentsValue) {
        maxDocuments.setValue(maxDocumentsValue);
    }

    public JBTextField getSourcesField() {
        return sources;
    }

    public IntegerField getMaxDocumentsField() {
        return maxDocuments;
    }
}
