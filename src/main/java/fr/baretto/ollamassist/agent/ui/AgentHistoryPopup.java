package fr.baretto.ollamassist.agent.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.utils.FontUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.util.stream.Collectors;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Popup that displays recent agent execution history read from the JSONL audit log.
 *
 * <p>Entries are grouped by correlation ID ({@code cid}) so the user can see
 * each full execution at a glance: which tools were called, which succeeded,
 * and which failed.
 */
public final class AgentHistoryPopup {

    private static final String AUDIT_FILE      = ".ollamassist/agent_audit.jsonl";
    private static final String AUDIT_FILE_OLD  = ".ollamassist/agent_audit.jsonl.1";
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    /** Maximum number of executions (correlation IDs) to display, most recent first. */
    private static final int MAX_EXECUTIONS = 50;

    private AgentHistoryPopup() {}

    /**
     * Opens a non-modal dialog showing the execution history for the given project.
     * Must be called on the EDT.
     */
    public static void show(Project project) {
        List<Execution> executions = loadExecutions(project);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(8, 8, 8, 8));

        if (executions.isEmpty()) {
            JLabel empty = new JLabel("No agent executions recorded yet.");
            empty.setFont(FontUtils.getNormalFont());
            empty.setForeground(JBColor.GRAY);
            empty.setBorder(JBUI.Borders.empty(16));
            content.add(empty);
        } else {
            content.add(buildStatsPanel(executions));
            content.add(Box.createVerticalStrut(8));
            content.add(new JSeparator());
            content.add(Box.createVerticalStrut(8));
            for (Execution exec : executions) {
                content.add(buildExecutionRow(exec));
                content.add(Box.createVerticalStrut(6));
            }
        }

        JBScrollPane scroll = new JBScrollPane(content);
        scroll.setPreferredSize(new Dimension(680, 480));
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JDialog dialog = new JDialog((Frame) null, "Agent execution history", false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.getContentPane().add(scroll);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    // -------------------------------------------------------------------------
    // Stats panel
    // -------------------------------------------------------------------------

    private static JPanel buildStatsPanel(List<Execution> executions) {
        long total      = executions.size();
        long successful = executions.stream().filter(e -> !e.hasFailure()).count();
        int  successPct = total == 0 ? 0 : (int) (successful * 100L / total);

        // Most-used tool across all entries
        String mostUsedTool = executions.stream()
                .flatMap(e -> e.entries().stream())
                .collect(Collectors.groupingBy(AuditEntry::tool, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("—");

        long totalSteps = executions.stream()
                .mapToLong(e -> e.entries().size())
                .sum();

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(statLabel("Executions", String.valueOf(total)));
        panel.add(statLabel("Success rate", successPct + "%"));
        panel.add(statLabel("Total steps", String.valueOf(totalSteps)));
        panel.add(statLabel("Top tool", mostUsedTool));

        return panel;
    }

    private static JLabel statLabel(String key, String value) {
        JLabel label = new JLabel("<html><font color='gray'>" + key + ": </font><b>" + value + "</b></html>");
        label.setFont(FontUtils.getSmallFont());
        return label;
    }

    // -------------------------------------------------------------------------
    // Row builder
    // -------------------------------------------------------------------------

    private static JPanel buildExecutionRow(Execution exec) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0,
                        exec.hasFailure()
                                ? JBColor.namedColor("Component.errorFocusColor", JBColor.RED)
                                : JBColor.namedColor("Plugins.tagBackground", JBColor.BLUE)),
                JBUI.Borders.empty(4, 8, 4, 4)
        ));

        // Header: timestamp + step count + success/failure badge
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        header.setOpaque(false);

        Icon icon = exec.hasFailure() ? AllIcons.General.Warning : AllIcons.Actions.Checked;
        header.add(new JLabel(icon));

        JLabel timestamp = new JLabel(TIME_FMT.format(exec.startTime()));
        timestamp.setFont(FontUtils.getSmallFont().deriveFont(Font.BOLD));
        header.add(timestamp);

        JLabel badge = new JLabel(exec.successCount() + "/" + exec.entries().size() + " steps OK");
        badge.setFont(FontUtils.getSmallFont());
        badge.setForeground(exec.hasFailure() ? JBColor.RED : JBColor.namedColor("Label.successForeground", JBColor.GREEN));
        header.add(badge);

