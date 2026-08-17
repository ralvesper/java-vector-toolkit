package com.pluxee.vector.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record VectorSearchQuery(
        String query,
        String dataset,
        int topK,
        Map<String, Object> filters
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String query;
        private String dataset;
        private int topK = 5;
        private final Map<String, Object> filters = new HashMap<>();

        private Builder() {
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder dataset(String dataset) {
            this.dataset = dataset;
            return this;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public Builder filter(String key, Object value) {
            this.filters.put(key, value);
            return this;
        }

        public VectorSearchQuery build() {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("query must not be blank");
            }
            if (dataset == null || dataset.isBlank()) {
                throw new IllegalArgumentException("dataset must not be blank");
            }
            if (topK < 1) {
                throw new IllegalArgumentException("topK must be greater than 0");
            }
            return new VectorSearchQuery(query, dataset, topK, Collections.unmodifiableMap(new HashMap<>(filters)));
        }
    }
}

