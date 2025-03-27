package fr.baretto.ollamassist.chat.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.component.ComponentCustomizer;
import fr.baretto.ollamassist.events.ConversationNotifier;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ConversationSelectorPanel extends JPanel {

    private static final String OLLAM_ASSIST_HISTORIC = "Conversation";
    private static final String NEW_CONVERSATION = "Clear current conversation";

    public ConversationSelectorPanel() {
        super(new BorderLayout());

        JLabel titleLabel = new JLabel(OLLAM_ASSIST_HISTORIC);

        JButton newConversation = new JButton();
        ComponentCustomizer.applyHoverEffect(newConversation);
        newConversation.setPreferredSize(new Dimension(16, 16));
        newConversation.setMargin(JBUI.insets(2));
        newConversation.setFocusPainted(false);
        newConversation.setBorderPainted(false);
        newConversation.setContentAreaFilled(false);

        newConversation.setIcon(IconUtils.DELETE_CONVERSATION);

        newConversation.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                ApplicationManager.getApplication()
                        .getMessageBus()
                        .syncPublisher(ConversationNotifier.TOPIC)
                        .newConversation();
            }
        });

        newConversation.setToolTipText(NEW_CONVERSATION);

        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 12, 0, 0);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        topPanel.add(titleLabel, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        topPanel.add(newConversation, gbc);

        this.add(topPanel, BorderLayout.NORTH);
    }
}