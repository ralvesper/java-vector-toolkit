package com.pluxee.vector.document;

import java.util.List;

public interface ChunkStrategy {

    List<DocumentChunk> split(SourceDocument document);
}

