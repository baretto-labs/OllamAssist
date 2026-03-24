package fr.baretto.ollamassist.chat.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.component.ComponentCustomizer;
import fr.baretto.ollamassist.conversation.Conversation;
import fr.baretto.ollamassist.conversation.ConversationService;
import fr.baretto.ollamassist.events.ConversationSwitchedNotifier;
import fr.baretto.ollamassist.events.NewUserMessageNotifier;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.*;
import java.util.List;

public class ConversationManagerPanel extends JPanel {

    private final Project project;
    private final JComboBox<Conversation> conversationCombo;
    private boolean isRefreshing = false;

    public ConversationManagerPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.conversationCombo = new JComboBox<>();

        setupComboBox();
        setupButtons();
        subscribeToEvents();
        refresh();
    }

    private void setupComboBox() {
        conversationCombo.setRenderer(new ConversationListCellRenderer());
        conversationCombo.setMaximumRowCount(10);
        conversationCombo.addActionListener(e -> {
            if (isRefreshing) return;
            Conversation selected = (Conversation) conversationCombo.getSelectedItem();
            if (selected == null) return;
            ConversationService service = project.getService(ConversationService.class);
            if (selected == service.getActiveConversation()) return;
            service.setActiveConversation(selected);
            project.getMessageBus()
                    .syncPublisher(ConversationSwitchedNotifier.TOPIC)
                    .conversationSwitched(selected);
        });
    }

    private void setupButtons() {
        JButton newButton = createIconButton(IconUtils.NEW_CONVERSATION, "New conversation");
        newButton.addActionListener(e -> {
            ConversationService service = project.getService(ConversationService.class);
            Conversation created = service.createConversation();
            service.setActiveConversation(created);
            refresh();
            project.getMessageBus()
                    .syncPublisher(ConversationSwitchedNotifier.TOPIC)
                    .conversationSwitched(created);
        });

        JButton deleteButton = createIconButton(IconUtils.DELETE_CONVERSATION, "Delete current conversation");
        deleteButton.addActionListener(e -> deleteActiveConversation());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(newButton);
        buttonPanel.add(deleteButton);

        add(conversationCombo, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.EAST);
        setBorder(JBUI.Borders.empty(2, 6, 2, 2));
    }

    private JButton createIconButton(Icon icon, String tooltip) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(22, 22));
        button.setMargin(JBUI.insets(2));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        ComponentCustomizer.applyHoverEffect(button);
        return button;
    }

    private void deleteActiveConversation() {
        ConversationService service = project.getService(ConversationService.class);
        Conversation active = service.getActiveConversation();
        int confirm = Messages.showYesNoDialog(
                project,
                "Delete conversation \"" + active.getTitle() + "\"?",
                "Delete Conversation",
                Messages.getQuestionIcon());
        if (confirm != Messages.YES) return;
        service.delete(active);
        Conversation newActive = service.getActiveConversation();
        refresh();
        project.getMessageBus()
                .syncPublisher(ConversationSwitchedNotifier.TOPIC)
                .conversationSwitched(newActive);
    }

    private void subscribeToEvents() {
        project.getMessageBus()
                .connect()
                .subscribe(NewUserMessageNotifier.TOPIC, (NewUserMessageNotifier) message ->
                        SwingUtilities.invokeLater(this::refreshComboItems));
    }

    public void refresh() {
        SwingUtilities.invokeLater(this::refreshComboItems);
    }

    private void refreshComboItems() {
        isRefreshing = true;
        try {
            ConversationService service = project.getService(ConversationService.class);
            List<Conversation> all = service.getAllConversations();
            DefaultComboBoxModel<Conversation> model = new DefaultComboBoxModel<>();
            for (Conversation c : all) {
                model.addElement(c);
            }
            conversationCombo.setModel(model);
            conversationCombo.setSelectedItem(service.getActiveConversation());
        } finally {
            isRefreshing = false;
        }
    }

    private static class ConversationListCellRenderer extends BasicComboBoxRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Conversation conversation) {
                setText(conversation.getTitle());
                setToolTipText(conversation.getTitle());
            }
            setForeground(isSelected ? list.getSelectionForeground() : JBColor.foreground());
            return this;
        }
    }
}
