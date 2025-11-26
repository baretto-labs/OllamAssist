package fr.baretto.ollamassist.setting.panels;

import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.setting.PromptSettings;

import javax.swing.*;
import java.awt.*;

public class PromptConfigPanel extends JBPanel<PromptConfigPanel> {

    private final JTextArea chatSystemPromptArea;
    private final JTextArea refactorUserPromptArea;
    private final JButton resetChatPromptButton;
    private final JButton resetRefactorPromptButton;
    private final JButton resetAllPromptsButton;

    public PromptConfigPanel() {
        setLayout(new BorderLayout());
        setBorder(JBUI.Borders.empty(10));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // Chat System Prompt section
        mainPanel.add(createPromptSection(
                "Chat System Prompt",
                "This prompt defines the behavior and personality of the AI assistant in the chat window.",
                chatSystemPromptArea = createTextArea(),
                resetChatPromptButton = createResetButton("Reset Chat Prompt", () -> {
                    chatSystemPromptArea.setText(PromptSettings.DEFAULT_CHAT_SYSTEM_PROMPT);
                })
        ));

        mainPanel.add(Box.createVerticalStrut(20));

        // Refactor User Prompt section
        mainPanel.add(createPromptSection(
                "Refactor User Prompt",
                "This prompt is used when requesting code refactoring. Variables: {{code}}, {{language}}",
                refactorUserPromptArea = createTextArea(),
                resetRefactorPromptButton = createResetButton("Reset Refactor Prompt", () -> {
                    refactorUserPromptArea.setText(PromptSettings.DEFAULT_REFACTOR_USER_PROMPT);
                })
        ));

        mainPanel.add(Box.createVerticalStrut(20));

        // Reset All button
        JPanel resetAllPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        resetAllPromptsButton = new JButton("Reset All Prompts to Default");
        resetAllPromptsButton.addActionListener(e -> resetAllPrompts());
        resetAllPanel.add(resetAllPromptsButton);
        mainPanel.add(resetAllPanel);

        // Load current settings
        loadSettings();

        JBScrollPane scrollPane = new JBScrollPane(mainPanel);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createPromptSection(String title, String description, JTextArea textArea, JButton resetButton) {
        JBPanel<JBPanel<?>> section = new JBPanel<>();
        section.setLayout(new BorderLayout());
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                JBUI.Borders.empty(10)
        ));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Description
        JLabel descLabel = new JLabel("<html>" + description + "</html>");
        descLabel.setBorder(JBUI.Borders.emptyBottom(10));
        section.add(descLabel, BorderLayout.NORTH);

        // Text area with scroll
        JBScrollPane textScrollPane = new JBScrollPane(textArea);
        textScrollPane.setPreferredSize(new Dimension(600, 200));
        section.add(textScrollPane, BorderLayout.CENTER);

        // Reset button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(resetButton);
        section.add(buttonPanel, BorderLayout.SOUTH);

        return section;
    }

    private JTextArea createTextArea() {
        JTextArea textArea = new JTextArea();
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setTabSize(4);
        return textArea;
    }

    private JButton createResetButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> action.run());
        return button;
    }

    private void loadSettings() {
        PromptSettings settings = PromptSettings.getInstance();
        chatSystemPromptArea.setText(settings.getChatSystemPrompt());
        refactorUserPromptArea.setText(settings.getRefactorUserPrompt());
    }

    private void resetAllPrompts() {
        chatSystemPromptArea.setText(PromptSettings.DEFAULT_CHAT_SYSTEM_PROMPT);
        refactorUserPromptArea.setText(PromptSettings.DEFAULT_REFACTOR_USER_PROMPT);
    }

    // Getters
    public String getChatSystemPrompt() {
        return chatSystemPromptArea.getText().trim();
    }

    public void setChatSystemPrompt(String prompt) {
        chatSystemPromptArea.setText(prompt);
    }

    public String getRefactorUserPrompt() {
        return refactorUserPromptArea.getText().trim();
    }

    public void setRefactorUserPrompt(String prompt) {
        refactorUserPromptArea.setText(prompt);
    }

    public JTextArea getChatSystemPromptArea() {
        return chatSystemPromptArea;
    }

    public JTextArea getRefactorUserPromptArea() {
        return refactorUserPromptArea;
    }

    /**
     * Validates that prompts are not empty or null
     * @return true if all prompts are valid, false otherwise
     */
    public boolean validatePrompts() {
        String chatPrompt = getChatSystemPrompt();
        String refactorPrompt = getRefactorUserPrompt();

        if (chatPrompt == null || chatPrompt.trim().isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Chat System Prompt cannot be empty.",
                    "Validation Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return false;
        }

        if (refactorPrompt == null || refactorPrompt.trim().isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Refactor User Prompt cannot be empty.",
                    "Validation Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return false;
        }

        return true;
    }
}