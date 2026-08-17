package com.pluxee.vector.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryVectorStore implements VectorStorePort {

    private final Map<String, VectorDocument> data = new ConcurrentHashMap<>();

    @Override
    public void upsert(List<VectorDocument> documents) {
        for (VectorDocument document : documents) {
            data.put(document.id(), document);
        }
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchQuery query, float[] queryEmbedding) {
        return data.values().stream()
                .filter(doc -> doc.dataset().equals(query.dataset()))
                .filter(doc -> matchesFilters(doc.metadata(), query.filters()))
                .map(doc -> new VectorSearchResult(
                        cosine(queryEmbedding, doc.embedding()),
                        doc.id(),
                        doc.documentId(),
                        doc.content(),
                        doc.metadata()
                ))
                .sorted(Comparator.comparingDouble(VectorSearchResult::score).reversed())
                .limit(query.topK())
                .toList();
    }

    @Override
    public void deleteByDocumentId(String dataset, String documentId) {
        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, VectorDocument> entry : data.entrySet()) {
            VectorDocument value = entry.getValue();
            if (value.dataset().equals(dataset) && value.documentId().equals(documentId)) {
                keysToRemove.add(entry.getKey());
            }
        }
        keysToRemove.forEach(data::remove);
    }

    @Override
    public void deleteByDataset(String dataset) {
        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, VectorDocument> entry : data.entrySet()) {
            if (entry.getValue().dataset().equals(dataset)) {
                keysToRemove.add(entry.getKey());
            }
        }
        keysToRemove.forEach(data::remove);
    }

    private boolean matchesFilters(Map<String, Object> metadata, Map<String, Object> filters) {
        for (Map.Entry<String, Object> filter : filters.entrySet()) {
            if (!Objects.equals(metadata.get(filter.getKey()), filter.getValue())) {
                return false;
            }
        }
        return true;
    }

    private double cosine(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("vector dimensions do not match");
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}

