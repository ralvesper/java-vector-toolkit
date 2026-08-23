package com.pluxee.vector.rag;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeminiLlmClientTest {

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
        wireMockServer.stubFor(post(urlEqualTo("/models/gemini-2.5-flash:generateContent?key=test-key"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  { "text": "Usamos Neo4j " },
                                  { "text": "para o grafo [FS-699]." }
                                ]
                              }
                            }
                          ]
                        }
                        """)));

        GeminiLlmClient client = new GeminiLlmClient(
                "test-key", wireMockServer.baseUrl(), "gemini-2.5-flash"
        );

        String answer = client.complete("PERGUNTA: qual banco?");

        assertEquals("Usamos Neo4j para o grafo [FS-699].", answer);
        wireMockServer.verify(postRequestedFor(
                urlEqualTo("/models/gemini-2.5-flash:generateContent?key=test-key"))
                .withRequestBody(equalTo("{\"contents\":[{\"parts\":[{\"text\":\"PERGUNTA: qual banco?\"}]}]}")));
    }

    @Test
    void shouldRetryAfterRateLimitAndSucceed() {
        wireMockServer.stubFor(post(urlEqualTo("/models/gemini-2.5-flash:generateContent?key=test-key"))
                .willReturn(aResponse().withStatus(429).withBody("""
                        { "error": { "status": "RESOURCE_EXHAUSTED", "retryDelay": "1s" } }
                        """)
                )
                .inScenario("rate-limit")
                .whenScenarioStateIs("Started")
                .willSetStateTo("limited-once"));

        wireMockServer.stubFor(post(urlEqualTo("/models/gemini-2.5-flash:generateContent?key=test-key"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        { "candidates": [ { "content": { "parts": [ { "text": "ok" } ] } } ] }
                        """)
                )
                .inScenario("rate-limit")
                .whenScenarioStateIs("limited-once"));

        GeminiLlmClient client = new GeminiLlmClient(
                "test-key", wireMockServer.baseUrl(), "gemini-2.5-flash"
        );

        assertEquals("ok", client.complete("teste"));
    }

    @Test
    void shouldThrowAfterExhaustingRetriesOnPersistent429() {
        wireMockServer.stubFor(post(urlEqualTo("/models/gemini-2.5-flash:generateContent?key=test-key"))
                .willReturn(aResponse().withStatus(429).withBody("""
                        { "error": { "status": "RESOURCE_EXHAUSTED", "retryDelay": "1s" } }
                        """)));

        GeminiLlmClient client = new GeminiLlmClient(
                "test-key", wireMockServer.baseUrl(), "gemini-2.5-flash"
        );

        assertThrows(IllegalStateException.class, () -> client.complete("teste"));
    }
}
