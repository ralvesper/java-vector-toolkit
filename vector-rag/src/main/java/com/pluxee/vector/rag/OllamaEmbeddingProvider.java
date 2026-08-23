package com.pluxee.vector.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluxee.vector.core.EmbeddingProvider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class OllamaEmbeddingProvider implements EmbeddingProvider {

    public static final String DEFAULT_BASE_URL = OllamaLlmClient.DEFAULT_BASE_URL;

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaEmbeddingProvider(String model) {
        this(DEFAULT_BASE_URL, model);
    }

    public OllamaEmbeddingProvider(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public float[] embed(String text) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embed"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                            "model", model,
                            "input", text
                    ))))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Ollama embedding request failed with status "
                        + response.statusCode() + ": " + response.body());
            }

            Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            List<List<Double>> embeddings = (List<List<Double>>) parsed.get("embeddings");
            if (embeddings == null || embeddings.isEmpty()) {
                throw new IllegalStateException("Ollama embedding returned no vectors: " + response.body());
            }
            float[] vector = new float[embeddings.getFirst().size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = embeddings.getFirst().get(i).floatValue();
            }
            return vector;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("unable to call Ollama embedding API", e);
        } catch (IOException e) {
            throw new IllegalStateException("unable to call Ollama embedding API", e);
        }
    }
}
