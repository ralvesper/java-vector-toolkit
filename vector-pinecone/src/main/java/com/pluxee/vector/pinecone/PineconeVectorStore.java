package com.pluxee.vector.pinecone;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluxee.vector.core.VectorDocument;
import com.pluxee.vector.core.VectorSearchQuery;
import com.pluxee.vector.core.VectorSearchResult;
import com.pluxee.vector.core.VectorStorePort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PineconeVectorStore implements VectorStorePort {

    private final PineconeClientConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PineconeVectorStore(PineconeClientConfig config) {
        this(config, HttpClient.newHttpClient(), new ObjectMapper());
    }

    public PineconeVectorStore(PineconeClientConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void upsert(List<VectorDocument> documents) {
        List<Map<String, Object>> vectors = new ArrayList<>();
        for (VectorDocument document : documents) {
            Map<String, Object> metadata = new HashMap<>(document.metadata());
            metadata.put("documentId", document.documentId());
            metadata.put("content", document.content());
            vectors.add(Map.of(
                    "id", document.id(),
                    "values", document.embedding(),
                    "metadata", metadata
            ));
        }
        send("/vectors/upsert", Map.of("vectors", vectors, "namespace", documents.isEmpty() ? "default" : documents.getFirst().dataset()));
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchQuery query, float[] queryEmbedding) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("vector", queryEmbedding);
        payload.put("topK", query.topK());
        payload.put("namespace", query.dataset());
        payload.put("includeMetadata", true);
        if (!query.filters().isEmpty()) {
            payload.put("filter", query.filters());
        }
        return parseMatches(send("/query", payload));
    }

    @Override
    public List<VectorSearchResult> findSimilarById(String dataset, String vectorId, int topK) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", vectorId);
        payload.put("namespace", dataset);
        payload.put("topK", topK + 1);
        payload.put("includeMetadata", true);

        List<VectorSearchResult> results = parseMatches(send("/query", payload));
        results.removeIf(result -> result.id().equals(vectorId));
        return results.size() > topK ? new ArrayList<>(results.subList(0, topK)) : results;
    }

    private List<VectorSearchResult> parseMatches(String response) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(response, new TypeReference<>() {
            });
            List<Map<String, Object>> matches = (List<Map<String, Object>>) parsed.getOrDefault("matches", List.of());
            List<VectorSearchResult> results = new ArrayList<>();
            for (Map<String, Object> match : matches) {
                Map<String, Object> metadata = (Map<String, Object>) match.getOrDefault("metadata", Map.of());
                results.add(new VectorSearchResult(
                        ((Number) match.getOrDefault("score", 0.0)).doubleValue(),
                        (String) match.getOrDefault("id", ""),
                        (String) metadata.getOrDefault("documentId", ""),
                        (String) metadata.getOrDefault("content", ""),
                        metadata
                ));
            }
            results.sort((a, b) -> Double.compare(b.score(), a.score()));
            return results;
        } catch (IOException e) {
            throw new IllegalStateException("unable to parse Pinecone query response", e);
        }
    }

    @Override
    public void deleteByDocumentId(String dataset, String documentId) {
        send("/vectors/delete", Map.of(
                "namespace", dataset,
                "filter", Map.of("documentId", documentId)
        ));
    }

    @Override
    public void deleteByDataset(String dataset) {
        send("/vectors/delete", Map.of(
                "namespace", dataset,
                "deleteAll", true
        ));
    }

    public boolean healthCheck() {
        String response = send("/describe_index_stats", Map.of());
        return response != null && !response.isBlank();
    }

    private String send(String endpoint, Map<String, Object> payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.host() + endpoint))
                    .header("Api-Key", config.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Pinecone request failed with status "
                        + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("unable to call Pinecone API", e);
        } catch (IOException e) {
            throw new IllegalStateException("unable to call Pinecone API", e);
        }
    }
}

