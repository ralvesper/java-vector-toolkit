package com.pluxee.vector.examples.issues;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaEmbeddingProviderTest {

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void shouldReturnEmbeddingFromResponse() {
        wireMockServer.stubFor(post(urlEqualTo("/api/embed"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        { "embeddings": [[0.5, -1.25, 0.75]], "model": "nomic-embed-text" }
                        """)));

        OllamaEmbeddingProvider provider = new OllamaEmbeddingProvider(
                wireMockServer.baseUrl(), "nomic-embed-text");

        float[] vector = provider.embed("conteudo qualquer");

        assertEquals(3, vector.length);
        assertEquals(0.5f, vector[0]);
        assertEquals(-1.25f, vector[1]);
        assertEquals(0.75f, vector[2]);
    }

    @Test
    void shouldSendBearerWhenApiKeyProvided() {
        wireMockServer.stubFor(post(urlEqualTo("/api/embed"))
                .willReturn(aResponse().withStatus(200).withBody("{ \"embeddings\": [[1.0]] }")));

        OllamaEmbeddingProvider provider = new OllamaEmbeddingProvider(
                wireMockServer.baseUrl(), "nomic-embed-text", "secret-key");

        provider.embed("conteudo");

        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/embed"))
                .withHeader("Authorization", equalTo("Bearer secret-key")));
    }

    @Test
    void shouldThrowWhenOllamaReturnsError() {
        wireMockServer.stubFor(post(urlEqualTo("/api/embed"))
                .willReturn(aResponse().withStatus(404).withBody("{\"error\":\"model not found\"}")));

        OllamaEmbeddingProvider provider = new OllamaEmbeddingProvider(
                wireMockServer.baseUrl(), "missing-model");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                provider.embed("conteudo")
        );

        assertTrue(exception.getMessage().contains("status 404"));
    }
}
