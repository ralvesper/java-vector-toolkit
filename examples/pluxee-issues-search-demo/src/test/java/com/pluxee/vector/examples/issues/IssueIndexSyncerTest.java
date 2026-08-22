package com.pluxee.vector.examples.issues;

import com.pluxee.vector.core.HashingEmbeddingProvider;
import com.pluxee.vector.core.InMemoryVectorStore;
import com.pluxee.vector.core.VectorKnowledgeBase;
import com.pluxee.vector.core.VectorSearchQuery;
import com.pluxee.vector.document.FixedSizeChunkStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IssueIndexSyncerTest {

    @TempDir
    Path repoRoot;

    @TempDir
    Path stateDir;

    @Test
    void shouldAddUpdateAndRemoveAcrossSyncs() throws Exception {
        Files.createDirectories(repoRoot.resolve("dados/FS-1"));
        Files.writeString(repoRoot.resolve("dados/FS-1/a.md"), "conteudo original do arquivo a");
        Files.writeString(repoRoot.resolve("dados/FS-1/b.md"), "conteudo do arquivo b");

        var knowledgeBase = newVectorKnowledgeBase();
        IssueIndexSyncer syncer = new IssueIndexSyncer(
                knowledgeBase, "pluxee-issues", stateDir.resolve("state.json"));

        assertEquals(new IssueIndexSyncer.Stats(0, 0, 2, 0), syncer.sync(load()));
        assertTotalDocuments(knowledgeBase, 2);

        Files.writeString(repoRoot.resolve("dados/FS-1/a.md"), "conteudo alterado do arquivo a com texto diferente");
        Files.writeString(repoRoot.resolve("dados/FS-1/c.md"), "arquivo novo c");
        Files.deleteIfExists(repoRoot.resolve("dados/FS-1/b.md"));

        assertEquals(new IssueIndexSyncer.Stats(0, 1, 1, 1), syncer.sync(load()));
        assertTotalDocuments(knowledgeBase, 2);

        assertEquals(new IssueIndexSyncer.Stats(2, 0, 0, 0), syncer.sync(load()));
        assertTotalDocuments(knowledgeBase, 2);
    }

    private List<PluxeeIssuesLoader.IndexedIssue> load() {
        return new PluxeeIssuesLoader(repoRoot, new FixedSizeChunkStrategy(800, 100)).load();
    }

    private void assertTotalDocuments(VectorKnowledgeBase knowledgeBase, int expected) {
        var results = knowledgeBase.search(VectorSearchQuery.builder()
                .dataset("pluxee-issues")
                .query("conteudo")
                .topK(50)
                .build());
        long distinctDocs = results.stream().map(r -> r.metadata().get("arquivo")).distinct().count();
        assertEquals(expected, distinctDocs);
    }

    private VectorKnowledgeBase newVectorKnowledgeBase() {
        return new VectorKnowledgeBase(new HashingEmbeddingProvider(256), new InMemoryVectorStore());
    }
}
