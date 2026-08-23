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
import com.pluxee.vector.rag.GeminiLlmClient;
import com.pluxee.vector.rag.LlmClient;
import com.pluxee.vector.rag.OllamaEmbeddingProvider;
import com.pluxee.vector.rag.OllamaLlmClient;
import com.pluxee.vector.rag.RagService;
import org.springframework.beans.factory.ObjectProvider;
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
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "vector.embedding", name = "provider", havingValue = "ollama")
    public EmbeddingProvider ollamaEmbeddingProvider(VectorProperties properties) {
        String model = properties.getEmbedding().getModel();
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("vector.embedding.model is required when vector.embedding.provider=ollama");
        }
        String baseUrl = properties.getEmbedding().getBaseUrl();
        return baseUrl == null || baseUrl.isBlank()
                ? new OllamaEmbeddingProvider(model)
                : new OllamaEmbeddingProvider(baseUrl, model);
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
    @ConditionalOnProperty(prefix = "vector.llm", name = "provider", havingValue = "gemini")
    public LlmClient geminiLlmClient(VectorProperties properties) {
        String apiKey = properties.getLlm().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("vector.llm.api-key is required when vector.llm.provider=gemini");
        }
        String model = properties.getLlm().getModel();
        return model == null || model.isBlank()
                ? new GeminiLlmClient(apiKey)
                : new GeminiLlmClient(apiKey, GeminiLlmClient.DEFAULT_BASE_URL, model);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "vector.llm", name = "provider", havingValue = "ollama")
    public LlmClient ollamaLlmClient(VectorProperties properties) {
        String model = properties.getLlm().getModel();
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("vector.llm.model is required when vector.llm.provider=ollama");
        }
        String baseUrl = properties.getLlm().getBaseUrl();
        return baseUrl == null || baseUrl.isBlank()
                ? new OllamaLlmClient(model)
                : new OllamaLlmClient(baseUrl, model);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagService ragService(VectorKnowledgeBase vectorKnowledgeBase,
                                 ContextBuilder contextBuilder,
                                 ObjectProvider<LlmClient> llmClient) {
        return new RagService(vectorKnowledgeBase, contextBuilder, llmClient.getIfAvailable());
    }
}

