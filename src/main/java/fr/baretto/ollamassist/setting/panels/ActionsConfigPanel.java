package fr.baretto.ollamassist.setting.panels;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.notification.core.NotificationManager;
import fr.baretto.ollamassist.setting.ActionsSettings;

import javax.swing.*;
import java.awt.*;

public class ActionsConfigPanel extends JBPanel<ActionsConfigPanel> {

    private final JCheckBox autoApproveFileCreation = new JCheckBox("Auto-approve file creation");

    public ActionsConfigPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(JBUI.Borders.empty(10));

        autoApproveFileCreation.setSelected(ActionsSettings.getInstance().isAutoApproveFileCreation());
        autoApproveFileCreation.setToolTipText("When enabled, files created by the AI will be saved automatically without asking for confirmation");
        add(createCheckboxPanel(autoApproveFileCreation));

        // Add spacing
        add(Box.createVerticalStrut(20));

        // Add notification reset button
        add(createNotificationResetSection());
    }

    private JPanel createNotificationResetSection() {
        JBPanel<JBPanel<?>> panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Notifications (Debug)"),
                JBUI.Borders.empty(10)
        ));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel description = new JLabel("<html>Reset notification state to test update notifications again.</html>");
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        description.setBorder(JBUI.Borders.emptyBottom(10));
        panel.add(description);

        JButton resetButton = new JButton("Reset Notification State");
        resetButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetButton.addActionListener(e -> {
            NotificationManager notificationManager = ApplicationManager.getApplication()
                    .getService(NotificationManager.class);
            notificationManager.resetNotificationState();
            JOptionPane.showMessageDialog(
                    this,
                    "Notification state has been reset. Restart the IDE to see notifications again.",
                    "Reset Successful",
                    JOptionPane.INFORMATION_MESSAGE
            );
        });
        panel.add(resetButton);

        return panel;
    }

    private JPanel createCheckboxPanel(JCheckBox checkbox) {
        JBPanel<JBPanel<?>> panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(10, 0));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        checkbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(checkbox);

        return panel;
    }

    // Getters and setters
    public boolean isAutoApproveFileCreation() {
        return autoApproveFileCreation.isSelected();
    }

    public void setAutoApproveFileCreation(boolean value) {
        autoApproveFileCreation.setSelected(value);
    }

    public JCheckBox getAutoApproveFileCreationCheckbox() {
        return autoApproveFileCreation;
    }
}
