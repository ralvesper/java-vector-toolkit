package com.pluxee.vector.examples.source;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class SourceIndexCache {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path file;
    private Map<String, String> hashes;

    SourceIndexCache(Path file, Map<String, String> hashes) {
        this.file = file;
        this.hashes = new HashMap<>(hashes);
    }

    static SourceIndexCache load(String dataset) {
        Path file = Path.of(System.getProperty("user.home"), ".cache", "java-vector-toolkit", dataset + ".json");
        if (!Files.isRegularFile(file)) {
            return new SourceIndexCache(file, new HashMap<>());
        }
        try {
            Map<String, String> hashes = MAPPER.readValue(file.toFile(), new TypeReference<>() {
            });
            return new SourceIndexCache(file, new HashMap<>(hashes));
        } catch (IOException e) {
            System.out.println("[aviso] cache corrompido, reindexando tudo: " + file);
            return new SourceIndexCache(file, new HashMap<>());
        }
    }

    Map<String, String> hashes() {
        return hashes;
    }

    void put(String documentId, String hash) {
        hashes.put(documentId, hash);
    }

    void remove(String documentId) {
        hashes.remove(documentId);
    }

    void save() {
        try {
            Files.createDirectories(file.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), hashes);
        } catch (IOException e) {
            throw new UncheckedIOException("unable to save index cache " + file, e);
        }
    }

    void clear() {
        hashes = new HashMap<>();
    }
}
