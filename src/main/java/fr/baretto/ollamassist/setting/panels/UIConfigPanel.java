package fr.baretto.ollamassist.setting.panels;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.events.FontSettingsNotifier;
import fr.baretto.ollamassist.setting.OllamAssistUISettings;
import fr.baretto.ollamassist.utils.FontUtils;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

/**
 * Configuration panel for UI settings including font size multiplier.
 */
public class UIConfigPanel extends JBPanel<UIConfigPanel> {

    private final JSlider fontSizeSlider;
    private final JLabel multiplierLabel;
    private final JPanel previewPanel;
    private boolean isInitializing = true;

    public UIConfigPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(JBUI.Borders.empty(10));

        // Font size section
        JBPanel<?> fontSectionPanel = new JBPanel<>();
        fontSectionPanel.setLayout(new BoxLayout(fontSectionPanel, BoxLayout.Y_AXIS));
        fontSectionPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Font Size"),
                JBUI.Borders.empty(10)
        ));
        fontSectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Description label
        JLabel descriptionLabel = new JLabel("<html>" +
                "Adjust the font size multiplier for all UI components.<br/>" +
                "The multiplier is applied to the IDE's default font settings." +
                "</html>");
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        descriptionLabel.setBorder(JBUI.Borders.emptyBottom(10));
        fontSectionPanel.add(descriptionLabel);

        // Slider with label
        JPanel sliderPanel = new JPanel(new BorderLayout());
        sliderPanel.setOpaque(false);
        sliderPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sliderLabel = new JLabel("Font Size Multiplier:");
        sliderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        fontSizeSlider = new JSlider(JSlider.HORIZONTAL, 5, 20, 10); // 0.5 to 2.0 in 0.1 increments
        fontSizeSlider.setMajorTickSpacing(5);
        fontSizeSlider.setMinorTickSpacing(1);
        fontSizeSlider.setPaintTicks(true);
        fontSizeSlider.setPaintLabels(true);
        fontSizeSlider.setSnapToTicks(true);

        multiplierLabel = new JLabel("100%");
        multiplierLabel.setPreferredSize(new Dimension(50, multiplierLabel.getPreferredSize().height));

        sliderPanel.add(sliderLabel, BorderLayout.WEST);
        sliderPanel.add(fontSizeSlider, BorderLayout.CENTER);
        sliderPanel.add(multiplierLabel, BorderLayout.EAST);

        fontSectionPanel.add(sliderPanel);

        // Preview panel
        previewPanel = createPreviewPanel();
        previewPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Preview"),
                JBUI.Borders.empty(10)
        ));
        previewPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        fontSectionPanel.add(Box.createVerticalStrut(10));
        fontSectionPanel.add(previewPanel);

        add(fontSectionPanel);
        add(Box.createVerticalGlue());

        // Initialize from settings
        initializeFromSettings();

        // Add change listener
        fontSizeSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                updateMultiplierLabel();
                updatePreview();
            }
        });

        isInitializing = false;
    }

    private JPanel createPreviewPanel() {
        JBPanel<?> panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        // Title preview
        JLabel titlePreview = new JLabel("Title Text Sample");
        titlePreview.setFont(FontUtils.getTitleFont());
        titlePreview.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titlePreview);

        // Normal text preview
        JLabel normalPreview = new JLabel("Normal text sample with regular font size");
        normalPreview.setFont(FontUtils.getNormalFont());
        normalPreview.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(Box.createVerticalStrut(5));
        panel.add(normalPreview);

        // Small text preview
        JLabel smallPreview = new JLabel("Small text sample");
        smallPreview.setFont(FontUtils.getSmallFont());
        smallPreview.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(Box.createVerticalStrut(5));
        panel.add(smallPreview);

        // Code text preview
        JLabel codePreview = new JLabel("monospaced code sample");
        codePreview.setFont(FontUtils.getCodeFont());
        codePreview.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(Box.createVerticalStrut(5));
        panel.add(codePreview);

        return panel;
    }

    private void initializeFromSettings() {
        OllamAssistUISettings settings = OllamAssistUISettings.getInstance();
        float multiplier = settings.getFontSizeMultiplier();
        int sliderValue = (int) (multiplier * 10);
        fontSizeSlider.setValue(sliderValue);
        updateMultiplierLabel();
    }

    private void updateMultiplierLabel() {
        int sliderValue = fontSizeSlider.getValue();
        float multiplier = sliderValue / 10.0f;
        int percentage = (int) (multiplier * 100);
        multiplierLabel.setText(percentage + "%");
    }

    private void updatePreview() {
        FontUtils.updateMultiplier();
        for (Component comp : previewPanel.getComponents()) {
            if (comp instanceof JLabel) {
                JLabel label = (JLabel) comp;
                String text = label.getText();
                if (text.contains("Title")) {
                    label.setFont(FontUtils.getTitleFont());
                } else if (text.contains("Small")) {
                    label.setFont(FontUtils.getSmallFont());
                } else if (text.contains("monospaced")) {
                    label.setFont(FontUtils.getCodeFont());
                } else {
                    label.setFont(FontUtils.getNormalFont());
                }
            }
        }
        previewPanel.revalidate();
        previewPanel.repaint();
    }

    public void applySettings() {
        int sliderValue = fontSizeSlider.getValue();
        float multiplier = sliderValue / 10.0f;
        OllamAssistUISettings settings = OllamAssistUISettings.getInstance();
        settings.setFontSizeMultiplier(multiplier);

        // Publish font settings changed event
        ApplicationManager.getApplication()
                .getMessageBus()
                .syncPublisher(FontSettingsNotifier.TOPIC)
                .onFontSettingsChanged();
    }

    public void resetSettings() {
        initializeFromSettings();
    }
}
