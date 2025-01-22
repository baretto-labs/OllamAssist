package fr.baretto.ollamassist.chat;

import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class UserMessage extends JPanel {
    private final JTextArea textArea;

    public UserMessage(String userMessage) {
        setLayout(new BorderLayout());
        setBackground(UIManager.getColor("Button.background").brighter());
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(createHeaderLabel(), BorderLayout.EAST);
        // headerPanel.add(createDeleteButton(), BorderLayout.WEST);

        textArea = new JTextArea(userMessage);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        add(headerPanel, BorderLayout.NORTH);
        add(textArea, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        Dimension fixedDimension = new Dimension(Integer.MAX_VALUE, getPreferredSize().height);

        setMinimumSize(fixedDimension);
        setMaximumSize(fixedDimension);
    }

    private @NotNull JBLabel createHeaderLabel() {
        JBLabel header = new JBLabel("User", ImageUtil.USER_ICON, SwingConstants.LEFT);
        header.setFont(header.getFont().deriveFont(10f));
        header.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        return header;
    }


    private @NotNull JButton createDeleteButton() {
        JButton deleteButton = new JButton();
        deleteButton.setToolTipText("Remove the prompt & response");

        return deleteButton;
    }
}
