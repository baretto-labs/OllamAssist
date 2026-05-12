package fr.baretto.ollamassist.chat.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.utils.FontUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Iterator;
import java.util.Map;

/**
 * Compact collapsible component that shows one agent tool call.
 *
 * <p>Collapsed (default):  ▶  editFile — src/main/java/Foo.java
 * <p>Expanded on click:    ▼  editFile
 *                              path:   src/main/java/Foo.java
 *                              search: \n}
 *                              …
 */
public class ToolCallIndicator extends JPanel {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PARAM_QUERY = "query";

    private static final Map<String, String> LABELS = Map.of(
            "readFile",            "Read file",
            "editFile",            "Edit file",
            "writeFile",           "Write file",
            "deleteFile",          "Delete file",
            "appendFile",          "Append to file",
            "searchWorkspace",     "Search workspace",
            "searchKnowledgeBase", "Search knowledge base",
            "searchWeb",           "Search web"
    );

    private static final Map<String, String> PRIMARY_KEY = Map.of(
            "readFile",            "path",
            "editFile",            "path",
            "writeFile",           "path",
            "deleteFile",          "path",
            "appendFile",          "path",
            "searchWorkspace",     PARAM_QUERY,
            "searchKnowledgeBase", PARAM_QUERY,
            "searchWeb",           PARAM_QUERY
    );

    private final JLabel arrowLabel;
    private final JPanel detailsPanel;
    private boolean expanded = false;

    public ToolCallIndicator(String toolName, String argsJson) {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(JBUI.Borders.empty(1, 8, 1, 8));

        String label   = LABELS.getOrDefault(toolName, toolName);
        String summary = extractSummary(toolName, argsJson);

        // ── Header (always visible) ──────────────────────────────────
        arrowLabel = new JLabel(AllIcons.General.ArrowRight);
        arrowLabel.setBorder(JBUI.Borders.emptyRight(5));

        JLabel nameLabel = new JLabel(label);
        nameLabel.setFont(FontUtils.getSmallFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(JBColor.namedColor("Component.infoForeground", JBColor.GRAY));

        JLabel summaryLabel = new JLabel(summary.isBlank() ? "" : "  —  " + summary);
        summaryLabel.setFont(FontUtils.getSmallFont());
        summaryLabel.setForeground(JBColor.namedColor("Component.infoForeground", JBColor.GRAY));

        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        headerPanel.setOpaque(false);
        headerPanel.add(arrowLabel);
        headerPanel.add(nameLabel);
        headerPanel.add(summaryLabel);

        headerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        headerPanel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { toggle(); }
        });
        add(headerPanel, BorderLayout.NORTH);

        // ── Details (hidden until expanded) ─────────────────────────
        detailsPanel = buildDetailsPanel(argsJson);
        detailsPanel.setVisible(false);
        add(detailsPanel, BorderLayout.CENTER);
    }

    private void toggle() {
        expanded = !expanded;
        arrowLabel.setIcon(expanded ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight);
        detailsPanel.setVisible(expanded);
        revalidate();
        repaint();
        Container parent = getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    // -------------------------------------------------------------------------

    private static String extractSummary(String toolName, String argsJson) {
        String key = PRIMARY_KEY.get(toolName);
        if (key == null || argsJson == null || argsJson.isBlank()) return "";
        try {
            JsonNode node = MAPPER.readTree(argsJson);
            if (node.has(key)) {
                String val = node.get(key).asText();
                return val.length() > 70 ? val.substring(0, 67) + "…" : val;
            }
        } catch (Exception ignored) {
            // malformed JSON node — return empty string
        }
        return "";
    }

    private static JPanel buildDetailsPanel(String argsJson) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(JBUI.Borders.empty(2, 18, 4, 0));

        if (argsJson == null || argsJson.isBlank()) return panel;

        try {
            JsonNode node = MAPPER.readTree(argsJson);
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                String key = entry.getKey();
                String val = entry.getValue().asText();

                // Truncate very long values (e.g. file content, big replace blocks)
                String display = val.length() > 300
                        ? val.substring(0, 280) + " … [+" + (val.length() - 280) + " chars]"
                        : val;

                // Render newlines as visible symbols, escape HTML
                String htmlVal = display
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\n", "<br><span style='color:gray'>↵</span>");

                JLabel row = new JLabel(
                        "<html><span style='color:gray'>" + key + ":</span>&nbsp;" + htmlVal + "</html>");
                row.setFont(FontUtils.getSmallFont());
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setBorder(JBUI.Borders.emptyTop(2));
                panel.add(row);
            }
        } catch (Exception e) {
            JLabel err = new JLabel("(could not parse arguments)");
            err.setFont(FontUtils.getSmallFont());
            panel.add(err);
        }
        return panel;
    }
}
