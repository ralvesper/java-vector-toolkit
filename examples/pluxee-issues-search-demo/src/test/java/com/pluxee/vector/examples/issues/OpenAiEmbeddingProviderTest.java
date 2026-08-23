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

class OpenAiEmbeddingProviderTest {

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
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {
                          "data": [
                            { "embedding": [0.25, -0.5, 1.0], "index": 0 }
                          ],
                          "model": "text-embedding-3-small"
                        }
                        """)));

        OpenAiEmbeddingProvider provider = new OpenAiEmbeddingProvider(
                "test-key", wireMockServer.baseUrl(), "text-embedding-3-small");

        float[] vector = provider.embed("conteudo qualquer");

        assertEquals(3, vector.length);
        assertEquals(0.25f, vector[0]);
        assertEquals(-0.5f, vector[1]);
        assertEquals(1.0f, vector[2]);

        wireMockServer.verify(postRequestedFor(urlEqualTo("/embeddings"))
                .withHeader("Authorization", equalTo("Bearer test-key")));
    }

    @Test
    void shouldThrowWhenOpenAiReturnsError() {
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(401).withBody("invalid api key")));

        OpenAiEmbeddingProvider provider = new OpenAiEmbeddingProvider(
                "bad-key", wireMockServer.baseUrl(), "text-embedding-3-small");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                provider.embed("conteudo")
        );

        assertTrue(exception.getMessage().contains("status 401"));
    }

    @Test
    void shouldRetryOn429WhenRetryAfterHeaderPresent() {
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .inScenario("rate-limit")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "0")
                        .withBody("rate limited"))
                .willSetStateTo("retried"));
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .inScenario("rate-limit")
                .whenScenarioStateIs("retried")
                .willReturn(aResponse().withStatus(200).withBody("""
                        {
                          "data": [
                            { "embedding": [0.5, 0.5], "index": 0 }
                          ]
                        }
                        """)));

        OpenAiEmbeddingProvider provider = new OpenAiEmbeddingProvider(
                "test-key", wireMockServer.baseUrl(), "text-embedding-3-small");

        float[] vector = provider.embed("conteudo");

        assertEquals(2, vector.length);
        wireMockServer.verify(2, postRequestedFor(urlEqualTo("/embeddings")));
    }

    @Test
    void shouldFailFastOn429WithoutRetryAfterHeader() {
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(429)
                        .withBody("{\"error\":{\"code\":\"credit_balance_exhausted\"}}")));

        OpenAiEmbeddingProvider provider = new OpenAiEmbeddingProvider(
                "test-key", wireMockServer.baseUrl(), "text-embedding-3-small");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                provider.embed("conteudo")
        );

        assertTrue(exception.getMessage().contains("credit_balance_exhausted"));
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/embeddings")));
    }
}
