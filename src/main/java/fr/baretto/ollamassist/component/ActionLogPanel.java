package fr.baretto.ollamassist.component;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

public class ActionLogPanel extends JPanel {

    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> logList = new JList<>(model);
    private final JScrollPane scrollPane;
    private final JPanel container = new JPanel(new BorderLayout());
    private boolean collapsed = false;
    private final int maxEntries = 100;

    public ActionLogPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        // Header avec bouton collapse et label
        JButton toggleButton = new JButton("▼");
        toggleButton.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
        toggleButton.setFocusPainted(false);
        toggleButton.setContentAreaFilled(false);

        JLabel headerLabel = new JLabel("Actions");

        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        headerPanel.add(toggleButton);
        headerPanel.add(headerLabel);

        add(headerPanel, BorderLayout.NORTH);

        logList.setCellRenderer(new LogCellRenderer());
        scrollPane = new JBScrollPane(logList);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);


        scrollPane.setPreferredSize(new Dimension(400, 40));
        scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        add(scrollPane, BorderLayout.CENTER);

        toggleButton.addActionListener(e -> {
            boolean visible = scrollPane.isVisible();
            scrollPane.setVisible(!visible);
            toggleButton.setText(visible ? "►" : "▼");
            revalidate();
            repaint();
        });
    }

    private void updateCollapsedState() {
        container.setVisible(!collapsed);
        revalidate();
        repaint();
    }

    public void addAction(String message) {
        SwingUtilities.invokeLater(() -> {
            if (model.size() >= maxEntries) model.remove(0);
            model.addElement(message);
            logList.ensureIndexIsVisible(model.size() - 1);
        });
    }

    private static class LogCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(JBUI.Borders.empty(1, 4));
            label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));

            String text = value.toString().toLowerCase();
            if (text.contains("error")) {
                label.setForeground(Color.RED.darker());
                label.setIcon(UIManager.getIcon("OptionPane.errorIcon"));
            } else if (text.contains("warning")) {
                label.setForeground(new JBColor(new Color(210, 105, 30), new Color(210, 105, 30)));
                label.setIcon(UIManager.getIcon("OptionPane.warningIcon"));
            } else {
                label.setForeground(JBColor.DARK_GRAY);
                label.setIcon(UIManager.getIcon("OptionPane.informationIcon"));
            }

            return label;
        }
    }
}