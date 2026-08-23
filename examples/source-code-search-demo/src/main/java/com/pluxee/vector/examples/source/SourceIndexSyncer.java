package com.pluxee.vector.examples.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SourceIndexSyncer {

    private SourceIndexSyncer() {
    }

    record SyncPlan(List<String> toUpsert, List<String> toDelete, int unchanged) {
    }

    static SyncPlan diff(Set<String> diskDocuments, Map<String, String> diskHashes,
                         Set<String> storedDocuments, Map<String, String> storedHashes) {
        List<String> toUpsert = new ArrayList<>();
        for (String documentId : diskDocuments) {
            String storedHash = storedHashes.get(documentId);
            if (storedHash == null || !storedHash.equals(diskHashes.get(documentId))) {
                toUpsert.add(documentId);
            }
        }
        List<String> toDelete = storedDocuments.stream()
                .filter(documentId -> !diskDocuments.contains(documentId))
                .toList();
        int unchanged = diskDocuments.size() - toUpsert.size();
        return new SyncPlan(toUpsert, toDelete, unchanged);
    }

    static String documentIdFromVectorId(String dataset, String vectorId) {
        String prefix = dataset + "-";
        if (!vectorId.startsWith(prefix)) {
            return null;
        }
        String remainder = vectorId.substring(prefix.length());
        int lastDash = remainder.lastIndexOf('-');
        if (lastDash <= 0 || !remainder.substring(lastDash + 1).matches("\\d+")) {
            return null;
        }
        return remainder.substring(0, lastDash);
    }
}
