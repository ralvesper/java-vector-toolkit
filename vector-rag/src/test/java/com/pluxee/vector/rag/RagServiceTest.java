package com.pluxee.vector.rag;

import com.pluxee.vector.core.HashingEmbeddingProvider;
import com.pluxee.vector.core.InMemoryVectorStore;
import com.pluxee.vector.core.VectorKnowledgeBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagServiceTest {

    private VectorKnowledgeBase knowledgeBase() {
        VectorKnowledgeBase knowledgeBase = new VectorKnowledgeBase(
                new HashingEmbeddingProvider(128),
                new InMemoryVectorStore()
        );
        knowledgeBase.index(
                "arquitetura",
                "decisoes.md",
                List.of(new VectorKnowledgeBase.ChunkInput(
                        "Escolhemos Neo4j para metadados estruturais do grafo de dependencias",
                        Map.of("type", "adr")
                ))
        );
        return knowledgeBase;
    }

    @Test
    void shouldAnswerUsingLlmClientWhenAvailable() {
        StringBuilder receivedPrompt = new StringBuilder();
        LlmClient llmClient = prompt -> {
            receivedPrompt.append(prompt);
            return "Resposta gerada pelo LLM [decisoes.md]";
        };

        RagService service = new RagService(knowledgeBase(), new DefaultContextBuilder(), llmClient);
        RagResponse response = service.ask(RagRequest.of("arquitetura", "Qual banco usamos para o grafo?"));

        assertEquals("Resposta gerada pelo LLM [decisoes.md]", response.answer());
        assertTrue(receivedPrompt.toString().contains("Neo4j"));
        assertTrue(receivedPrompt.toString().contains("Qual banco usamos para o grafo?"));
        assertTrue(response.context().contains("decisoes.md"));
    }

    @Test
    void shouldFallbackToRetrievalOnlyMessageWithoutLlmClient() {
        RagService service = new RagService(knowledgeBase(), new DefaultContextBuilder());

        RagResponse response = service.ask(RagRequest.of("arquitetura", "Qual banco usamos para o grafo?"));

        assertEquals("Contexto recuperado com 1 trecho(s). Pergunta: Qual banco usamos para o grafo?",
                response.answer());
    }
}
