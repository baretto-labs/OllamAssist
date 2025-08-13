package fr.baretto.ollamassist.prerequiste;

import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.chat.ui.IconUtils;

import javax.swing.*;
import java.awt.*;

public class LoadingPanel extends JPanel {


    public LoadingPanel(String message) {
        this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        this.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel textLabel = new JLabel(message);
        textLabel.setFont(textLabel.getFont().deriveFont(Font.BOLD));
        textLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));

        JLabel iconLabel = new JLabel(IconUtils.LOADING);

        this.add(textLabel);
        this.add(iconLabel);
        this.setPreferredSize(JBUI.size(200, 40));
    }
}
