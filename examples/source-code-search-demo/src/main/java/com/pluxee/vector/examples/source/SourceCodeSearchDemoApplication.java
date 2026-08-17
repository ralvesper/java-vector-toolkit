package com.pluxee.vector.examples.source;

import com.pluxee.vector.core.HashingEmbeddingProvider;
import com.pluxee.vector.core.InMemoryVectorStore;
import com.pluxee.vector.core.VectorKnowledgeBase;
import com.pluxee.vector.core.VectorSearchQuery;

import java.util.List;
import java.util.Map;

public class SourceCodeSearchDemoApplication {

    public static void main(String[] args) {
        VectorKnowledgeBase knowledgeBase = new VectorKnowledgeBase(
                new HashingEmbeddingProvider(256),
                new InMemoryVectorStore()
        );

        knowledgeBase.index(
                "source-code",
                "OrderService.java",
                List.of(
                        new VectorKnowledgeBase.ChunkInput(
                                "OrderService processa pedidos e publica evento no RabbitMQ",
                                Map.of("class", "OrderService", "type", "service", "technology", "rabbitmq")
                        )
                )
        );

        knowledgeBase.index(
                "source-code",
                "PaymentRepository.java",
                List.of(
                        new VectorKnowledgeBase.ChunkInput(
                                "PaymentRepository persiste pagamentos no Oracle",
                                Map.of("class", "PaymentRepository", "type", "repository", "technology", "oracle")
                        )
                )
        );

        var results = knowledgeBase.search(
                VectorSearchQuery.builder()
                        .dataset("source-code")
                        .query("Onde e feito o processamento de pedidos?")
                        .topK(3)
                        .build()
        );

        System.out.println("Resultados encontrados: " + results.size());
        results.forEach(result -> System.out.println(result.documentId() + " -> score=" + result.score()));
    }
}

