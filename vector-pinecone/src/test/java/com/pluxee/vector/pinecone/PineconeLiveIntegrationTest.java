package com.pluxee.vector.pinecone;

import com.pluxee.vector.core.HashingEmbeddingProvider;
import com.pluxee.vector.core.VectorDocument;
import com.pluxee.vector.core.VectorSearchQuery;
import com.pluxee.vector.core.VectorSearchResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "PINECONE_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PINECONE_HOST", matches = ".+")
class PineconeLiveIntegrationTest {

    private static final String DATASET = "it-" + UUID.randomUUID();
    private static final long CONSISTENCY_TIMEOUT_MS = 30_000;

    private PineconeVectorStore store;
    private HashingEmbeddingProvider embeddings;

    @BeforeAll
    void setUp() {
        int dimensions = Integer.parseInt(System.getenv().getOrDefault("PINECONE_DIMENSION", "1536"));
        store = new PineconeVectorStore(new PineconeClientConfig(
                System.getenv("PINECONE_API_KEY"),
                System.getenv("PINECONE_HOST")
        ));
        embeddings = new HashingEmbeddingProvider(dimensions);
    }

    @AfterAll
    void tearDown() {
        if (store != null) {
            store.deleteByDataset(DATASET);
        }
    }

    @Test
    void shouldIndexSearchAndDeleteAgainstRealIndex() {
        assertTrue(store.healthCheck());

        store.upsert(List.of(
                new VectorDocument(
                        DATASET + "-rabbitmq",
                        "rabbitmq.md",
                        DATASET,
                        "RabbitMQ desacopla processamento assincrono e melhora resiliencia.",
                        Map.of("technology", "rabbitmq"),
                        embeddings.embed("RabbitMQ desacopla processamento assincrono e melhora resiliencia.")
                ),
                new VectorDocument(
                        DATASET + "-kubernetes",
                        "kubernetes.md",
                        DATASET,
                        "Kubernetes orquestra containers e automatiza deploy e escalabilidade.",
                        Map.of("technology", "kubernetes"),
                        embeddings.embed("Kubernetes orquestra containers e automatiza deploy e escalabilidade.")
                )
        ));

        List<VectorSearchResult> allResults = awaitResults("upsert visibility for both documents", () -> {
            List<VectorSearchResult> results = query(allQuery());
            return results.size() == 2 ? results : null;
        });
        assertEquals(Set.of("rabbitmq.md", "kubernetes.md"),
                Set.of(allResults.get(0).documentId(), allResults.get(1).documentId()));

        List<VectorSearchResult> ranked = awaitResults("nearest neighbor ranking", () -> {
            List<VectorSearchResult> results = query(exactQuery("RabbitMQ desacopla processamento assincrono"));
            return results.size() == 2 ? results : null;
        });
        assertEquals("rabbitmq.md", ranked.getFirst().documentId());
        assertEquals("RabbitMQ desacopla processamento assincrono e melhora resiliencia.", ranked.getFirst().content());

        awaitResults("metadata filter visibility", () -> {
            List<VectorSearchResult> results = query(filterQuery("kubernetes"), "Kubernetes orquestra containers");
            return results.size() == 1 && "kubernetes.md".equals(results.getFirst().documentId()) ? results : null;
        });
        assertEquals("kubernetes.md", query(filterQuery("kubernetes"), "Kubernetes orquestra containers").getFirst().documentId());

        List<VectorSearchResult> similar = store.findSimilarById(DATASET, DATASET + "-rabbitmq", 10);
        assertEquals(1, similar.size());
        assertEquals("kubernetes.md", similar.getFirst().documentId());

        store.deleteByDocumentId(DATASET, "rabbitmq.md");
        List<VectorSearchResult> remaining = awaitResults("delete by documentId applied", () -> {
            List<VectorSearchResult> results = query(allQuery());
            return results.size() == 1 && "kubernetes.md".equals(results.getFirst().documentId()) ? results : null;
        });
        assertEquals("kubernetes.md", remaining.getFirst().documentId());
    }

    private List<VectorSearchResult> query(VectorSearchQuery searchQuery) {
        return query(searchQuery, "consulta");
    }

    private List<VectorSearchResult> query(VectorSearchQuery searchQuery, String text) {
        return store.search(searchQuery, embeddings.embed(text));
    }

    private VectorSearchQuery allQuery() {
        return VectorSearchQuery.builder().dataset(DATASET).query("consulta").topK(10).build();
    }

    private VectorSearchQuery exactQuery(String content) {
        return VectorSearchQuery.builder().dataset(DATASET).query(content).topK(2).build();
    }

    private VectorSearchQuery filterQuery(String technology) {
        return VectorSearchQuery.builder()
                .dataset(DATASET)
                .query("orquestracao")
                .topK(10)
                .filter("technology", technology)
                .build();
    }

    private List<VectorSearchResult> awaitResults(String step, Supplier<List<VectorSearchResult>> condition) {
        long deadline = System.currentTimeMillis() + CONSISTENCY_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            List<VectorSearchResult> result = condition.get();
            if (result != null) {
                return result;
            }
            sleepQuietly();
        }
        throw new IllegalStateException("timeout waiting for Pinecone consistency: " + step);
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for Pinecone consistency", e);
        }
    }
}
