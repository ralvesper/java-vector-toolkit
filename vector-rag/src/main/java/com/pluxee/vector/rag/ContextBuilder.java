package com.pluxee.vector.rag;

import com.pluxee.vector.core.VectorSearchResult;

import java.util.List;

public interface ContextBuilder {

    String build(List<VectorSearchResult> results);
}

