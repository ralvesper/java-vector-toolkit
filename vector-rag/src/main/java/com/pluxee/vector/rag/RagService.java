package com.pluxee.vector.rag;

import com.pluxee.vector.core.VectorKnowledgeBase;
import com.pluxee.vector.core.VectorSearchQuery;

public class RagService {

    private static final String RAG_PROMPT = """
            Voce e um assistente que responde perguntas sobre a arquitetura de sistemas da empresa.
            Responda EXCLUSIVAMENTE com base no contexto abaixo. Se o contexto nao tiver informacao
            suficiente, diga claramente que nao sabe. Cite os documentos/tickets entre colchetes,
            por exemplo [FS-699].

            CONTEXTO:
            %s

            PERGUNTA: %s
            """;

    private final VectorKnowledgeBase knowledgeBase;
    private final ContextBuilder contextBuilder;
    private final LlmClient llmClient;

    public RagService(VectorKnowledgeBase knowledgeBase, ContextBuilder contextBuilder) {
        this(knowledgeBase, contextBuilder, null);
    }

    public RagService(VectorKnowledgeBase knowledgeBase, ContextBuilder contextBuilder, LlmClient llmClient) {
        this.knowledgeBase = knowledgeBase;
        this.contextBuilder = contextBuilder;
        this.llmClient = llmClient;
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
        String answer;
        if (llmClient == null) {
            answer = "Contexto recuperado com " + searchResults.size() + " trecho(s). "
                    + "Pergunta: " + request.question();
        } else {
            answer = llmClient.complete(RAG_PROMPT.formatted(context, request.question()));
        }
        return new RagResponse(request.question(), context, answer);
    }
}
