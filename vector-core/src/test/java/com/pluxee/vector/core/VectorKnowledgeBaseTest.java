package com.pluxee.vector.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}

