package com.pluxee.vector.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class GeminiLlmClient implements LlmClient {

    public static final String DEFAULT_MODEL = "gemini-2.5-flash";
    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final int MAX_ATTEMPTS = 5;

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiLlmClient(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, DEFAULT_MODEL);
    }

    public GeminiLlmClient(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String complete(String prompt) {
        IllegalStateException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return doComplete(prompt);
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

    private String doComplete(String prompt) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/models/" + model + ":generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
                    ))))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new RateLimitedException(parseRetryDelayMs(response.body()));
            }
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Gemini generation request failed with status "
                        + response.statusCode() + ": " + response.body());
            }

            Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) parsed.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new IllegalStateException("Gemini generation returned no candidates: " + response.body());
            }
            Map<String, Object> content = (Map<String, Object>) candidates.getFirst().get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            StringBuilder text = new StringBuilder();
            for (Map<String, Object> part : parts) {
                text.append((String) part.getOrDefault("text", ""));
            }
            return text.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("unable to call Gemini generation API", e);
        } catch (IOException e) {
            throw new IllegalStateException("unable to call Gemini generation API", e);
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
            return new IllegalStateException("Gemini generation request rate limited after retries");
        }
    }
}
