package com.pluxee.vector.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VectorKnowledgeBase {

    private final EmbeddingProvider embeddingProvider;
    private final VectorStorePort vectorStore;

    public VectorKnowledgeBase(EmbeddingProvider embeddingProvider, VectorStorePort vectorStore) {
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
    }

    public void index(String dataset, String documentId, List<ChunkInput> chunks) {
        List<VectorDocument> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            ChunkInput chunk = chunks.get(i);
            documents.add(new VectorDocument(
                    dataset + "-" + documentId + "-" + i,
                    documentId,
                    dataset,
                    chunk.content(),
                    chunk.metadata(),
                    embeddingProvider.embed(chunk.content())
            ));
        }
        vectorStore.upsert(documents);
    }

    public List<VectorSearchResult> search(VectorSearchQuery query) {
        return vectorStore.search(query, embeddingProvider.embed(query.query()));
    }

    public List<VectorSearchResult> findSimilar(String dataset, String vectorId, int topK) {
        return vectorStore.findSimilarById(dataset, vectorId, topK);
    }

    public void deleteByDocumentId(String dataset, String documentId) {
        vectorStore.deleteByDocumentId(dataset, documentId);
    }

    public void deleteByDataset(String dataset) {
        vectorStore.deleteByDataset(dataset);
    }

    public void reindex(String dataset, String documentId, List<ChunkInput> chunks) {
        deleteByDocumentId(dataset, documentId);
        index(dataset, documentId, chunks);
    }

    public record ChunkInput(String content, Map<String, Object> metadata) {
    }
}

