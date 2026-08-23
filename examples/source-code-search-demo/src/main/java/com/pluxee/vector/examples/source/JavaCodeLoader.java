package com.pluxee.vector.examples.source;

import com.pluxee.vector.document.ChunkStrategy;
import com.pluxee.vector.document.DocumentChunk;
import com.pluxee.vector.document.SourceDocument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class JavaCodeLoader {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern TYPE_PATTERN = Pattern.compile("\\b(class|interface|enum|record)\\s+(\\w+)");

    private final Path repoRoot;
    private final ChunkStrategy chunkStrategy;

    public JavaCodeLoader(Path repoRoot, ChunkStrategy chunkStrategy) {
        this.repoRoot = repoRoot.toAbsolutePath().normalize();
        this.chunkStrategy = chunkStrategy;
    }

    public record IndexedFile(String documentId, List<DocumentChunk> chunks) {
    }

    public List<IndexedFile> load() {
        try (Stream<Path> paths = Files.walk(repoRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .filter(this::isNotBuildArtifact)
                    .map(this::toIndexedFile)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("unable to load java sources from " + repoRoot, e);
        }
    }

    private boolean isNotBuildArtifact(Path file) {
        String path = file.toString();
        return !path.contains("/target/") && !path.contains("/.git/") && !path.contains("/node_modules/");
    }

    private IndexedFile toIndexedFile(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String relativePath = repoRoot.relativize(file).toString().replace('\\', '/');

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("arquivo", relativePath);
            metadata.put("type", "code");
            String packageName = firstGroup(PACKAGE_PATTERN, content);
            if (packageName != null) {
                metadata.put("pacote", packageName);
            }
            String typeName = typeName(content);
            if (typeName != null) {
                metadata.put("classe", typeName);
            }

            SourceDocument document = new SourceDocument(relativePath, content, metadata);
            return new IndexedFile(relativePath, chunkStrategy.split(document));
        } catch (IOException e) {
            throw new IllegalStateException("unable to read file " + file, e);
        }
    }

    private String typeName(String content) {
        Matcher matcher = TYPE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(2) : null;
    }

    private String firstGroup(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }
}
