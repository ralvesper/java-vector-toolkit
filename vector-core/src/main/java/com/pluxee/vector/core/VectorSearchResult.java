package com.pluxee.vector.core;

import java.util.Map;

public record VectorSearchResult(
        double score,
        String id,
        String documentId,
        String content,
        Map<String, Object> metadata
) {
}

