package com.pluxee.vector.pinecone;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PineconeVectorStoreListFetchTest {

    private WireMockServer wireMockServer;
    private PineconeVectorStore store;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
        store = new PineconeVectorStore(new PineconeClientConfig("test-key", wireMockServer.baseUrl()));
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void shouldListVectorIdsAcrossPages() {
        wireMockServer.stubFor(get(urlPathEqualTo("/vectors/list"))
                .withQueryParam("limit", equalTo("100"))
                .withQueryParam("namespace", equalTo("code"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {
                          "vectors": [ { "id": "code-A-0" }, { "id": "code-A-1" } ],
                          "pagination": { "next": "token-1" }
                        }
                        """)));
        wireMockServer.stubFor(get(urlPathEqualTo("/vectors/list"))
                .withQueryParam("paginationToken", equalTo("token-1"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {
                          "vectors": [ { "id": "code-B-0" } ]
                        }
                        """)));

        List<String> ids = store.listVectorIds("code");

        assertEquals(List.of("code-A-0", "code-A-1", "code-B-0"), ids);
    }

    @Test
    void shouldReturnEmptyListWhenNamespaceIsEmpty() {
        wireMockServer.stubFor(get(urlPathEqualTo("/vectors/list"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        assertTrue(store.listVectorIds("code").isEmpty());
    }

    @Test
    void shouldFetchMetadataByIds() {
        wireMockServer.stubFor(get(urlPathEqualTo("/vectors/fetch"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {
                          "vectors": {
                            "code-src/Main.java-0": {
                              "id": "code-src/Main.java-0",
                              "metadata": { "fileHash": "abc123", "arquivo": "src/Main.java" }
                            },
                            "code-src/Util.java-2": {
                              "id": "code-src/Util.java-2",
                              "metadata": { "fileHash": "def456" }
                            }
                          }
                        }
                        """)));

        Map<String, Map<String, Object>> metadata =
                store.fetchMetadata("code", List.of("code-src/Main.java-0", "code-src/Util.java-2"));

        assertEquals("abc123", metadata.get("code-src/Main.java-0").get("fileHash"));
        assertEquals("src/Main.java", metadata.get("code-src/Main.java-0").get("arquivo"));
        assertEquals("def456", metadata.get("code-src/Util.java-2").get("fileHash"));
    }

    @Test
    void shouldUrlEncodeIdsAndNamespaceOnFetch() {
        wireMockServer.stubFor(get(urlEqualTo("/vectors/fetch?namespace=my%20proj&ids=code-a%2Fb%20c-0"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        store.fetchMetadata("my proj", List.of("code-a/b c-0"));

        wireMockServer.verify(getRequestedFor(urlEqualTo("/vectors/fetch?namespace=my%20proj&ids=code-a%2Fb%20c-0")));
    }

    @Test
    void shouldFailFastOnErrorStatus() {
        wireMockServer.stubFor(get(urlPathEqualTo("/vectors/list"))
                .willReturn(aResponse().withStatus(403).withBody("forbidden")));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                store.listVectorIds("code")
        );
        assertTrue(exception.getMessage().contains("status 403"));
    }
}
