package com.pluxee.vector.pinecone;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.pluxee.vector.core.VectorDocument;
import com.pluxee.vector.core.VectorSearchQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PineconeVectorStoreTest {

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
    void shouldUpsertSearchDeleteAndCheckHealth() {
        wireMockServer.stubFor(post(urlEqualTo("/vectors/upsert"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        wireMockServer.stubFor(post(urlEqualTo("/query"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {
                          "matches": [
                            {
                              "id": "vec-1",
                              "score": 0.91,
                              "metadata": {
                                "documentId": "doc-1",
                                "content": "RabbitMQ desacopla processamento",
                                "technology": "rabbitmq"
                              }
                            }
                          ]
                        }
                        """)));

        wireMockServer.stubFor(post(urlEqualTo("/vectors/delete"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        wireMockServer.stubFor(post(urlEqualTo("/describe_index_stats"))
                .willReturn(aResponse().withStatus(200).withBody("{\"namespaces\":{}}")));

        PineconeVectorStore store = new PineconeVectorStore(
                new PineconeClientConfig("test-key", wireMockServer.baseUrl())
        );

        store.upsert(List.of(new VectorDocument(
                "vec-1",
                "doc-1",
                "payments",
                "RabbitMQ desacopla processamento",
                Map.of("technology", "rabbitmq"),
                new float[]{0.1f, 0.2f, 0.3f}
        )));

        var results = store.search(
                VectorSearchQuery.builder()
                        .dataset("payments")
                        .query("como desacoplar processamento?")
                        .topK(3)
                        .filter("technology", "rabbitmq")
                        .build(),
                new float[]{0.1f, 0.2f, 0.3f}
        );

        store.deleteByDocumentId("payments", "doc-1");
        store.deleteByDataset("payments");

        assertTrue(store.healthCheck());
        assertFalse(results.isEmpty());
        assertEquals("doc-1", results.getFirst().documentId());

        wireMockServer.verify(postRequestedFor(urlEqualTo("/vectors/upsert"))
                .withHeader("Api-Key", equalTo("test-key"))
                .withRequestBody(matchingJsonPath("$.namespace", equalTo("payments"))));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/query"))
                .withRequestBody(matchingJsonPath("$.filter.technology", equalTo("rabbitmq"))));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/vectors/delete"))
                .withRequestBody(matchingJsonPath("$.filter.documentId", equalTo("doc-1"))));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/vectors/delete"))
                .withRequestBody(matchingJsonPath("$.deleteAll", equalTo("true"))));
    }

    @Test
    void shouldSortMatchesByScoreDescending() {
        wireMockServer.stubFor(post(urlEqualTo("/query"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {
                          "matches": [
                            {
                              "id": "vec-low",
                              "score": 0.1,
                              "metadata": { "documentId": "doc-low", "content": "baixa relevancia" }
                            },
                            {
                              "id": "vec-high",
                              "score": 0.9,
                              "metadata": { "documentId": "doc-high", "content": "alta relevancia" }
                            }
                          ]
                        }
                        """)));

        PineconeVectorStore store = new PineconeVectorStore(
                new PineconeClientConfig("test-key", wireMockServer.baseUrl())
        );

        List<com.pluxee.vector.core.VectorSearchResult> results = store.search(
                VectorSearchQuery.builder()
                        .dataset("payments")
                        .query("consulta")
                        .topK(2)
                        .build(),
                new float[]{0.1f, 0.2f}
        );

        assertEquals(2, results.size());
        assertEquals("doc-high", results.get(0).documentId());
        assertEquals("doc-low", results.get(1).documentId());
    }

    @Test
    void shouldFindSimilarByIdExcludingSelf() {
        wireMockServer.stubFor(post(urlEqualTo("/query"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {
                          "matches": [
                            {
                              "id": "vec-origin",
                              "score": 1.0,
                              "metadata": { "documentId": "doc-origin", "content": "vetor de origem" }
                            },
                            {
                              "id": "vec-similar",
                              "score": 0.87,
                              "metadata": { "documentId": "doc-similar", "content": "conteudo similar" }
                            }
                          ]
                        }
                        """)));

        PineconeVectorStore store = new PineconeVectorStore(
                new PineconeClientConfig("test-key", wireMockServer.baseUrl())
        );

        List<com.pluxee.vector.core.VectorSearchResult> results = store.findSimilarById("payments", "vec-origin", 5);

        assertEquals(1, results.size());
        assertEquals("doc-similar", results.getFirst().documentId());

        wireMockServer.verify(postRequestedFor(urlEqualTo("/query"))
                .withRequestBody(matchingJsonPath("$.id", equalTo("vec-origin")))
                .withRequestBody(matchingJsonPath("$.namespace", equalTo("payments")))
                .withRequestBody(matchingJsonPath("$.topK", equalTo("6"))));
    }

    @Test
    void shouldThrowWhenPineconeReturnsClientError() {
        wireMockServer.stubFor(post(urlEqualTo("/vectors/upsert"))
                .willReturn(aResponse().withStatus(400).withBody("invalid payload")));

        PineconeVectorStore store = new PineconeVectorStore(
                new PineconeClientConfig("test-key", wireMockServer.baseUrl())
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                store.upsert(List.of(new VectorDocument(
                        "vec-1",
                        "doc-1",
                        "payments",
                        "conteudo",
                        Map.of(),
                        new float[]{0.1f, 0.2f}
                )))
        );

        assertTrue(exception.getMessage().contains("status 400"));
        assertTrue(exception.getMessage().contains("invalid payload"));
    }

    @Test
    void shouldThrowWhenPineconeReturnsServerError() {
        wireMockServer.stubFor(post(urlEqualTo("/query"))
                .willReturn(aResponse().withStatus(500).withBody("internal error")));

        PineconeVectorStore store = new PineconeVectorStore(
                new PineconeClientConfig("test-key", wireMockServer.baseUrl())
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                store.search(
                        VectorSearchQuery.builder()
                                .dataset("payments")
                                .query("consulta")
                                .topK(3)
                                .build(),
                        new float[]{0.1f, 0.2f}
                )
        );

        assertTrue(exception.getMessage().contains("status 500"));
        assertTrue(exception.getMessage().contains("internal error"));
    }
}

