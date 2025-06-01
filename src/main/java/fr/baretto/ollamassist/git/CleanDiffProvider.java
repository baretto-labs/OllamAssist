package fr.baretto.ollamassist.git;


import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CleanDiffProvider {

    public static String get(@NotNull List<Change> changes) {
        StringBuilder sb = new StringBuilder();

        for (Change change : ContainerUtil.notNullize(changes)) {
            try {
                processChange(change, sb);
            } catch (Exception e) {
                sb.append("// Error processing change for: ")
                        .append(getFilePath(change))
                        .append("\n");
            }
        }

        return sb.toString();
    }

    private static void processChange(Change change, StringBuilder sb) throws VcsException {
        ContentRevision before = change.getBeforeRevision();
        ContentRevision after = change.getAfterRevision();

        String beforeContent = safeGetContent(before);
        String afterContent = safeGetContent(after);

        List<String> beforeLines = splitLines(beforeContent);
        List<String> afterLines = splitLines(afterContent);

        sb.append("# File: ").append(getFilePath(change)).append("\n");
        computeLineDiffs(beforeLines, afterLines, sb);
    }

    private static void computeLineDiffs(List<String> before, List<String> after, StringBuilder sb) {
        int maxLines = Math.max(before.size(), after.size());

        for (int i = 0; i < maxLines; i++) {
            String beforeLine = i < before.size() ? before.get(i) : null;
            String afterLine = i < after.size() ? after.get(i) : null;

            if (beforeLine != null && afterLine != null) {
                if (!beforeLine.equals(afterLine)) {
                    sb.append("- ").append(beforeLine).append("\n");
                    sb.append("+ ").append(afterLine).append("\n");
                }
            } else if (afterLine != null) {
                sb.append("+ ").append(afterLine).append("\n");
            } else if (beforeLine != null) {
                sb.append("- ").append(beforeLine).append("\n");
            }
        }
    }

    // Méthodes utilitaires
    private static String getFilePath(Change change) {
        return change.getVirtualFile() != null
                ? change.getVirtualFile().getPath()
                : change.toString();
    }

    private static String safeGetContent(ContentRevision revision) throws VcsException {
        return revision != null && revision.getContent() != null
                ? revision.getContent().replace("\r", "")
                : "";
    }

    private static List<String> splitLines(String content) {
        List<String> lines = new ArrayList<>();
        if (content == null) return lines;

        int pos = 0;
        while (pos < content.length()) {
            int next = content.indexOf('\n', pos);
            if (next == -1) {
                lines.add(content.substring(pos));
                break;
            }
            lines.add(content.substring(pos, next));
            pos = next + 1;
        }
        return lines;
    }
}