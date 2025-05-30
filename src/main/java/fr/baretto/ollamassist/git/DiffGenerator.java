package fr.baretto.ollamassist.git;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DiffGenerator {


    public static String getDiff(@NotNull Collection<Change> includedChanges,
                                 @NotNull List<FilePath> unversionedFiles) {
        StringBuilder sb = new StringBuilder();

        for (Change change : includedChanges) {
            ContentRevision beforeRev = change.getBeforeRevision();
            ContentRevision afterRev = change.getAfterRevision();

            String before = readRevisionContent(beforeRev);
            String after = readRevisionContent(afterRev);

            String path = (afterRev != null ? afterRev.getFile().getPath() : beforeRev.getFile().getPath());

            String compact = MyersDiff.computeCompactDiff(before, after);
            if (!compact.isBlank()) {
                sb.append("=== ").append(path).append(" ===\n");
                sb.append(compact).append("\n");
            }
        }

        for (FilePath filePath : unversionedFiles) {
            VirtualFile vf = filePath.getVirtualFile();
            if (vf != null && !vf.isDirectory()) {
                String content = readFile(vf);
                sb.append("=== ").append(filePath.getPath()).append(" ===\n");
                for (String line : content.split("\n")) {
                    sb.append("+ ").append(line).append("\n");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private static String readRevisionContent(ContentRevision revision) {
        if (revision == null) return "";
        try {
            return revision.getContent();
        } catch (VcsException e) {
            return "";
        }
    }

    private static String readFile(VirtualFile vf) {
        try {
            return new String(vf.contentsToByteArray(), vf.getCharset());
        } catch (IOException e) {
            return "";
        }
    }
}