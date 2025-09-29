package fr.baretto.ollamassist.chat.ui;

import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class PresentationPanel extends JBPanel<PresentationPanel> {

    public PresentationPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.CENTER_ALIGNMENT);
        setBackground(UIUtil.getPanelBackground());

        JBPanel<?> mainPanel = new JBPanel<>();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(JBUI.Borders.empty(10));
        mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.setBackground(UIUtil.getPanelBackground());

        JLabel titleLabel = new JLabel("OllamAssist");
        titleLabel.setFont(JBUI.Fonts.label(16).asBold());
        titleLabel.setForeground(UIUtil.getLabelForeground());
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(titleLabel);

        mainPanel.add(Box.createVerticalStrut(JBUI.scale(10)));

        JLabel descriptionLabel = new JLabel("This plugin allows interaction with Ollama directly within the IntelliJ IDE.");
        descriptionLabel.setFont(JBUI.Fonts.label(12));
        descriptionLabel.setForeground(UIUtil.getLabelForeground());
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(descriptionLabel);

        mainPanel.add(Box.createVerticalStrut(JBUI.scale(10)));

        JLabel featuresTitle = new JLabel("Features:");
        featuresTitle.setFont(JBUI.Fonts.label(13).asBold());
        featuresTitle.setForeground(UIUtil.getLabelForeground());
        featuresTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(featuresTitle);

        JBPanel<?> featuresPanel = createFeaturesPanel();
        mainPanel.add(featuresPanel);

        JBScrollPane scrollPane = new JBScrollPane(mainPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(JBUI.Borders.empty());
        add(scrollPane);
    }

    private JBPanel<?> createFeaturesPanel() {
        JBPanel<?> panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBackground(UIUtil.getPanelBackground());

        JLabel chatFeature = new JLabel("• Chat: Chat and interact with ollama model, which can access your workspace.");
        chatFeature.setFont(JBUI.Fonts.label(12));
        chatFeature.setForeground(UIUtil.getLabelForeground());
        chatFeature.setMaximumSize(new Dimension(Integer.MAX_VALUE, chatFeature.getPreferredSize().height));
        panel.add(chatFeature);

        JLabel chatFeatureDetail = new JLabel("   It helps you understand code, implement methods, or write tests.");
        chatFeatureDetail.setFont(JBUI.Fonts.label(12));
        chatFeatureDetail.setForeground(UIUtil.getLabelForeground());
        chatFeatureDetail.setMaximumSize(new Dimension(Integer.MAX_VALUE, chatFeatureDetail.getPreferredSize().height));
        panel.add(chatFeatureDetail);

        JLabel settingFeature = new JLabel("• Settings: You can choose the model used in the settings.");
        settingFeature.setFont(JBUI.Fonts.label(12));
        settingFeature.setForeground(UIUtil.getLabelForeground());
        settingFeature.setMaximumSize(new Dimension(Integer.MAX_VALUE, settingFeature.getPreferredSize().height));
        panel.add(settingFeature);

        JLabel autoCompleteFeature = new JLabel("• Autocomplete (experimental): Ask OllamAssist to complete your code");
        autoCompleteFeature.setFont(JBUI.Fonts.label(12));
        autoCompleteFeature.setForeground(UIUtil.getLabelForeground());
        autoCompleteFeature.setMaximumSize(new Dimension(Integer.MAX_VALUE, autoCompleteFeature.getPreferredSize().height));
        panel.add(autoCompleteFeature);

        JLabel autoCompleteFeatureDetail1 = new JLabel("  by pressing Shift+Space. Press Enter to insert the suggestion");
        autoCompleteFeatureDetail1.setFont(JBUI.Fonts.label(12));
        autoCompleteFeatureDetail1.setForeground(UIUtil.getLabelForeground());
        autoCompleteFeatureDetail1.setMaximumSize(new Dimension(Integer.MAX_VALUE, autoCompleteFeatureDetail1.getPreferredSize().height));
        panel.add(autoCompleteFeatureDetail1);

        JLabel autoCompleteFeatureDetail2 = new JLabel("  any other key will dismiss it.");
        autoCompleteFeatureDetail2.setFont(JBUI.Fonts.label(12));
        autoCompleteFeatureDetail2.setForeground(UIUtil.getLabelForeground());
        autoCompleteFeatureDetail2.setMaximumSize(new Dimension(Integer.MAX_VALUE, autoCompleteFeatureDetail2.getPreferredSize().height));
        panel.add(autoCompleteFeatureDetail2);

        return panel;
    }
}