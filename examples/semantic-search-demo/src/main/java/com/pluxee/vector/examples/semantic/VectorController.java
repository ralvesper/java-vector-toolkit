package com.pluxee.vector.examples.semantic;

import com.pluxee.vector.core.VectorKnowledgeBase;
import com.pluxee.vector.core.VectorSearchQuery;
import com.pluxee.vector.document.ChunkStrategy;
import com.pluxee.vector.document.SourceDocument;
import com.pluxee.vector.rag.RagRequest;
import com.pluxee.vector.rag.RagResponse;
import com.pluxee.vector.rag.RagService;
import com.pluxee.vector.starter.VectorProperties;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vector")
public class VectorController {

    private final VectorKnowledgeBase knowledgeBase;
    private final RagService ragService;
    private final ChunkStrategy chunkStrategy;
    private final VectorProperties properties;

    public VectorController(
            VectorKnowledgeBase knowledgeBase,
            RagService ragService,
            ChunkStrategy chunkStrategy,
            VectorProperties properties
    ) {
        this.knowledgeBase = knowledgeBase;
        this.ragService = ragService;
        this.chunkStrategy = chunkStrategy;
        this.properties = properties;
    }

    @PostMapping("/documents")
    public Map<String, Object> index(@RequestBody IndexRequest request) {
        SourceDocument sourceDocument = new SourceDocument(
                request.documentId(),
                request.content(),
                request.metadata() == null ? Map.of() : request.metadata()
        );

        List<VectorKnowledgeBase.ChunkInput> chunks = chunkStrategy.split(sourceDocument).stream()
                .map(chunk -> new VectorKnowledgeBase.ChunkInput(chunk.content(), chunk.metadata()))
                .toList();

        knowledgeBase.reindex(request.dataset(), request.documentId(), chunks);
        return Map.of("indexedChunks", chunks.size(), "dataset", request.dataset(), "documentId", request.documentId());
    }

    @PostMapping("/search")
    public List<SearchItem> search(@RequestBody SearchRequest request) {
        int topK = request.topK() != null ? request.topK() : properties.getSearch().getTopK();
        var queryBuilder = VectorSearchQuery.builder()
                .dataset(request.dataset())
                .query(request.query())
                .topK(topK);

        if (request.filters() != null) {
            request.filters().forEach(queryBuilder::filter);
        }

        return knowledgeBase.search(queryBuilder.build()).stream()
                .map(result -> new SearchItem(
                        result.score(),
                        result.documentId(),
                        result.content(),
                        result.metadata()
                ))
                .toList();
    }

    @PostMapping("/ask")
    public RagResponse ask(@RequestBody AskRequest request) {
        return ragService.ask(RagRequest.of(request.dataset(), request.question()));
    }

    @GetMapping("/similar/{dataset}/{vectorId}")
    public List<SearchItem> findSimilar(
            @PathVariable("dataset") String dataset,
            @PathVariable("vectorId") String vectorId,
            @RequestParam(name = "topK", defaultValue = "5") int topK
    ) {
        return knowledgeBase.findSimilar(dataset, vectorId, topK).stream()
                .map(result -> new SearchItem(
                        result.score(),
                        result.documentId(),
                        result.content(),
                        result.metadata()
                ))
                .toList();
    }

    @DeleteMapping("/documents/{dataset}/{documentId}")
    public Map<String, Object> deleteByDocumentId(
            @PathVariable("dataset") String dataset,
            @PathVariable("documentId") String documentId
    ) {
        knowledgeBase.deleteByDocumentId(dataset, documentId);
        return Map.of("deleted", true, "dataset", dataset, "documentId", documentId);
    }

    public record IndexRequest(String dataset, String documentId, String content, Map<String, Object> metadata) {
    }

    public record SearchRequest(String dataset, String query, Integer topK, Map<String, Object> filters) {
    }

    public record AskRequest(String dataset, String question) {
    }

    public record SearchItem(double score, String documentId, String content, Map<String, Object> metadata) {
    }
}

