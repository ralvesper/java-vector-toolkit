package com.pluxee.vector.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryVectorStoreTest {

    private final InMemoryVectorStore store = new InMemoryVectorStore();

    @Test
    void shouldFindSimilarVectorsExcludingSelfSortedByCosine() {
        VectorDocument origin = document("vec-a", "doc-a", new float[]{1.0f, 0.0f});
        VectorDocument close = document("vec-b", "doc-b", new float[]{0.9f, 0.1f});
        VectorDocument far = document("vec-c", "doc-c", new float[]{0.0f, 1.0f});
        VectorDocument otherDataset = document("vec-d", "doc-d", new float[]{1.0f, 0.0f}, "outro-dataset");
        store.upsert(List.of(origin, close, far, otherDataset));

        List<VectorSearchResult> results = store.findSimilarById("estudo", "vec-a", 5);

        assertEquals(2, results.size());
        assertEquals("vec-b", results.get(0).id());
        assertEquals("vec-c", results.get(1).id());
    }

    @Test
    void shouldLimitResultsToTopK() {
        VectorDocument origin = document("vec-a", "doc-a", new float[]{1.0f, 0.0f});
        store.upsert(List.of(
                origin,
                document("vec-b", "doc-b", new float[]{0.9f, 0.1f}),
                document("vec-c", "doc-c", new float[]{0.8f, 0.2f}),
                document("vec-d", "doc-d", new float[]{0.7f, 0.3f})
        ));

        List<VectorSearchResult> results = store.findSimilarById("estudo", "vec-a", 2);

        assertEquals(2, results.size());
    }

    @Test
    void shouldThrowWhenVectorNotFoundInDataset() {
        VectorDocument origin = document("vec-a", "doc-a", new float[]{1.0f, 0.0f});
        store.upsert(List.of(origin));

        assertThrows(IllegalArgumentException.class, () -> store.findSimilarById("estudo", "nao-existe", 5));
        assertThrows(IllegalArgumentException.class,
                () -> store.findSimilarById("outro-dataset", "vec-a", 5));
    }

    @Test
    void shouldReturnEmptyWhenOnlySelfExists() {
        VectorDocument origin = document("vec-a", "doc-a", new float[]{1.0f, 0.0f});
        store.upsert(List.of(origin));

        List<VectorSearchResult> results = store.findSimilarById("estudo", "vec-a", 5);

        assertTrue(results.isEmpty());
    }

    private VectorDocument document(String id, String documentId, float[] embedding) {
        return document(id, documentId, embedding, "estudo");
    }

    private VectorDocument document(String id, String documentId, float[] embedding, String dataset) {
        return new VectorDocument(id, documentId, dataset, "conteudo " + documentId,
                Map.of("chunkIndex", 0), embedding);
    }
}
