package com.pluxee.vector.examples.issues;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiEmbeddingProviderTest {

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
        wireMockServer.stubFor(post(urlEqualTo("/models/gemini-embedding-001:embedContent?key=test-key"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {
                          "embedding": { "values": [0.5, -1.25, 0.75] }
                        }
                        """)));

        GeminiEmbeddingProvider provider = new GeminiEmbeddingProvider(
                "test-key", wireMockServer.baseUrl(), "gemini-embedding-001");

        float[] vector = provider.embed("conteudo qualquer");

        assertEquals(3, vector.length);
        assertEquals(0.5f, vector[0]);
        assertEquals(-1.25f, vector[1]);
        assertEquals(0.75f, vector[2]);
    }

    @Test
    void shouldThrowWhenGeminiReturnsError() {
        wireMockServer.stubFor(post(urlEqualTo("/models/gemini-embedding-001:embedContent?key=bad-key"))
                .willReturn(aResponse().withStatus(400).withBody("invalid model")));

        GeminiEmbeddingProvider provider = new GeminiEmbeddingProvider(
                "bad-key", wireMockServer.baseUrl(), "gemini-embedding-001");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                provider.embed("conteudo")
        );

        assertTrue(exception.getMessage().contains("status 400"));
    }
}
