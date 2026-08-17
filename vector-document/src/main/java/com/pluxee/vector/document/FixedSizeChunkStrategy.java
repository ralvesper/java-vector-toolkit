package com.pluxee.vector.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FixedSizeChunkStrategy implements ChunkStrategy {

    private final int size;
    private final int overlap;

    public FixedSizeChunkStrategy(int size, int overlap) {
        if (size < 64) {
            throw new IllegalArgumentException("size must be at least 64");
        }
        if (overlap < 0 || overlap >= size) {
            throw new IllegalArgumentException("overlap must be between 0 and size - 1");
        }
        this.size = size;
        this.overlap = overlap;
    }

    @Override
    public List<DocumentChunk> split(SourceDocument document) {
        String content = document.content();
        List<DocumentChunk> chunks = new ArrayList<>();
        int start = 0;
        int chunkIndex = 0;

        while (start < content.length()) {
            int end = Math.min(content.length(), start + size);
            String chunkText = content.substring(start, end);
            var metadata = new HashMap<>(document.metadata());
            metadata.put("chunkIndex", chunkIndex++);
            chunks.add(new DocumentChunk(chunkText, metadata));
            if (end == content.length()) {
                break;
            }
            start = end - overlap;
        }

        return chunks;
    }
}

