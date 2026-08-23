package com.pluxee.vector.pinecone;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluxee.vector.core.VectorDocument;
import com.pluxee.vector.core.VectorSearchQuery;
import com.pluxee.vector.core.VectorSearchResult;
import com.pluxee.vector.core.VectorStorePort;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PineconeVectorStore implements VectorStorePort {

    private final PineconeClientConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private Integer indexDimension;

    public PineconeVectorStore(PineconeClientConfig config) {
        this(config, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(), new ObjectMapper());
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
            requireCompatibleDimension(document.embedding());
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
        requireCompatibleDimension(queryEmbedding);
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

    public List<String> listVectorIds(String dataset) {
        List<String> ids = new ArrayList<>();
        String paginationToken = null;
        do {
            StringBuilder path = new StringBuilder("/vectors/list?limit=100&namespace=").append(urlEncode(dataset));
            if (paginationToken != null) {
                path.append("&paginationToken=").append(urlEncode(paginationToken));
            }
            String response = sendGet(path.toString());
            try {
                Map<String, Object> parsed = objectMapper.readValue(response, new TypeReference<>() {
                });
                List<Map<String, Object>> vectors = (List<Map<String, Object>>) parsed.getOrDefault("vectors", List.of());
                for (Map<String, Object> vector : vectors) {
                    ids.add((String) vector.get("id"));
                }
                Map<String, Object> pagination = (Map<String, Object>) parsed.get("pagination");
                paginationToken = pagination == null ? null : (String) pagination.get("next");
            } catch (IOException e) {
                throw new IllegalStateException("unable to parse Pinecone list response", e);
            }
        } while (paginationToken != null);
        return ids;
    }

    public Map<String, Map<String, Object>> fetchMetadata(String dataset, List<String> ids) {
        Map<String, Map<String, Object>> metadataById = new HashMap<>();
        for (int start = 0; start < ids.size(); start += 100) {
            List<String> batch = ids.subList(start, Math.min(start + 100, ids.size()));
            StringBuilder path = new StringBuilder("/vectors/fetch?namespace=").append(urlEncode(dataset));
            for (String id : batch) {
                path.append("&ids=").append(urlEncode(id));
            }
            String response = sendGet(path.toString());
            try {
                Map<String, Object> parsed = objectMapper.readValue(response, new TypeReference<>() {
                });
                Map<String, Object> vectors = (Map<String, Object>) parsed.getOrDefault("vectors", Map.of());
                for (Map.Entry<String, Object> entry : vectors.entrySet()) {
                    Map<String, Object> vector = (Map<String, Object>) entry.getValue();
                    Map<String, Object> metadata = (Map<String, Object>) vector.getOrDefault("metadata", Map.of());
                    metadataById.put(entry.getKey(), metadata);
                }
            } catch (IOException e) {
                throw new IllegalStateException("unable to parse Pinecone fetch response", e);
            }
        }
        return metadataById;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String sendGet(String pathWithQuery) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.host() + pathWithQuery))
                    .header("Api-Key", config.apiKey())
                    .GET()
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

    public boolean healthCheck() {
        String response = send("/describe_index_stats", Map.of());
        return response != null && !response.isBlank();
    }

    private void requireCompatibleDimension(float[] vector) {
        if (indexDimension == null) {
            indexDimension = fetchIndexDimension();
        }
        if (indexDimension != null && vector.length != indexDimension) {
            throw new IllegalStateException("embedding dimension mismatch: vectors have " + vector.length
                    + " dims but the Pinecone index has " + indexDimension
                    + "; use an embedder with matching dimension or an index with matching dimension");
        }
    }

    private Integer fetchIndexDimension() {
        try {
            String response = send("/describe_index_stats", Map.of());
            Map<String, Object> parsed = objectMapper.readValue(response, new TypeReference<>() {
            });
            Object dimension = parsed.get("dimension");
            return dimension instanceof Number number ? number.intValue() : null;
        } catch (IOException e) {
            return null;
        } catch (IllegalStateException e) {
            return null;
        }
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

