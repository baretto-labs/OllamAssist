package fr.baretto.ollamassist.chat.rag;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * What to change in the KnowledgeIndex so it matches the Workspace again, after a period when
 * file changes were not followed (RAG switched off). A modified file is both removed and indexed:
 * its old segments must go before the new ones are added.
 *
 * @param toRemove files whose segments must be deleted
 * @param toIndex  files to index
 */
public record IndexCatchUp(Set<String> toRemove, Set<String> toIndex) {

    /**
     * @param indexed   indexed files, with the time each one was indexed
     * @param collected files collected from the Workspace, with their last modification time
     * @param onDisk    whether a file still exists
     */
    public static IndexCatchUp plan(Map<String, Instant> indexed,
                                    Map<String, Instant> collected,
                                    Predicate<String> onDisk) {
        Set<String> toRemove = new TreeSet<>();
        Set<String> toIndex = new TreeSet<>();

        collected.forEach((file, modifiedAt) -> {
            Instant indexedAt = indexed.get(file);
            if (indexedAt == null) {
                toIndex.add(file);
            } else if (modifiedAt.isAfter(indexedAt)) {
                toRemove.add(file);
                toIndex.add(file);
            }
        });
        indexed.keySet().stream()
                .filter(file -> !onDisk.test(file))
                .forEach(toRemove::add);

        return new IndexCatchUp(Collections.unmodifiableSet(toRemove), Collections.unmodifiableSet(toIndex));
    }

    public boolean isEmpty() {
        return toRemove.isEmpty() && toIndex.isEmpty();
    }
}
