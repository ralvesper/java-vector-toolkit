package com.pluxee.vector.examples.issues;

import com.pluxee.vector.document.FixedSizeChunkStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluxeeIssuesLoaderTest {

    @TempDir
    Path repoRoot;

    @Test
    void shouldLoadMarkdownFilesWithTicketAndTipoMetadata() throws Exception {
        Files.createDirectories(repoRoot.resolve("dados/FS-677"));
        Files.writeString(repoRoot.resolve("dados/FS-677/investigacao-causa-raiz.md"), "# FS-677");
        Files.writeString(repoRoot.resolve("dados/FS-677/explicacao-fix-simples.md"), "fix");
        Files.writeString(repoRoot.resolve("issues.md"), "issues gerais");

        List<PluxeeIssuesLoader.IndexedIssue> issues = new PluxeeIssuesLoader(
                repoRoot,
                new FixedSizeChunkStrategy(800, 100)
        ).load();

        assertEquals(3, issues.size());

        PluxeeIssuesLoader.IndexedIssue investigacao = byId(issues, "dados/FS-677/investigacao-causa-raiz.md");
        assertEquals("FS-677", investigacao.chunks().getFirst().metadata().get("ticket"));
        assertEquals("investigacao", investigacao.chunks().getFirst().metadata().get("tipo"));
        assertEquals("# FS-677", investigacao.chunks().getFirst().content());

        PluxeeIssuesLoader.IndexedIssue fix = byId(issues, "dados/FS-677/explicacao-fix-simples.md");
        assertEquals("fix", fix.chunks().getFirst().metadata().get("tipo"));

        PluxeeIssuesLoader.IndexedIssue issuesGerais = byId(issues, "issues.md");
        assertEquals("", issuesGerais.chunks().getFirst().metadata().get("ticket"));
        assertEquals("issues", issuesGerais.chunks().getFirst().metadata().get("tipo"));
    }

    @Test
    void shouldSkipGitFolderAndNonMarkdownFiles() throws Exception {
        Files.createDirectories(repoRoot.resolve(".git"));
        Files.writeString(repoRoot.resolve(".git/config.md"), "git");
        Files.createDirectories(repoRoot.resolve("dados/FS-1"));
        Files.writeString(repoRoot.resolve("dados/FS-1/log.txt"), "log");
        Files.writeString(repoRoot.resolve("dados/FS-1/investigacao.md"), "conteudo real");

        List<PluxeeIssuesLoader.IndexedIssue> issues = new PluxeeIssuesLoader(
                repoRoot,
                new FixedSizeChunkStrategy(800, 100)
        ).load();

        assertEquals(1, issues.size());
        assertEquals("dados/FS-1/investigacao.md", issues.getFirst().documentId());
    }

    @Test
    void shouldSplitLargeFileIntoChunksWithChunkIndex() throws Exception {
        Files.createDirectories(repoRoot.resolve("dados/FS-2"));
        String content = "x".repeat(2000);
        Files.writeString(repoRoot.resolve("dados/FS-2/investigacao-longa.md"), content);

        List<PluxeeIssuesLoader.IndexedIssue> issues = new PluxeeIssuesLoader(
                repoRoot,
                new FixedSizeChunkStrategy(800, 100)
        ).load();

        List<com.pluxee.vector.document.DocumentChunk> chunks = issues.getFirst().chunks();
        assertTrue(chunks.size() >= 3);
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).metadata().get("chunkIndex"));
        }
    }

    @Test
    void shouldFailWhenRepositoryHasNoMarkdownFiles() throws Exception {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PluxeeIssuesLoader(repoRoot, new FixedSizeChunkStrategy(800, 100)).load()
        );
        assertTrue(exception.getMessage().contains("no markdown files found"));
    }

    private PluxeeIssuesLoader.IndexedIssue byId(List<PluxeeIssuesLoader.IndexedIssue> issues, String id) {
        return issues.stream()
                .filter(issue -> issue.documentId().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
