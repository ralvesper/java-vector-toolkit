package com.pluxee.vector.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorKnowledgeBaseTest {

    @Test
    void shouldIndexSearchAndFilterByMetadata() {
        VectorKnowledgeBase knowledgeBase = new VectorKnowledgeBase(
                new HashingEmbeddingProvider(128),
                new InMemoryVectorStore()
        );

        knowledgeBase.index(
                "java-architecture",
                "rabbitmq.md",
                List.of(
                        new VectorKnowledgeBase.ChunkInput(
                                "RabbitMQ desacopla o processamento assincrono",
                                Map.of("technology", "rabbitmq", "type", "documentation")
                        ),
                        new VectorKnowledgeBase.ChunkInput(
                                "Oracle armazena pagamentos consolidados",
                                Map.of("technology", "oracle", "type", "documentation")
                        )
                )
        );

        var results = knowledgeBase.search(
                VectorSearchQuery.builder()
                        .dataset("java-architecture")
                        .query("Como desacoplar processamento?")
                        .topK(5)
                        .filter("technology", "rabbitmq")
                        .build()
        );

        assertFalse(results.isEmpty());
        assertEquals("rabbitmq.md", results.getFirst().documentId());
    }

    @Test
    void shouldGenerateDeterministicVectorIdsPerChunk() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        VectorKnowledgeBase knowledgeBase = new VectorKnowledgeBase(
                new HashingEmbeddingProvider(128),
                store
        );

        knowledgeBase.index("estudo", "doc.md", List.of(
                new VectorKnowledgeBase.ChunkInput("primeiro chunk", Map.of()),
                new VectorKnowledgeBase.ChunkInput("segundo chunk", Map.of())
        ));

        assertTrue(store.findById("estudo-doc.md-0").isPresent());
        assertTrue(store.findById("estudo-doc.md-1").isPresent());
    }
}

