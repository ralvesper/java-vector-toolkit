package com.pluxee.vector.examples.issues;

import com.pluxee.vector.document.ChunkStrategy;
import com.pluxee.vector.document.DocumentChunk;
import com.pluxee.vector.document.SourceDocument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class PluxeeIssuesLoader {

    private final Path repoRoot;
    private final ChunkStrategy chunkStrategy;

    public PluxeeIssuesLoader(Path repoRoot, ChunkStrategy chunkStrategy) {
        this.repoRoot = repoRoot.toAbsolutePath().normalize();
        this.chunkStrategy = chunkStrategy;
    }

    public List<IndexedIssue> load() {
        List<IndexedIssue> issues = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(repoRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> !path.startsWith(repoRoot.resolve(".git")))
                    .sorted()
                    .forEach(path -> issues.add(loadFile(path)));
        } catch (IOException e) {
            throw new IllegalStateException("unable to scan pluxee-issues repository at " + repoRoot, e);
        }
        if (issues.isEmpty()) {
            throw new IllegalStateException("no markdown files found under " + repoRoot);
        }
        return issues;
    }

    private IndexedIssue loadFile(Path path) {
        String relativePath = repoRoot.relativize(path).toString().replace('\\', '/');
        String content = readContent(path);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("arquivo", relativePath);
        metadata.put("ticket", extractTicket(relativePath));
        metadata.put("tipo", extractTipo(relativePath));

        SourceDocument document = new SourceDocument(relativePath, content, metadata);
        List<DocumentChunk> chunks = chunkStrategy.split(document);
        return new IndexedIssue(relativePath, sha256(content), chunks);
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String readContent(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException("unable to read " + path, e);
        }
    }

    private String extractTicket(String relativePath) {
        String[] parts = relativePath.split("/");
        if (parts.length > 2 && parts[0].equals("dados") && !parts[1].isBlank()) {
            return parts[1];
        }
        return "";
    }

    private String extractTipo(String relativePath) {
        String fileName = relativePath.toLowerCase();
        if (fileName.contains("investigacao")) {
            return "investigacao";
        }
        if (fileName.contains("fix") || fileName.contains("hotfix")) {
            return "fix";
        }
        if (fileName.contains("massa-de-dados")) {
            return "massa-de-dados";
        }
        if (fileName.contains("merge-request")) {
            return "merge-request";
        }
        if (fileName.endsWith("issues.md")) {
            return "issues";
        }
        return "doc";
    }

    public record IndexedIssue(String documentId, String contentHash, List<DocumentChunk> chunks) {
    }
}
