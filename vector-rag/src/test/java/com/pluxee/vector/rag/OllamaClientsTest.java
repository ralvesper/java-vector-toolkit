package com.pluxee.vector.rag;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OllamaClientsTest {

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldCompleteWithGeneratedText() {
        wireMockServer.stubFor(post(urlEqualTo("/api/generate"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        { "model": "llama3.2:1b", "response": "Usamos Neo4j para o grafo.", "done": true }
                        """)));

        OllamaLlmClient client = new OllamaLlmClient(wireMockServer.baseUrl(), "llama3.2:1b");

        assertEquals("Usamos Neo4j para o grafo.", client.complete("PERGUNTA: qual banco?"));
        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/generate")));
    }

    @Test
    void shouldThrowWhenGenerationFails() {
        wireMockServer.stubFor(post(urlEqualTo("/api/generate"))
                .willReturn(aResponse().withStatus(404).withBody("{\"error\":\"model not found\"}")));

        OllamaLlmClient client = new OllamaLlmClient(wireMockServer.baseUrl(), "nao-existe");

        assertThrows(IllegalStateException.class, () -> client.complete("teste"));
    }

    @Test
    void shouldEmbedTextAsFloatVector() {
        wireMockServer.stubFor(post(urlEqualTo("/api/embed"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        { "model": "nomic-embed-text", "embeddings": [[0.1, 0.2, 0.3]] }
                        """)));

        OllamaEmbeddingProvider provider = new OllamaEmbeddingProvider(
                wireMockServer.baseUrl(), "nomic-embed-text"
        );

        float[] vector = provider.embed("texto de teste");

        assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f}, vector);
    }

    @Test
    void shouldThrowWhenEmbeddingFails() {
        wireMockServer.stubFor(post(urlEqualTo("/api/embed"))
                .willReturn(aResponse().withStatus(404).withBody("{\"error\":\"model not found\"}")));

        OllamaEmbeddingProvider provider = new OllamaEmbeddingProvider(
                wireMockServer.baseUrl(), "nao-existe"
        );

        assertThrows(IllegalStateException.class, () -> provider.embed("teste"));
    }
}
