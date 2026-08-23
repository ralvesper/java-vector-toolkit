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

public class GeminiEmbeddingProvider implements EmbeddingProvider {

    public static final String DEFAULT_MODEL = "gemini-embedding-001";
    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    public static final int OUTPUT_DIMENSIONALITY = 1536;
    private static final int MAX_ATTEMPTS = 5;

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiEmbeddingProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, DEFAULT_MODEL);
    }

    public GeminiEmbeddingProvider(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public float[] embed(String content) {
        IllegalStateException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return doEmbed(content);
            } catch (RateLimitedException e) {
                lastError = e.toIllegalState();
                if (attempt == MAX_ATTEMPTS) {
                    break;
                }
                sleepQuietly(e.retryAfterMs);
            }
        }
        throw lastError;
    }

    private float[] doEmbed(String content) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/models/" + model + ":embedContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                            "content", Map.of("parts", List.of(Map.of("text", content))),
                            "outputDimensionality", OUTPUT_DIMENSIONALITY
                    ))))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new RateLimitedException(parseRetryDelayMs(response.body()));
            }
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Gemini embeddings request failed with status "
                        + response.statusCode() + ": " + response.body());
            }

            Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            Map<String, Object> embedding = (Map<String, Object>) parsed.get("embedding");
            List<Number> values = (List<Number>) embedding.get("values");
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i).floatValue();
            }
            return vector;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("unable to call Gemini embeddings API", e);
        } catch (IOException e) {
            throw new IllegalStateException("unable to call Gemini embeddings API", e);
        } catch (RateLimitedException e) {
            throw e;
        }
    }

    private long parseRetryDelayMs(String body) {
        try {
            int index = body.indexOf("\"retryDelay\"");
            if (index < 0) {
                return 5_000;
            }
            String tail = body.substring(index);
            String value = tail.split("\"")[2];
            long seconds = Long.parseLong(value.replaceAll("[^0-9]", ""));
            return seconds * 1_000;
        } catch (Exception e) {
            return 5_000;
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(Math.min(millis, 65_000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for quota", e);
        }
    }

    private static final class RateLimitedException extends RuntimeException {

        private final long retryAfterMs;

        private RateLimitedException(long retryAfterMs) {
            super("rate limited by Gemini API");
            this.retryAfterMs = retryAfterMs;
        }

        private IllegalStateException toIllegalState() {
            return new IllegalStateException("Gemini embeddings request rate limited after retries");
        }
    }
}
