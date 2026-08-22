package com.pluxee.vector.examples.issues;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluxee.vector.core.VectorKnowledgeBase;
import com.pluxee.vector.core.VectorSearchQuery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class IssueIndexSyncer {

    private final VectorKnowledgeBase knowledgeBase;
    private final String dataset;
    private final Path stateFile;
    private final ObjectMapper objectMapper;

    public IssueIndexSyncer(VectorKnowledgeBase knowledgeBase, String dataset, Path stateFile) {
        this.knowledgeBase = knowledgeBase;
        this.dataset = dataset;
        this.stateFile = stateFile.toAbsolutePath().normalize();
        this.objectMapper = new ObjectMapper();
    }

    public Stats sync(List<PluxeeIssuesLoader.IndexedIssue> issues) {
        Map<String, String> previous = loadState();
        Map<String, String> current = new HashMap<>();

        int unchanged = 0;
        int updated = 0;
        int added = 0;

        for (PluxeeIssuesLoader.IndexedIssue issue : issues) {
            current.put(issue.documentId(), issue.contentHash());
            List<VectorKnowledgeBase.ChunkInput> chunks = issue.chunks().stream()
                    .map(chunk -> new VectorKnowledgeBase.ChunkInput(chunk.content(), chunk.metadata()))
                    .toList();

            String previousHash = previous.get(issue.documentId());
            if (previousHash == null) {
                knowledgeBase.index(dataset, issue.documentId(), chunks);
                added++;
            } else if (!previousHash.equals(issue.contentHash())) {
                knowledgeBase.reindex(dataset, issue.documentId(), chunks);
                updated++;
            } else {
                unchanged++;
            }
        }

        int removed = 0;
        for (String documentId : new HashSet<>(previous.keySet())) {
            if (!current.containsKey(documentId)) {
                knowledgeBase.deleteByDocumentId(dataset, documentId);
                removed++;
            }
        }

        saveState(current);
        return new Stats(unchanged, updated, added, removed);
    }

    public void saveStateOnly(List<PluxeeIssuesLoader.IndexedIssue> issues) {
        Map<String, String> state = new HashMap<>();
        for (PluxeeIssuesLoader.IndexedIssue issue : issues) {
            state.put(issue.documentId(), issue.contentHash());
        }
        saveState(state);
    }

    private Map<String, String> loadState() {
        if (!Files.exists(stateFile)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(stateFile.toFile(), new TypeReference<Map<String, String>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("unable to read index state file " + stateFile, e);
        }
    }

    private void saveState(Map<String, String> state) {
        try {
            Files.createDirectories(stateFile.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), new TreeMap<>(state));
        } catch (IOException e) {
            throw new IllegalStateException("unable to write index state file " + stateFile, e);
        }
    }

    public record Stats(int unchanged, int updated, int added, int removed) {
    }
}
