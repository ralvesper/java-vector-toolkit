package com.pluxee.vector.examples.source;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceIndexSyncerTest {

    @Test
    void shouldUpsertNewAndChangedDocuments() {
        SourceIndexSyncer.SyncPlan plan = SourceIndexSyncer.diff(
                Set.of("Novo.java", "Mudou.java", "Igual.java"),
                Map.of("Novo.java", "h1", "Mudou.java", "h2-novo", "Igual.java", "h3"),
                Set.of("Mudou.java", "Igual.java"),
                Map.of("Mudou.java", "h2-velho", "Igual.java", "h3")
        );

        assertEquals(Set.of("Novo.java", "Mudou.java"), Set.copyOf(plan.toUpsert()));
        assertTrue(plan.toDelete().isEmpty());
        assertEquals(1, plan.unchanged());
    }

    @Test
    void shouldDeleteRemovedDocuments() {
        SourceIndexSyncer.SyncPlan plan = SourceIndexSyncer.diff(
                Set.of("Fica.java"),
                Map.of("Fica.java", "h1"),
                Set.of("Fica.java", "Apagado.java"),
                Map.of("Fica.java", "h1", "Apagado.java", "h2")
        );

        assertTrue(plan.toUpsert().isEmpty());
        assertEquals(java.util.List.of("Apagado.java"), plan.toDelete());
        assertEquals(1, plan.unchanged());
    }

    @Test
    void shouldTreatStoredDocumentWithoutHashAsChanged() {
        SourceIndexSyncer.SyncPlan plan = SourceIndexSyncer.diff(
                Set.of("SemHash.java"),
                Map.of("SemHash.java", "h1"),
                Set.of("SemHash.java"),
                Map.of()
        );

        assertEquals(java.util.List.of("SemHash.java"), plan.toUpsert());
    }

    @Test
    void shouldExtractDocumentIdFromVectorId() {
        String dataset = "source-code-java-vector-toolkit";

        assertEquals("examples/src/Foo.java",
                SourceIndexSyncer.documentIdFromVectorId(dataset, dataset + "-examples/src/Foo.java-0"));
        assertEquals("A.java",
                SourceIndexSyncer.documentIdFromVectorId(dataset, dataset + "-A.java-12"));
        assertNull(SourceIndexSyncer.documentIdFromVectorId(dataset, "outro-dataset-A.java-0"));
        assertNull(SourceIndexSyncer.documentIdFromVectorId(dataset, dataset + "-sem-chunk"));
    }
}
