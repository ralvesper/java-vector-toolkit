package com.pluxee.vector.examples.source;

import com.pluxee.vector.document.FixedSizeChunkStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaCodeLoaderTest {

    @TempDir
    Path repo;

    @Test
    void shouldLoadJavaFilesWithMetadataAndChunks() throws Exception {
        Path main = Files.createDirectories(repo.resolve("src/main/java/com/exemplo"));
        Files.writeString(main.resolve("OrderService.java"), """
                package com.exemplo;

                class OrderService {
                    void processa() { }
                }
                """);
        Path targetDir = Files.createDirectories(repo.resolve("target/classes"));
        Files.writeString(targetDir.resolve("Generated.java"), "class Generated { }");

        List<JavaCodeLoader.IndexedFile> files = new JavaCodeLoader(
                repo, new FixedSizeChunkStrategy(64, 0)
        ).load();

        assertEquals(1, files.size());
        JavaCodeLoader.IndexedFile file = files.getFirst();
        assertEquals("src/main/java/com/exemplo/OrderService.java", file.documentId());
        assertTrue(file.chunks().size() >= 1);
        var metadata = file.chunks().getFirst().metadata();
        assertEquals("com.exemplo", metadata.get("pacote"));
        assertEquals("OrderService", metadata.get("classe"));
        assertEquals("code", metadata.get("type"));
        assertEquals(0, metadata.get("chunkIndex"));
    }

    @Test
    void shouldChunkLargeFileIntoMultipleChunks() throws Exception {
        Path main = Files.createDirectories(repo.resolve("src"));
        String body = "// comentario longo para forcar quebra de chunk\n".repeat(30);
        Files.writeString(main.resolve("Grande.java"), "class Grande {\n" + body + "}");

        List<JavaCodeLoader.IndexedFile> files = new JavaCodeLoader(
                repo, new FixedSizeChunkStrategy(64, 8)
        ).load();

        assertTrue(files.getFirst().chunks().size() > 1);
        assertEquals(1, files.getFirst().chunks().get(1).metadata().get("chunkIndex"));
    }
}
