package fr.baretto.ollamassist.component;

import com.intellij.util.ui.JBUI;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ComponentCustomizer {

    public static void applyHoverEffect(final JButton button) {
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(JBUI.CurrentTheme.Link.Foreground.ENABLED);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(UIManager.getColor("Button.background"));
            }
        });
        button.setOpaque(true);
    }
}
