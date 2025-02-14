package fr.baretto.ollamassist.chat.ui;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

public class LoadingPanel extends JPanel {

    public LoadingPanel(Dimension preferredSize) {
        // Suppression des bordures
        setBorder(null);

        setLayout(new BorderLayout());

        JTextPane presentationContent = new JTextPane();
        presentationContent.setText("""
                 The model is currently loading. 
                 OllamAssist will be available 
                 once the model is fully initialized and ready to use.
                 Please wait...
                """);
        presentationContent.setEditable(false);
        presentationContent.setFont(new Font("Arial", Font.PLAIN, 14));
        presentationContent.setMargin(JBUI.insets(20));

        JScrollPane scrollPane = new JBScrollPane(presentationContent);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);
    }
}