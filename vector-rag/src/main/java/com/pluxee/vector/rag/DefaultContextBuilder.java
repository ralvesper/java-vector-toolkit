package com.pluxee.vector.rag;

import com.pluxee.vector.core.VectorSearchResult;

import java.util.List;

public class DefaultContextBuilder implements ContextBuilder {

    @Override
    public String build(List<VectorSearchResult> results) {
        StringBuilder builder = new StringBuilder();
        for (VectorSearchResult result : results) {
            builder.append("SOURCE: ")
                    .append(result.documentId())
                    .append(System.lineSeparator())
                    .append(result.content())
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }
        return builder.toString().trim();
    }
}

