package fr.baretto.ollamassist.chat;

import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class PromptPanel extends JPanel {

    private final JTextArea textArea;

    public PromptPanel() {
        super(new BorderLayout());

        // Création du JTextArea
        textArea = new JTextArea();
        textArea.setLineWrap(true); // Active le retour à la ligne automatique
        textArea.setWrapStyleWord(true); // Coupe proprement au niveau des mots
        textArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // textArea.setFont(new Font("Arial", Font.PLAIN, 14)); // Police lisible

        // Ajout dans un JScrollPane sans scrollbars
        JScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); // Pas de scrollbar horizontale
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER); // Pas de scrollbar verticale

        // Ajout des marges au panel principal
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Ajout du JScrollPane au panel
        add(scrollPane, BorderLayout.CENTER);
    }

    // Méthode pour lier une action à l'ENTER
    public void addActionMap(final ActionListener actionListener) {
        textArea.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "sendMessage");
        textArea.getActionMap().put("sendMessage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                actionListener.actionPerformed(new ActionEvent(this, 1, null));
            }
        });
    }

    // Méthode pour vider le texte
    public void clear() {
        textArea.setText("");
    }

    // Méthode pour récupérer le texte saisi
    public String getUserPrompt() {
        return textArea.getText();
    }
}