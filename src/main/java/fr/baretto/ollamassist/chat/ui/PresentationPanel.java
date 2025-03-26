package fr.baretto.ollamassist.chat.ui;

import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;

public class PresentationPanel extends JPanel {

    public PresentationPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel("OllamAssist");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(titleLabel);

        mainPanel.add(Box.createVerticalStrut(10)); // Espacement

        JLabel descriptionLabel = new JLabel("This plugin allows interaction with Ollama directly within the IntelliJ IDE.");
        descriptionLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(descriptionLabel);

        mainPanel.add(Box.createVerticalStrut(10)); // Espacement

        JLabel featuresTitle = new JLabel("Features:");
        featuresTitle.setFont(new Font("Arial", Font.BOLD, 13));
        featuresTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(featuresTitle);

        JPanel featuresPanel = createFeaturesPanel();
        mainPanel.add(featuresPanel);

        JScrollPane scrollPane = new JBScrollPane(mainPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane);
    }

    private JPanel createFeaturesPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel chatFeature = new JLabel("• Chat: Chat and interact with ollama model, which can access your workspace.");
        chatFeature.setFont(new Font("Arial", Font.PLAIN, 12));
        chatFeature.setMaximumSize(new Dimension(Integer.MAX_VALUE, chatFeature.getPreferredSize().height));  // Ajuste la largeur
        panel.add(chatFeature);

        JLabel chatFeatureDetail = new JLabel("   It helps you understand code, implement methods, or write tests.");
        chatFeatureDetail.setFont(new Font("Arial", Font.PLAIN, 12));
        chatFeatureDetail.setMaximumSize(new Dimension(Integer.MAX_VALUE, chatFeatureDetail.getPreferredSize().height));  // Ajuste la largeur
        panel.add(chatFeatureDetail);

        JLabel settingFeature = new JLabel("• Settings: You can choose the model used in the settings.");
        settingFeature.setFont(new Font("Arial", Font.PLAIN, 12));
        settingFeature.setMaximumSize(new Dimension(Integer.MAX_VALUE, settingFeature.getPreferredSize().height));  // Ajuste la largeur
        panel.add(settingFeature);

        JLabel autoCompleteFeature = new JLabel("• Autocomplete (experimental): Ask OllamAssist to complete your code");
        autoCompleteFeature.setFont(new Font("Arial", Font.PLAIN, 12));
        autoCompleteFeature.setMaximumSize(new Dimension(Integer.MAX_VALUE, autoCompleteFeature.getPreferredSize().height));  // Ajuste la largeur
        panel.add(autoCompleteFeature);

        JLabel autoCompleteFeatureDetail1 = new JLabel("  by pressing Shift+Space. Press Enter to insert the suggestion");
        autoCompleteFeatureDetail1.setFont(new Font("Arial", Font.PLAIN, 12));
        autoCompleteFeatureDetail1.setMaximumSize(new Dimension(Integer.MAX_VALUE, autoCompleteFeatureDetail1.getPreferredSize().height));  // Ajuste la largeur
        panel.add(autoCompleteFeatureDetail1);

        JLabel autoCompleteFeatureDetail2 = new JLabel("  any other key will dismiss it.");
        autoCompleteFeatureDetail2.setFont(new Font("Arial", Font.PLAIN, 12));
        autoCompleteFeatureDetail2.setMaximumSize(new Dimension(Integer.MAX_VALUE, autoCompleteFeatureDetail2.getPreferredSize().height));  // Ajuste la largeur
        panel.add(autoCompleteFeatureDetail2);

        return panel;
    }
}