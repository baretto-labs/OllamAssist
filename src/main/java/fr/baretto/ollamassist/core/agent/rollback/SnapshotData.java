package fr.baretto.ollamassist.core.agent.rollback;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Données de snapshot pour une action
 */
@Data
@Builder
public class SnapshotData {
    private final String filePath;
    private final String fileContent;
    private final boolean fileExists;
    private final Map<String, String> gitState;
    private final Map<String, Object> additionalData;

    public static SnapshotData empty() {
        return SnapshotData.builder()
                .fileExists(false)
                .build();
    }

    public static SnapshotData forFile(String filePath, String content) {
        return SnapshotData.builder()
                .filePath(filePath)
                .fileContent(content)
                .fileExists(true)
                .build();
    }

    public static SnapshotData forDeletedFile(String filePath) {
        return SnapshotData.builder()
                .filePath(filePath)
                .fileExists(false)
                .build();
    }
}