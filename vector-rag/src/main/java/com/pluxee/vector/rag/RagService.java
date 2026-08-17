package com.pluxee.vector.rag;

import com.pluxee.vector.core.VectorKnowledgeBase;
import com.pluxee.vector.core.VectorSearchQuery;

public class RagService {

    private final VectorKnowledgeBase knowledgeBase;
    private final ContextBuilder contextBuilder;

    public RagService(VectorKnowledgeBase knowledgeBase, ContextBuilder contextBuilder) {
        this.knowledgeBase = knowledgeBase;
        this.contextBuilder = contextBuilder;
    }

    public RagResponse ask(RagRequest request) {
        var searchResults = knowledgeBase.search(
                VectorSearchQuery.builder()
                        .dataset(request.dataset())
                        .query(request.question())
                        .topK(request.topK())
                        .build()
        );

        String context = contextBuilder.build(searchResults);
        String answer = "Contexto recuperado com " + searchResults.size() + " trecho(s). "
                + "Pergunta: " + request.question();
        return new RagResponse(request.question(), context, answer);
    }
}

