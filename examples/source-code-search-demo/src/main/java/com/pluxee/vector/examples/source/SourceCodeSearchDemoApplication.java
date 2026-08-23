package com.pluxee.vector.examples.source;

import com.pluxee.vector.core.EmbeddingProvider;
import com.pluxee.vector.core.HashingEmbeddingProvider;
import com.pluxee.vector.core.InMemoryVectorStore;
import com.pluxee.vector.core.VectorKnowledgeBase;
import com.pluxee.vector.core.VectorSearchQuery;
import com.pluxee.vector.core.VectorSearchResult;
import com.pluxee.vector.document.FixedSizeChunkStrategy;
import com.pluxee.vector.pinecone.PineconeClientConfig;
import com.pluxee.vector.pinecone.PineconeVectorStore;
import com.pluxee.vector.rag.GeminiEmbeddingProvider;
import com.pluxee.vector.rag.OllamaEmbeddingProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;

public class SourceCodeSearchDemoApplication {

    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 100;

    public static void main(String[] args) {
        Path repo = repoPath(args);
        int topK = argValue(args, "--topk").map(Integer::parseInt).orElse(5);
        boolean usePinecone = argValue(args, "--store")
                .map(value -> value.equalsIgnoreCase("pinecone"))
                .orElse(false);
        boolean forceReindex = hasFlag(args, "--reindex");

        String dataset = "source-code-" + slug(repo.getFileName().toString());
        EmbeddingProvider embeddings = resolveEmbeddings(usePinecone);
        System.out.println("Dataset: " + dataset + (usePinecone ? " (Pinecone, incremental)" : " (memória)"));

        VectorKnowledgeBase knowledgeBase;
        PineconeVectorStore pinecone = null;
        if (usePinecone) {
            pinecone = new PineconeVectorStore(pineconeConfig());
            knowledgeBase = new VectorKnowledgeBase(embeddings, pinecone);
        } else {
            knowledgeBase = new VectorKnowledgeBase(embeddings, new InMemoryVectorStore());
        }

        sync(knowledgeBase, pinecone, repo, dataset, forceReindex);

        Optional<String> similar = argValue(args, "--similar");
        if (similar.isPresent()) {
            runSimilar(knowledgeBase, dataset, similar.get(), topK);
            return;
        }

        Optional<String> query = argValue(args, "--query");
        if (query.isPresent() && !query.get().isBlank()) {
            runQuery(knowledgeBase, dataset, query.get(), topK);
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
                    runSimilar(knowledgeBase, dataset, line.substring("similar ".length()).trim(), topK);
                    continue;
                }
                runQuery(knowledgeBase, dataset, line, topK);
            }
        }
    }

    private static void sync(VectorKnowledgeBase knowledgeBase, PineconeVectorStore store,
                             Path repo, String dataset, boolean forceReindex) {
        long start = System.currentTimeMillis();
        JavaCodeLoader loader = new JavaCodeLoader(repo, new FixedSizeChunkStrategy(CHUNK_SIZE, CHUNK_OVERLAP));
        List<JavaCodeLoader.IndexedFile> files = loader.load();

        Map<String, String> hashes = new HashMap<>();
        for (JavaCodeLoader.IndexedFile file : files) {
            hashes.put(file.documentId(), sha256(repo.resolve(file.documentId())));
        }

        if (store == null) {
            indexFiles(knowledgeBase, dataset, files, hashes);
            System.out.printf("Indexados %d arquivos Java (%d chunks) em %d ms%n",
                    files.size(), countChunks(files), System.currentTimeMillis() - start);
            return;
        }

        SourceIndexCache cache = SourceIndexCache.load(dataset);
        if (forceReindex) {
            knowledgeBase.deleteByDataset(dataset);
            cache.clear();
            System.out.println("--reindex: namespace limpo, reindexacao completa");
        }

        Set<String> diskDocuments = new HashSet<>();
        files.forEach(file -> diskDocuments.add(file.documentId()));
        Set<String> storedDocuments = cache.hashes().keySet();
        SourceIndexSyncer.SyncPlan plan = SourceIndexSyncer.diff(diskDocuments, hashes, storedDocuments, cache.hashes());

        for (String documentId : plan.toDelete()) {
            knowledgeBase.deleteByDocumentId(dataset, documentId);
            cache.remove(documentId);
        }
        List<JavaCodeLoader.IndexedFile> changed = files.stream()
                .filter(file -> plan.toUpsert().contains(file.documentId()))
                .toList();
        indexFiles(knowledgeBase, dataset, changed, hashes);
        changed.forEach(file -> cache.put(file.documentId(), hashes.get(file.documentId())));
        cache.save();

        System.out.printf("Sync: %d novos/alterados, %d removidos, %d inalterados (%d arquivos no projeto) em %d ms%n",
                plan.toUpsert().size(), plan.toDelete().size(), plan.unchanged(),
                files.size(), System.currentTimeMillis() - start);
    }

    private static void indexFiles(VectorKnowledgeBase knowledgeBase, String dataset,
                                   List<JavaCodeLoader.IndexedFile> files, Map<String, String> hashes) {
        for (JavaCodeLoader.IndexedFile file : files) {
            List<VectorKnowledgeBase.ChunkInput> chunks = file.chunks().stream()
                    .map(chunk -> {
                        Map<String, Object> metadata = new HashMap<>(chunk.metadata());
                        metadata.put("fileHash", hashes.get(file.documentId()));
                        return new VectorKnowledgeBase.ChunkInput(chunk.content(), metadata);
                    })
                    .toList();
            knowledgeBase.index(dataset, file.documentId(), chunks);
        }
    }

    private static int countChunks(List<JavaCodeLoader.IndexedFile> files) {
        return files.stream().mapToInt(file -> file.chunks().size()).sum();
    }

    private static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new UncheckedIOException("unable to hash " + file, e instanceof IOException io ? io : new IOException(e));
        }
    }

    private static String slug(String name) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("^-+|-+$", "");
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }

    private static void runQuery(VectorKnowledgeBase knowledgeBase, String dataset, String query, int topK) {
        List<VectorSearchResult> results = knowledgeBase.search(
                VectorSearchQuery.builder()
                        .dataset(dataset)
                        .query(query)
                        .topK(topK)
                        .build()
        );
        printResults(results, false);
    }

    private static void runSimilar(VectorKnowledgeBase knowledgeBase, String dataset, String documentId, int topK) {
        String normalized = documentId.replace('\\', '/');
        String vectorId = dataset + "-" + normalized + "-0";
        try {
            List<VectorSearchResult> results = knowledgeBase.findSimilar(dataset, vectorId, topK);
            System.out.println("\nCodigo similar a " + normalized + ":");
            printResults(results, true);
        } catch (IllegalArgumentException e) {
            System.out.println("Arquivo nao indexado: " + normalized
                    + " (use o caminho relativo mostrado na sincronizacao)");
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

    private static EmbeddingProvider resolveEmbeddings(boolean usePinecone) {
        String provider = System.getenv("EMBEDDINGS_PROVIDER");
        if ("ollama".equalsIgnoreCase(provider)) {
            String baseUrl = orDefault(System.getenv("OLLAMA_BASE_URL"), OllamaEmbeddingProvider.DEFAULT_BASE_URL);
            String model = orDefault(System.getenv("OLLAMA_EMBEDDING_MODEL"), "nomic-embed-text");
            System.out.println("Ollama: " + baseUrl + " modelo " + model);
            if (usePinecone) {
                System.out.println("[aviso] embeddings Ollama (768 dims) em indice Pinecone de outra dimensao "
                        + "vao falhar no guard-rail; para persistencia use EMBEDDINGS_PROVIDER=gemini");
            }
            return new OllamaEmbeddingProvider(baseUrl, model);
        }
        if ("gemini".equalsIgnoreCase(provider)) {
            String baseUrl = orDefault(System.getenv("GEMINI_BASE_URL"), GeminiEmbeddingProvider.DEFAULT_BASE_URL);
            String model = orDefault(System.getenv("GEMINI_EMBEDDING_MODEL"), GeminiEmbeddingProvider.DEFAULT_MODEL);
            System.out.println("Gemini: modelo " + model);
            return new GeminiEmbeddingProvider(requireEnv("GEMINI_API_KEY"), baseUrl, model);
        }
        if ("hashing".equalsIgnoreCase(provider)) {
            System.out.println("EMBEDDINGS_PROVIDER=hashing: matching lexical, sem semantica real");
        } else {
            System.out.println("Nenhum provider configurado: usando HashingEmbeddingProvider (matching lexical, sem semantica real)");
        }
        return new HashingEmbeddingProvider(768);
    }

    private static PineconeClientConfig pineconeConfig() {
        return new PineconeClientConfig(requireEnv("PINECONE_API_KEY"), requireEnv("PINECONE_HOST"));
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("variavel de ambiente obrigatoria ausente: " + name);
        }
        return value;
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

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equals(flag)) {
                return true;
            }
        }
        return false;
    }

    private static String orDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
