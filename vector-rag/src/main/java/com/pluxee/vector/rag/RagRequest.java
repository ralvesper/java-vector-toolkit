package com.pluxee.vector.rag;

public record RagRequest(String dataset, String question, int topK) {

    public static RagRequest of(String dataset, String question) {
        return new RagRequest(dataset, question, 5);
    }
}

