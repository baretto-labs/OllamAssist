package fr.baretto.ollamassist.ai.store;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class InitEmbeddingStoreTask extends Task.Backgroundable {
    private static final String SEPARATOR = ";";
    private final EmbeddingStore<TextSegment> store;

    public InitEmbeddingStoreTask(@Nullable Project project, EmbeddingStore<TextSegment> store) {
        super(project, "Ollamassist is reading project files ...", true);
        this.store = store;
    }

    private static String getSources() {
        OllamAssistSettings.State state = OllamAssistSettings.getInstance().getState();
        if (state == null) {
            return "src/";
        }
        return state.getSources();
    }

    private static boolean containsAny(String text, List<String> strings) {
        for (String s : strings) {
            if (text.contains(s)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

        List<String> sources = Arrays.stream(getSources().split(SEPARATOR)).toList();

        PathMatcher pathMatcher = path -> Files.isRegularFile(path) && containsAny(path.toString(), sources);

        FilesUtil.batch(getProject().getName(), getProject().getBasePath(), pathMatcher,
                docs -> {
                    if (!docs.isEmpty()) {
                        EmbeddingStoreIngestor.ingest(docs, store);
                        indicator.setIndeterminate(true);
                        indicator.setText("Ingestion workspace");
                    }
                }
        );

        new IndexRegistry().addProject(getProject().getName());
        Thread.currentThread().setContextClassLoader(classLoader);
    }

}
