package com.pluxee.vector.starter;

import com.pluxee.vector.core.InMemoryVectorStore;
import com.pluxee.vector.core.VectorDocument;
import com.pluxee.vector.core.VectorSearchQuery;
import com.pluxee.vector.core.VectorSearchResult;
import com.pluxee.vector.core.VectorStorePort;
import com.pluxee.vector.pinecone.PineconeVectorStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VectorToolkitAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VectorToolkitAutoConfiguration.class))
            .withPropertyValues("logging.level.org.springframework=ERROR");

    @Test
    void shouldCreateInMemoryStoreByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(VectorStorePort.class);
            assertThat(context.getBean(VectorStorePort.class)).isInstanceOf(InMemoryVectorStore.class);
        });
    }

    @Test
    void shouldCreatePineconeStoreWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "vector.store.provider=pinecone",
                        "vector.pinecone.api-key=test-key",
                        "vector.pinecone.host=http://localhost:39091"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(VectorStorePort.class);
                    assertThat(context.getBean(VectorStorePort.class)).isInstanceOf(PineconeVectorStore.class);
                });
    }

    @Test
    @Tag("negative")
    void shouldFailWhenPineconeHostIsMissing() {
        contextRunner
                .withPropertyValues(
                        "vector.store.provider=pinecone",
                        "vector.pinecone.api-key=test-key"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("vector.pinecone.host is required");
                });
    }

    @Test
    void shouldKeepCustomVectorStoreBean() {
        contextRunner
                .withUserConfiguration(CustomVectorStoreConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(VectorStorePort.class);
                    assertThat(context.getBean(VectorStorePort.class)).isSameAs(CustomVectorStoreConfig.CUSTOM_STORE);
                });
    }

    @Configuration
    static class CustomVectorStoreConfig {
        private static final VectorStorePort CUSTOM_STORE = new VectorStorePort() {
            @Override
            public void upsert(List<VectorDocument> documents) {
            }

            @Override
            public List<VectorSearchResult> search(VectorSearchQuery query, float[] queryEmbedding) {
                return List.of();
            }

            @Override
            public List<VectorSearchResult> findSimilarById(String dataset, String vectorId, int topK) {
                return List.of();
            }

            @Override
            public void deleteByDocumentId(String dataset, String documentId) {
            }

            @Override
            public void deleteByDataset(String dataset) {
            }
        };

        @Bean
        VectorStorePort vectorStorePort() {
            return CUSTOM_STORE;
        }
    }
}

