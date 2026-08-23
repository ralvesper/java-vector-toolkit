package com.pluxee.vector.core;

import java.util.List;

public interface VectorStorePort {

    void upsert(List<VectorDocument> documents);

    List<VectorSearchResult> search(VectorSearchQuery query, float[] queryEmbedding);

    List<VectorSearchResult> findSimilarById(String dataset, String vectorId, int topK);

    void deleteByDocumentId(String dataset, String documentId);

    void deleteByDataset(String dataset);
}

