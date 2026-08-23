package com.pluxee.vector.examples.issues;

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

public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    public static final String DEFAULT_MODEL = "text-embedding-3-small";
    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final int MAX_ATTEMPTS = 5;

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiEmbeddingProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, DEFAULT_MODEL);
    }

    public OpenAiEmbeddingProvider(String apiKey, String baseUrl, String model) {
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
                    .uri(URI.create(baseUrl + "/embeddings"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                            "model", model,
                            "input", content
                    ))))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                Long retryAfterMs = parseRetryAfterMs(response);
                if (retryAfterMs == null) {
                    throw new IllegalStateException("OpenAI embeddings request failed with status "
                            + response.statusCode() + ": " + response.body());
                }
                throw new RateLimitedException(retryAfterMs);
            }
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI embeddings request failed with status "
                        + response.statusCode() + ": " + response.body());
            }

            Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            List<?> data = (List<?>) parsed.get("data");
            Map<String, Object> first = (Map<String, Object>) data.getFirst();
            List<Number> embedding = (List<Number>) first.get("embedding");
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = embedding.get(i).floatValue();
            }
            return vector;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("unable to call OpenAI embeddings API", e);
        } catch (IOException e) {
            throw new IllegalStateException("unable to call OpenAI embeddings API", e);
        } catch (RateLimitedException e) {
            throw e;
        }
    }

    private Long parseRetryAfterMs(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try {
                        return Long.parseLong(value.trim()) * 1_000;
                    } catch (NumberFormatException e) {
                        return 5_000L;
                    }
                })
                .orElse(null);
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
            super("rate limited by OpenAI API");
            this.retryAfterMs = retryAfterMs;
        }

        private IllegalStateException toIllegalState() {
            return new IllegalStateException("OpenAI embeddings request rate limited after retries");
        }
    }
}
