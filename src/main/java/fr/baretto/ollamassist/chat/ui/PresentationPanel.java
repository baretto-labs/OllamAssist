package fr.baretto.ollamassist.chat.ui;

import com.intellij.ui.components.JBScrollPane;
import fr.baretto.ollamassist.utils.FontUtils;

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
        titleLabel.setFont(FontUtils.getTitleFont());
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(titleLabel);

        mainPanel.add(Box.createVerticalStrut(10)); // Espacement

        JLabel descriptionLabel = new JLabel("This plugin allows interaction with Ollama directly within the IntelliJ IDE.");
        descriptionLabel.setFont(FontUtils.getNormalFont());
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(descriptionLabel);

        mainPanel.add(Box.createVerticalStrut(10)); // Espacement

        JLabel featuresTitle = new JLabel("Features:");
        featuresTitle.setFont(FontUtils.getSubtitleFont());
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
        chatFeature.setFont(FontUtils.getNormalFont());
        chatFeature.setMaximumSize(new Dimension(Integer.MAX_VALUE, chatFeature.getPreferredSize().height));
        panel.add(chatFeature);

        JLabel chatFeatureDetail = new JLabel("   It helps you understand code, implement methods, or write tests.");
        chatFeatureDetail.setFont(FontUtils.getNormalFont());
        chatFeatureDetail.setMaximumSize(new Dimension(Integer.MAX_VALUE, chatFeatureDetail.getPreferredSize().height));
        panel.add(chatFeatureDetail);

        JLabel settingFeature = new JLabel("• Settings: You can choose the model used in the settings.");
        settingFeature.setFont(FontUtils.getNormalFont());
        settingFeature.setMaximumSize(new Dimension(Integer.MAX_VALUE, settingFeature.getPreferredSize().height));
        panel.add(settingFeature);

        JLabel autoCompleteFeature = new JLabel("• Autocomplete (experimental): Ask OllamAssist to complete your code");
        autoCompleteFeature.setFont(FontUtils.getNormalFont());
        autoCompleteFeature.setMaximumSize(new Dimension(Integer.MAX_VALUE, autoCompleteFeature.getPreferredSize().height));
        panel.add(autoCompleteFeature);

        JLabel autoCompleteFeatureDetail1 = new JLabel("  by pressing Shift+Space. Press Enter to insert the suggestion");
        autoCompleteFeatureDetail1.setFont(FontUtils.getNormalFont());
        autoCompleteFeatureDetail1.setMaximumSize(new Dimension(Integer.MAX_VALUE, autoCompleteFeatureDetail1.getPreferredSize().height));
        panel.add(autoCompleteFeatureDetail1);

        JLabel autoCompleteFeatureDetail2 = new JLabel("  any other key will dismiss it.");
        autoCompleteFeatureDetail2.setFont(FontUtils.getNormalFont());
        autoCompleteFeatureDetail2.setMaximumSize(new Dimension(Integer.MAX_VALUE, autoCompleteFeatureDetail2.getPreferredSize().height));
        panel.add(autoCompleteFeatureDetail2);

        return panel;
    }
}