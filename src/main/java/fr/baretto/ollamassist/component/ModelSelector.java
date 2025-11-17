package fr.baretto.ollamassist.component;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.events.ChatModelModifiedNotifier;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.Setter;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import java.awt.*;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ModelSelector extends JPanel {

    private static final String ERROR_TITLE = "Loading Error";
    private static final String ERROR_MESSAGE_FORMAT = "Failed to load models: %s";
    private static final String ELLIPSIS = "...";

    private final ComboBox<String> comboBox;
    private final JProgressBar progressBar;
    private final transient Executor executor = Executors.newSingleThreadExecutor();
    @Setter
    private transient ModelLoader modelLoader;
    private boolean isLoaded = false;

    public ModelSelector() {
        setLayout(new BorderLayout());

        String savedModel = OllamAssistSettings.getInstance().getChatModelName();

        DefaultComboBoxModel<String> initialModel = new DefaultComboBoxModel<>();
        initialModel.setSelectedItem(savedModel);
        comboBox = new ComboBox<>(initialModel);
        comboBox.setPrototypeDisplayValue("Prototype_Model_Name_Length");
        comboBox.setPreferredSize(new Dimension(180, comboBox.getPreferredSize().height));
        comboBox.setMinimumSize(new Dimension(80, comboBox.getPreferredSize().height));
        comboBox.setMaximumSize(new Dimension(180, comboBox.getPreferredSize().height));
        comboBox.setRenderer(new LoadingListRenderer());
        configureComboBoxBehavior();

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setBorder(JBUI.Borders.empty(2));


        add(comboBox, BorderLayout.CENTER);
        add(progressBar, BorderLayout.SOUTH);
        activateListener();
    }

    public void activateListener() {

        comboBox.addActionListener(e -> {
            OllamAssistSettings.getInstance().setChatModelName(getSelectedModel());
            ApplicationManager.getApplication().getMessageBus().syncPublisher(ChatModelModifiedNotifier.TOPIC).onChatModelModified();
        });
    }

    public String getSelectedModel() {
        return (String) comboBox.getSelectedItem();
    }

    public void setSelectedModel(String model) {
        // Update the model directly to avoid triggering ActionListener
        DefaultComboBoxModel<String> currentModel = (DefaultComboBoxModel<String>) comboBox.getModel();
        currentModel.setSelectedItem(model);
    }

    private void configureComboBoxBehavior() {
        comboBox.addPopupMenuListener(new PopupMenuListenerAdapter() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                if (!isLoaded && modelLoader != null) {
                    startLoading();
                    executor.execute(() -> {
                        try {
                            List<String> models = modelLoader.loadModels();
                            SwingUtilities.invokeLater(() -> updateModels(models));
                        } catch (Exception ex) {
                            SwingUtilities.invokeLater(() -> showError(ex.getMessage()));
                        } finally {
                            SwingUtilities.invokeLater(() -> finishLoading());
                        }
                    });
                }
            }
        });
    }

    private void startLoading() {
        comboBox.setEnabled(false);
        progressBar.setVisible(true);
        comboBox.setPopupVisible(false);
    }

    private void updateModels(List<String> models) {
        String savedModel = OllamAssistSettings.getInstance().getChatModelName();

        models.sort(String::compareTo);
        DefaultComboBoxModel<String> comboBoxModel = new DefaultComboBoxModel<>();
        models.forEach(comboBoxModel::addElement);
        comboBox.setModel(comboBoxModel);

        models.stream()
                .filter(model -> model.startsWith(savedModel))
                .findFirst()
                .ifPresent(comboBoxModel::setSelectedItem);

        isLoaded = true;
        comboBox.setPreferredSize(new Dimension(180, comboBox.getPreferredSize().height));
        comboBox.setMinimumSize(new Dimension(80, comboBox.getPreferredSize().height));
        comboBox.setMaximumSize(new Dimension(180, comboBox.getPreferredSize().height));
    }

    private void finishLoading() {
        comboBox.setEnabled(true);
        progressBar.setVisible(false);
        if (isLoaded) {
            comboBox.setPopupVisible(true);
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this,
                String.format(ERROR_MESSAGE_FORMAT, message),
                ERROR_TITLE,
                JOptionPane.ERROR_MESSAGE
        );
    }

    public interface ModelLoader {
        List<String> loadModels();
    }

    private static class LoadingListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus
        ) {
            String displayValue = value != null ? value.toString() : "Loading...";

            FontMetrics metrics = getFontMetrics(getFont());
            int maxWidth = list.getFixedCellWidth() - 10;
            if (maxWidth > 0 && metrics.stringWidth(displayValue) > maxWidth) {
                displayValue = truncateText(displayValue, metrics, maxWidth);
            }

            return super.getListCellRendererComponent(list, displayValue, index, isSelected, cellHasFocus);
        }

        private String truncateText(String text, FontMetrics metrics, int maxWidth) {
            StringBuilder truncated = new StringBuilder(text);
            int ellipsisWidth = metrics.stringWidth("...");

            while (!truncated.isEmpty() && metrics.stringWidth(truncated.toString()) + ellipsisWidth > maxWidth) {
                truncated.deleteCharAt(truncated.length() - 1);
            }

            return truncated + ELLIPSIS;
        }
    }

}