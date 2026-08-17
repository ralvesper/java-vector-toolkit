package com.pluxee.vector.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FileSystemDocumentLoader implements DocumentLoader {

    @Override
    public List<SourceDocument> load(DocumentSource source) {
        Path root = source.path();
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("path does not exist: " + root);
        }

        try {
            List<SourceDocument> documents = new ArrayList<>();
            Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(this::isSupported)
                    .forEach(file -> documents.add(toSourceDocument(root, file)));
            return documents;
        } catch (IOException e) {
            throw new IllegalStateException("unable to load documents from " + root, e);
        }
    }

    private boolean isSupported(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return fileName.endsWith(".md") || fileName.endsWith(".txt");
    }

    private SourceDocument toSourceDocument(Path root, Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String relativePath = root.relativize(file).toString();
            return new SourceDocument(
                    relativePath,
                    content,
                    Map.of(
                            "path", relativePath,
                            "type", "documentation"
                    )
            );
        } catch (IOException e) {
            throw new IllegalStateException("unable to read file " + file, e);
        }
    }
}

