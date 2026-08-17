package com.pluxee.vector.core;

import java.util.Map;

public record VectorDocument(
        String id,
        String documentId,
        String dataset,
        String content,
        Map<String, Object> metadata,
        float[] embedding
) {
}

