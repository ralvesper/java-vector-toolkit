package com.pluxee.vector.starter;

import com.pluxee.vector.core.EmbeddingProvider;
import com.pluxee.vector.core.HashingEmbeddingProvider;
import com.pluxee.vector.core.InMemoryVectorStore;
import com.pluxee.vector.core.VectorKnowledgeBase;
import com.pluxee.vector.core.VectorStorePort;
import com.pluxee.vector.document.ChunkStrategy;
import com.pluxee.vector.document.FixedSizeChunkStrategy;
import com.pluxee.vector.pinecone.PineconeClientConfig;
import com.pluxee.vector.pinecone.PineconeVectorStore;
import com.pluxee.vector.rag.ContextBuilder;
import com.pluxee.vector.rag.DefaultContextBuilder;
import com.pluxee.vector.rag.RagService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(VectorProperties.class)
public class VectorToolkitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EmbeddingProvider embeddingProvider() {
        return new HashingEmbeddingProvider(256);
    }

    @Bean
    @ConditionalOnMissingBean(VectorStorePort.class)
    @ConditionalOnProperty(prefix = "vector.store", name = "provider", havingValue = "in-memory", matchIfMissing = true)
    public VectorStorePort inMemoryVectorStorePort() {
        return new InMemoryVectorStore();
    }

    @Bean
    @ConditionalOnMissingBean(VectorStorePort.class)
    @ConditionalOnProperty(prefix = "vector.store", name = "provider", havingValue = "pinecone")
    public VectorStorePort pineconeVectorStorePort(VectorProperties properties) {
        String apiKey = properties.getPinecone().getApiKey();
        String host = properties.getPinecone().getHost();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("vector.pinecone.api-key is required when vector.store.provider=pinecone");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("vector.pinecone.host is required when vector.store.provider=pinecone");
        }
        return new PineconeVectorStore(new PineconeClientConfig(apiKey, host));
    }

    @Bean
    @ConditionalOnMissingBean
    public ChunkStrategy chunkStrategy(VectorProperties properties) {
        return new FixedSizeChunkStrategy(
                properties.getChunking().getSize(),
                properties.getChunking().getOverlap()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public VectorKnowledgeBase vectorKnowledgeBase(EmbeddingProvider embeddingProvider, VectorStorePort vectorStorePort) {
        return new VectorKnowledgeBase(embeddingProvider, vectorStorePort);
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextBuilder contextBuilder() {
        return new DefaultContextBuilder();
    }

    @Bean
    @ConditionalOnMissingBean
    public RagService ragService(VectorKnowledgeBase vectorKnowledgeBase, ContextBuilder contextBuilder) {
        return new RagService(vectorKnowledgeBase, contextBuilder);
    }
}

