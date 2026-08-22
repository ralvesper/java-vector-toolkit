package com.pluxee.vector.examples.issues;

import com.pluxee.vector.core.HashingEmbeddingProvider;
import com.pluxee.vector.core.EmbeddingProvider;
import com.pluxee.vector.core.InMemoryVectorStore;
import com.pluxee.vector.core.VectorKnowledgeBase;
import com.pluxee.vector.core.VectorSearchQuery;
import com.pluxee.vector.core.VectorSearchResult;
import com.pluxee.vector.document.FixedSizeChunkStrategy;
import com.pluxee.vector.pinecone.PineconeClientConfig;
import com.pluxee.vector.pinecone.PineconeVectorStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class PluxeeIssuesSearchApplication {

    private static final String DATASET = "pluxee-issues";
    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 100;

    public static void main(String[] args) throws Exception {
        Path repo = repoPath(args);
        int topK = argValue(args, "--topk")
                .map(Integer::parseInt)
                .orElse(5);
        boolean syncMode = hasFlag(args, "--sync");

        EmbeddingProvider embeddings = resolveEmbeddings();
        System.out.println("Embedder: " + embeddings.getClass().getSimpleName());

        com.pluxee.vector.core.VectorStorePort store = resolveStore();
        var knowledgeBase = new VectorKnowledgeBase(embeddings, store);

        if (store instanceof PineconeVectorStore) {
            int dimensions = embeddings.embed("dimension probe").length;
            if (dimensions != 1536) {
                throw new IllegalStateException("embedder produces " + dimensions
                        + " dims but the Pinecone index has 1536; use an in-memory store or an index with matching dimension");
            }
        }

        if (syncMode) {
            Path stateFile = statePath(args);
            System.out.println("Modo incremental (--sync), estado: " + stateFile);
            IssueIndexSyncer syncer = new IssueIndexSyncer(knowledgeBase, DATASET, stateFile);
            PluxeeIssuesLoader loader = new PluxeeIssuesLoader(repo, new FixedSizeChunkStrategy(CHUNK_SIZE, CHUNK_OVERLAP));
            IssueIndexSyncer.Stats stats = syncer.sync(loader.load());
            System.out.printf("Sync: %d inalterados, %d atualizados, %d novos, %d removidos%n",
                    stats.unchanged(), stats.updated(), stats.added(), stats.removed());
        } else {
            if (store instanceof PineconeVectorStore pineconeStore) {
                System.out.println("Store: Pinecone - limpando namespace " + DATASET + " antes de reindexar");
                clearNamespaceIgnoringMissing(pineconeStore);
            }
            indexRepository(knowledgeBase, repo, statePath(args));
        }

        String query = argValue(args, "--query").orElse(null);
        if (query != null && !query.isBlank()) {
            runQuery(knowledgeBase, query, topK);
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
                runQuery(knowledgeBase, line, topK);
            }
        }
    }

    private static void clearNamespaceIgnoringMissing(PineconeVectorStore store) {
        try {
            store.deleteByDataset(DATASET);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                System.out.println("Namespace " + DATASET + " ainda nao existe no indice; seguindo com indexacao");
                return;
            }
            throw e;
        }
    }

    private static void indexRepository(VectorKnowledgeBase knowledgeBase, Path repo, Path stateFile) {
        long start = System.currentTimeMillis();
        PluxeeIssuesLoader loader = new PluxeeIssuesLoader(
                repo,
                new FixedSizeChunkStrategy(CHUNK_SIZE, CHUNK_OVERLAP)
        );
        List<PluxeeIssuesLoader.IndexedIssue> issues = loader.load();

        int totalChunks = 0;
        for (PluxeeIssuesLoader.IndexedIssue issue : issues) {
            List<VectorKnowledgeBase.ChunkInput> chunks = issue.chunks().stream()
                    .map(chunk -> new VectorKnowledgeBase.ChunkInput(chunk.content(), chunk.metadata()))
                    .toList();
            knowledgeBase.index(DATASET, issue.documentId(), chunks);
            totalChunks += issue.chunks().size();
        }
        new IssueIndexSyncer(knowledgeBase, DATASET, stateFile).saveStateOnly(issues);

        System.out.printf("Indexados %d documentos (%d chunks) de %s em %d ms%n",
                issues.size(),
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

        if (results.isEmpty()) {
            System.out.println("Nenhum resultado.");
            return;
        }
        int position = 1;
        for (VectorSearchResult result : results) {
            Map<String, Object> metadata = result.metadata();
            String ticket = String.valueOf(metadata.getOrDefault("ticket", ""));
            String arquivo = String.valueOf(metadata.getOrDefault("arquivo", result.documentId()));
            Object chunkIndex = metadata.get("chunkIndex");

            System.out.printf("%d. score=%.4f  [%s] %s%s%n",
                    position++,
                    result.score(),
                    ticket.isEmpty() ? "-" : ticket,
                    arquivo,
                    chunkIndex != null ? " (chunk " + chunkIndex + ")" : ""
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
        if ("hashing".equalsIgnoreCase(provider)) {
            System.out.println("EMBEDDINGS_PROVIDER=hashing: matching lexical, sem semantica real");
            return new HashingEmbeddingProvider(1536);
        }
        if ("ollama".equalsIgnoreCase(provider)) {
            String baseUrl = orDefault(System.getenv("OLLAMA_BASE_URL"), OllamaEmbeddingProvider.DEFAULT_BASE_URL);
            String model = orDefault(System.getenv("OLLAMA_EMBEDDING_MODEL"), OllamaEmbeddingProvider.DEFAULT_MODEL);
            System.out.println("Ollama: " + baseUrl + " modelo " + model);
            return new OllamaEmbeddingProvider(baseUrl, model, System.getenv("OLLAMA_API_KEY"));
        }
        if ("openai".equalsIgnoreCase(provider) && hasText(System.getenv("OPENAI_API_KEY"))) {
            return new OpenAiEmbeddingProvider(requireEnv("OPENAI_API_KEY"));
        }
        if (hasText(System.getenv("GEMINI_API_KEY"))) {
            return new GeminiEmbeddingProvider(requireEnv("GEMINI_API_KEY"));
        }
        if (hasText(System.getenv("OPENAI_API_KEY"))) {
            return new OpenAiEmbeddingProvider(requireEnv("OPENAI_API_KEY"));
        }
        System.out.println("Nenhuma chave de embeddings encontrada: usando HashingEmbeddingProvider (matching lexical, sem semantica real)");
        return new HashingEmbeddingProvider(1536);
    }

    private static String orDefault(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equals(flag)) {
                return true;
            }
        }
        return false;
    }

    private static Path statePath(String[] args) {
        String path = argValue(args, "--state").orElse(null);
        if (!hasText(path)) {
            path = System.getProperty("user.home") + "/.java-vector-toolkit/" + DATASET + "-state.json";
        }
        return Path.of(path).toAbsolutePath().normalize();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static com.pluxee.vector.core.VectorStorePort resolveStore() {
        String provider = System.getenv("ISSUES_STORE");
        if ("pinecone".equalsIgnoreCase(provider)) {
            String apiKey = requireEnv("PINECONE_API_KEY");
            String host = requireEnv("PINECONE_HOST");
            return new PineconeVectorStore(new PineconeClientConfig(apiKey, host));
        }
        return new InMemoryVectorStore();
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when ISSUES_STORE=pinecone");
        }
        return value;
    }

    private static Path repoPath(String[] args) {
        String path = argValue(args, "--repo").orElse(null);
        if (path == null || path.isBlank()) {
            path = System.getenv("PLUXEE_ISSUES_HOME");
        }
        if (path == null || path.isBlank()) {
            path = "../core-backoffice/pluxee-issues";
        }
        Path resolved = Path.of(path).toAbsolutePath().normalize();
        if (!Files.isDirectory(resolved)) {
            throw new IllegalStateException("pluxee-issues repository not found at " + resolved);
        }
        return resolved;
    }

    private static java.util.Optional<String> argValue(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(flag)) {
                return java.util.Optional.of(args[i + 1]);
            }
        }
        return java.util.Optional.empty();
    }
}