        JLabel cidLabel = new JLabel("  cid: " + exec.correlationId().substring(0, Math.min(8, exec.correlationId().length())));
        cidLabel.setFont(FontUtils.getSmallFont());
        cidLabel.setForeground(JBColor.GRAY);
        header.add(cidLabel);

        row.add(header);

        // Steps summary
        for (AuditEntry entry : exec.entries()) {
            JPanel stepRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            stepRow.setOpaque(false);

            Icon stepIcon = entry.ok() ? AllIcons.Actions.Checked : AllIcons.General.Error;
            stepRow.add(new JLabel(stepIcon));

            JLabel toolLabel = new JLabel("[" + entry.tool() + "]");
            toolLabel.setFont(FontUtils.getSmallFont().deriveFont(Font.BOLD));
            toolLabel.setForeground(JBColor.GRAY);
            stepRow.add(toolLabel);

            JLabel stepDesc = new JLabel(truncate(entry.step(), 70));
            stepDesc.setFont(FontUtils.getSmallFont());
            stepDesc.setForeground(entry.ok()
                    ? JBColor.namedColor("Label.foreground", JBColor.BLACK)
                    : JBColor.RED);
            if (!entry.ok() && entry.err() != null && !entry.err().isBlank()) {
                stepDesc.setToolTipText(entry.err());
            }
            stepRow.add(stepDesc);

            row.add(stepRow);
        }

        return row;
    }

    // -------------------------------------------------------------------------
    // JSONL loading
    // -------------------------------------------------------------------------

    private static List<Execution> loadExecutions(Project project) {
        String base = project.getBasePath();
        if (base == null) return List.of();

        List<AuditEntry> allEntries = new ArrayList<>();
        Path main = Paths.get(base, AUDIT_FILE);
        Path old  = Paths.get(base, AUDIT_FILE_OLD);

        // Read rotated file first (older entries), then the current file
        if (Files.exists(old))  allEntries.addAll(readJsonl(old));
        if (Files.exists(main)) allEntries.addAll(readJsonl(main));

        // Group by correlation ID, preserving insertion order (LinkedHashMap)
        Map<String, List<AuditEntry>> byCid = new LinkedHashMap<>();
        for (AuditEntry e : allEntries) {
            byCid.computeIfAbsent(e.cid(), k -> new ArrayList<>()).add(e);
        }

        // Convert to Execution list, most recent first, capped at MAX_EXECUTIONS
        List<Execution> result = new ArrayList<>();
        List<String> cids = new ArrayList<>(byCid.keySet());
        Collections.reverse(cids);
        for (String cid : cids) {
            if (result.size() >= MAX_EXECUTIONS) break;
            List<AuditEntry> entries = byCid.get(cid);
            result.add(new Execution(cid, entries));
        }
        return result;
    }

    private static List<AuditEntry> readJsonl(Path path) {
        ObjectMapper mapper = new ObjectMapper();
        List<AuditEntry> entries = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                try {
                    JsonNode node = mapper.readTree(line);
                    entries.add(new AuditEntry(
                            text(node, "cid"),
                            text(node, "ts"),
                            text(node, "tool"),
                            text(node, "step"),
                            node.path("ok").asBoolean(true),
                            text(node, "err")
                    ));
                } catch (Exception e) {
                    // Skip malformed lines
                }
            }
        } catch (IOException e) {
            // File not accessible — return what we have
        }
        return entries;
    }

    private static String text(JsonNode node, String field) {
        JsonNode f = node.path(field);
        return f.isMissingNode() ? "" : f.asText("");
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    // -------------------------------------------------------------------------
    // Value types
    // -------------------------------------------------------------------------

    private record AuditEntry(String cid, String ts, String tool, String step, boolean ok, String err) {
        Instant instant() {
            try { return Instant.parse(ts); } catch (Exception e) { return Instant.EPOCH; }
        }
    }

    private record Execution(String correlationId, List<AuditEntry> entries) {
        Instant startTime() {
            return entries.isEmpty() ? Instant.EPOCH : entries.get(0).instant();
        }
        long successCount() {
            return entries.stream().filter(AuditEntry::ok).count();
        }
        boolean hasFailure() {
            return entries.stream().anyMatch(e -> !e.ok());
        }
    }
}
