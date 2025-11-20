package fr.baretto.ollamassist.notification.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import fr.baretto.ollamassist.notification.core.Notification;
import fr.baretto.ollamassist.notification.core.NotificationManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Dialog that displays notifications in a scrollable vertical layout.
 * Allows users to dismiss individual notifications or close to see all.
 */
public class NotificationDialog extends DialogWrapper {

    private final List<Notification> notifications;
    private final NotificationManager notificationManager;

    public NotificationDialog(Project project, List<Notification> notifications) {
        super(project);
        this.notifications = notifications;
        this.notificationManager = ApplicationManager.getApplication().getService(NotificationManager.class);

        setTitle("OllamAssist Updates");
        setModal(true);
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(5));

        // Header
        if (notifications.size() > 1) {
            JLabel headerLabel = new JLabel(
                    String.format("<html><div style='padding: 5px;'><b>%d updates since your last version</b></div></html>",
                            notifications.size())
            );
            headerLabel.setBorder(JBUI.Borders.empty(5, 10, 10, 10));
            mainPanel.add(headerLabel, BorderLayout.NORTH);
        }

        // Content panel with notifications
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(JBUI.Borders.empty(5));
        contentPanel.setBackground(UIUtil.getPanelBackground());

        // Add each notification as a card
        for (int i = 0; i < notifications.size(); i++) {
            Notification notification = notifications.get(i);
            JPanel notificationCard = createNotificationCard(notification);
            notificationCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, notificationCard.getPreferredSize().height));
            contentPanel.add(notificationCard);

            if (i < notifications.size() - 1) {
                contentPanel.add(Box.createVerticalStrut(10));
            }
        }

        // Wrap in scroll pane with better sizing
        JBScrollPane scrollPane = new JBScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.setPreferredSize(new Dimension(650, 450));

        return mainPanel;
    }

    private JPanel createNotificationCard(Notification notification) {
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                JBUI.Borders.empty(12)
        ));
        card.setBackground(UIUtil.getPanelBackground());

        // Icon + Type indicator
        JLabel iconLabel = new JLabel(getIconForType(notification.getType()));
        iconLabel.setFont(iconLabel.getFont().deriveFont(22f));
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        iconLabel.setBorder(JBUI.Borders.emptyTop(2));
        card.add(iconLabel, BorderLayout.WEST);

        // Content panel
        JPanel contentPanel = new JPanel(new BorderLayout(0, 8));
        contentPanel.setOpaque(false);

        // Title with version
        JLabel titleLabel = new JLabel(
                String.format("<html><b>%s</b> <span style='color: gray; font-size: 90%%;'>(v%s)</span></html>",
                        notification.getTitle(),
                        notification.getVersion())
        );
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        contentPanel.add(titleLabel, BorderLayout.NORTH);

        // Message - with proper wrapping
        JEditorPane messagePane = new JEditorPane("text/html", notification.getMessage());
        messagePane.setEditable(false);
        messagePane.setOpaque(false);
        messagePane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        messagePane.setFont(UIUtil.getLabelFont());

        // Disable hyperlink handling to avoid issues
        messagePane.addHyperlinkListener(null);

        contentPanel.add(messagePane, BorderLayout.CENTER);

        // "Don't show again" button if dismissible
        if (notification.isDismissible()) {
            JButton dismissButton = new JButton("Don't show again");
            dismissButton.setFont(dismissButton.getFont().deriveFont(10f));
            dismissButton.putClientProperty("JButton.buttonType", "borderless");
            dismissButton.addActionListener(e -> {
                notificationManager.markAsRead(notification.getId());
                dismissButton.setEnabled(false);
                dismissButton.setText("✓ Dismissed");
            });

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            buttonPanel.setOpaque(false);
            buttonPanel.add(dismissButton);
            buttonPanel.setBorder(JBUI.Borders.emptyTop(5));
            contentPanel.add(buttonPanel, BorderLayout.SOUTH);
        }

        card.add(contentPanel, BorderLayout.CENTER);

        return card;
    }

    private String getIconForType(Notification.NotificationType type) {
        return switch (type) {
            case INFO -> "💡";
            case WARNING -> "⚠️";
            case FEATURE -> "🎉";
            case BREAKING_CHANGE -> "🔴";
        };
    }

    @Override
    protected void doOKAction() {
        // Mark all displayed notifications as read when closing
        for (Notification notification : notifications) {
            notificationManager.markAsRead(notification.getId());
        }

        // Update last notified version only after user acknowledges the dialog
        notificationManager.updateLastNotifiedVersion();

        super.doOKAction();
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{getOKAction()};
    }

    @Override
    protected String getDimensionServiceKey() {
        return "OllamAssist.NotificationDialog";
    }
}
