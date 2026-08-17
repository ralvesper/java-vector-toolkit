package com.pluxee.vector.document;

import java.util.Map;

public record SourceDocument(
        String documentId,
        String content,
        Map<String, Object> metadata
) {
}

