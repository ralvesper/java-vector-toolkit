package com.pluxee.vector.examples.source;

import com.pluxee.vector.core.EmbeddingProvider;
import com.pluxee.vector.core.HashingEmbeddingProvider;
import com.pluxee.vector.core.InMemoryVectorStore;
import com.pluxee.vector.core.VectorKnowledgeBase;
import com.pluxee.vector.core.VectorSearchQuery;
import com.pluxee.vector.core.VectorSearchResult;
import com.pluxee.vector.document.FixedSizeChunkStrategy;
import com.pluxee.vector.rag.OllamaEmbeddingProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

public class SourceCodeSearchDemoApplication {

    private static final String DATASET = "source-code";
    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 100;

    public static void main(String[] args) {
        Path repo = repoPath(args);
        int topK = argValue(args, "--topk").map(Integer::parseInt).orElse(5);

        EmbeddingProvider embeddings = resolveEmbeddings();
        System.out.println("Embedder: " + embeddings.getClass().getSimpleName());

        VectorKnowledgeBase knowledgeBase = new VectorKnowledgeBase(embeddings, new InMemoryVectorStore());
        indexRepository(knowledgeBase, repo);

        Optional<String> similar = argValue(args, "--similar");
        if (similar.isPresent()) {
            runSimilar(knowledgeBase, similar.get(), topK);
            return;
        }

        Optional<String> query = argValue(args, "--query");
        if (query.isPresent() && !query.get().isBlank()) {
            runQuery(knowledgeBase, query.get(), topK);
            return;
        }

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("\nconsulta> ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String line = scanner.nextLine().trim();
                if (line.isEmpty() || line.equalsIgnoreCase("sair") || line.equalsIgnoreCase("exit")) {
                    break;
                }
                if (line.startsWith("similar ")) {
                    runSimilar(knowledgeBase, line.substring("similar ".length()).trim(), topK);
                    continue;
                }
                runQuery(knowledgeBase, line, topK);
            }
        }
    }

    private static void indexRepository(VectorKnowledgeBase knowledgeBase, Path repo) {
        long start = System.currentTimeMillis();
        JavaCodeLoader loader = new JavaCodeLoader(repo, new FixedSizeChunkStrategy(CHUNK_SIZE, CHUNK_OVERLAP));
        List<JavaCodeLoader.IndexedFile> files = loader.load();

        int totalChunks = 0;
        for (JavaCodeLoader.IndexedFile file : files) {
            List<VectorKnowledgeBase.ChunkInput> chunks = file.chunks().stream()
                    .map(chunk -> new VectorKnowledgeBase.ChunkInput(chunk.content(), chunk.metadata()))
                    .toList();
            knowledgeBase.index(DATASET, file.documentId(), chunks);
            totalChunks += chunks.size();
        }

        System.out.printf("Indexados %d arquivos Java (%d chunks) de %s em %d ms%n",
                files.size(),
                totalChunks,
                repo,
                System.currentTimeMillis() - start
        );
    }

    private static void runQuery(VectorKnowledgeBase knowledgeBase, String query, int topK) {
        List<VectorSearchResult> results = knowledgeBase.search(
                VectorSearchQuery.builder()
                        .dataset(DATASET)
                        .query(query)
                        .topK(topK)
                        .build()
        );

        printResults(results, false);
    }

    private static void runSimilar(VectorKnowledgeBase knowledgeBase, String documentId, int topK) {
        String normalized = documentId.replace('\\', '/');
        String vectorId = DATASET + "-" + normalized + "-0";
        try {
            List<VectorSearchResult> results = knowledgeBase.findSimilar(DATASET, vectorId, topK);
            System.out.println("\nCodigo similar a " + normalized + ":");
            printResults(results, true);
        } catch (IllegalArgumentException e) {
            System.out.println("Arquivo nao indexado: " + normalized
                    + " (use o caminho relativo mostrado na indexacao)");
        }
    }

    private static void printResults(List<VectorSearchResult> results, boolean percent) {
        if (results.isEmpty()) {
            System.out.println("Nenhum resultado.");
            return;
        }
        int position = 1;
        for (VectorSearchResult result : results) {
            Map<String, Object> metadata = result.metadata();
            String arquivo = String.valueOf(metadata.getOrDefault("arquivo", result.documentId()));
            Object chunkIndex = metadata.get("chunkIndex");
            String classe = String.valueOf(metadata.getOrDefault("classe", "-"));

            String similarity = percent
                    ? String.format("%3d%% similar", Math.round(result.score() * 100))
                    : String.format("score=%.4f", result.score());
            System.out.printf("%d. %s -> %s%s [%s]%n",
                    position++,
                    similarity,
                    arquivo,
                    chunkIndex != null ? " (chunk " + chunkIndex + ")" : "",
                    classe
            );
            System.out.println("   " + preview(result.content()));
        }
    }

    private static String preview(String content) {
        String singleLine = content.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 140 ? singleLine : singleLine.substring(0, 140) + "...";
    }

    private static EmbeddingProvider resolveEmbeddings() {
        String provider = System.getenv("EMBEDDINGS_PROVIDER");
        if ("ollama".equalsIgnoreCase(provider)) {
            String baseUrl = orDefault(System.getenv("OLLAMA_BASE_URL"), OllamaEmbeddingProvider.DEFAULT_BASE_URL);
            String model = orDefault(System.getenv("OLLAMA_EMBEDDING_MODEL"), "nomic-embed-text");
            System.out.println("Ollama: " + baseUrl + " modelo " + model);
            return new OllamaEmbeddingProvider(baseUrl, model);
        }
        if ("hashing".equalsIgnoreCase(provider)) {
            System.out.println("EMBEDDINGS_PROVIDER=hashing: matching lexical, sem semantica real");
        } else {
            System.out.println("Nenhum provider configurado: usando HashingEmbeddingProvider (matching lexical, sem semantica real)");
        }
        return new HashingEmbeddingProvider(768);
    }

    private static Path repoPath(String[] args) {
        String path = argValue(args, "--repo")
                .orElse(".");
        Path resolved = Path.of(path).toAbsolutePath().normalize();
        if (!Files.isDirectory(resolved)) {
            throw new IllegalStateException("repository not found at " + resolved);
        }
        return resolved;
    }

    private static Optional<String> argValue(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(flag)) {
                return Optional.of(args[i + 1]);
            }
        }
        return Optional.empty();
    }

    private static String orDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
