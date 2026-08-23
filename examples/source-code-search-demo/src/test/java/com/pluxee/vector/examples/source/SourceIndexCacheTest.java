package com.pluxee.vector.examples.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceIndexCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRoundTripHashes() {
        Path file = tempDir.resolve("source-code-demo.json");
        SourceIndexCache cache = new SourceIndexCache(file, Map.of("A.java", "h1"));

        cache.put("B.java", "h2");
        cache.save();

        assertTrue(Files.isRegularFile(file));
        SourceIndexCache reloaded = new SourceIndexCache(file, readJson(file));
        assertEquals("h1", reloaded.hashes().get("A.java"));
        assertEquals("h2", reloaded.hashes().get("B.java"));
    }

    @Test
    void shouldRemoveEntries() {
        SourceIndexCache cache = new SourceIndexCache(tempDir.resolve("c.json"), Map.of("A.java", "h1"));
        cache.remove("A.java");

        assertTrue(cache.hashes().isEmpty());
    }

    private Map<String, String> readJson(Path file) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    file.toFile(), new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
