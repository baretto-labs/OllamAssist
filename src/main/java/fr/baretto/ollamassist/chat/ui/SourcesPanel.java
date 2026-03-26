package fr.baretto.ollamassist.chat.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.chat.rag.RagSource;
import fr.baretto.ollamassist.utils.FontUtils;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Collapsible panel displayed below an AI message listing the RAG chunks used to generate it.
 *
 * <p>Starts collapsed ("► N sources"). Clicking the header toggles the list.
 * Clicking a source row opens the corresponding file in the editor.
 */
public class SourcesPanel extends JPanel {

    private static final String COLLAPSED_PREFIX = "► ";
    private static final String EXPANDED_PREFIX  = "▼ ";

    private final JPanel contentPanel = new JPanel();
    private final JButton toggleButton = new JButton();
    private boolean collapsed = true;

    public SourcesPanel(List<RagSource> sources, Project project) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
                JBUI.Borders.empty(2, 8, 4, 8)
        ));
        setAlignmentX(LEFT_ALIGNMENT);

        buildToggleButton(sources.size());
        buildContentPanel(sources, project);

        add(buildHeaderRow());
        add(contentPanel);
    }

    private JPanel buildHeaderRow() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.add(toggleButton);
        header.add(Box.createHorizontalGlue());
        return header;
    }

    private void buildToggleButton(int count) {
        toggleButton.setText(COLLAPSED_PREFIX + count + " source" + (count > 1 ? "s" : ""));
        toggleButton.setFont(FontUtils.getSmallFont());
        toggleButton.setForeground(JBColor.GRAY);
        toggleButton.setBorderPainted(false);
        toggleButton.setFocusPainted(false);
        toggleButton.setContentAreaFilled(false);
        toggleButton.setHorizontalAlignment(SwingConstants.LEFT);
        toggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        toggleButton.addActionListener(e -> {
            collapsed = !collapsed;
            contentPanel.setVisible(!collapsed);
            toggleButton.setText(
                    (collapsed ? COLLAPSED_PREFIX : EXPANDED_PREFIX)
                    + toggleButton.getText().replaceAll("^[►▼] ", "")
            );
            revalidate();
            repaint();
        });
    }

    private void buildContentPanel(List<RagSource> sources, Project project) {
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setAlignmentX(LEFT_ALIGNMENT);
        contentPanel.setVisible(false); // starts collapsed

        for (RagSource source : sources) {
            contentPanel.add(buildSourceRow(source, project));
            contentPanel.add(Box.createRigidArea(new Dimension(0, 2)));
        }
    }

    private JPanel buildSourceRow(RagSource source, Project project) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(JBUI.Borders.empty(1, 4, 1, 0));

        Icon icon = iconFor(source);
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setBorder(JBUI.Borders.empty(0, 0, 0, 4));

        boolean navigable = source.absolutePath() != null
                && source.sourceType() != RagSource.SourceType.WEB;

        JComponent sourceLabel;
        if (navigable) {
            LinkLabel<RagSource> link = new LinkLabel<>(source.displayLabel(), null,
                    (lbl, src) -> openInEditor(src.absolutePath(), project), source);
            link.setFont(FontUtils.getSmallFont());
            link.setToolTipText(source.absolutePath());
            sourceLabel = link;
        } else {
            JLabel plain = new JLabel(source.displayLabel());
            plain.setFont(FontUtils.getSmallFont());
            plain.setForeground(JBColor.GRAY);
            if (source.absolutePath() != null) {
                plain.setToolTipText(source.absolutePath());
            }
            sourceLabel = plain;
        }

        row.add(iconLabel);
        row.add(sourceLabel);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    private static Icon iconFor(RagSource source) {
        return switch (source.sourceType()) {
            case WEB       -> IconUtils.WEB_SEARCH_ENABLED;
            case WORKSPACE -> AllIcons.Actions.EditSource;
            case INDEX     -> fileTypeIcon(source.fileName());
        };
    }

    private static Icon fileTypeIcon(String fileName) {
        if (fileName == null) {
            return AllIcons.FileTypes.Unknown;
        }
        Icon icon = FileTypeManager.getInstance().getFileTypeByFileName(fileName).getIcon();
        return icon != null ? icon : AllIcons.FileTypes.Unknown;
    }

    private static void openInEditor(String absolutePath, Project project) {
        VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(absolutePath);
        if (vf != null) {
            FileEditorManager.getInstance(project).openFile(vf, true);
        }
    }
}
