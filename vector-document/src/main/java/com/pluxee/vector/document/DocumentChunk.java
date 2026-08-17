package com.pluxee.vector.document;

import java.util.Map;

public record DocumentChunk(
        String content,
        Map<String, Object> metadata
) {
}

