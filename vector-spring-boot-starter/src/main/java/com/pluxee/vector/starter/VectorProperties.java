package com.pluxee.vector.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vector")
public class VectorProperties {

    private final Store store = new Store();
    private final Pinecone pinecone = new Pinecone();
    private final Chunking chunking = new Chunking();
    private final Search search = new Search();

    public Store getStore() {
        return store;
    }

    public Pinecone getPinecone() {
        return pinecone;
    }

    public Chunking getChunking() {
        return chunking;
    }

    public Search getSearch() {
        return search;
    }

    public static class Store {
        private String provider = "in-memory";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }

    public static class Pinecone {
        private String apiKey;
        private String host;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }
    }

    public static class Chunking {
        private int size = 800;
        private int overlap = 100;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getOverlap() {
            return overlap;
        }

        public void setOverlap(int overlap) {
            this.overlap = overlap;
        }
    }

    public static class Search {
        private int topK = 5;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }
    }
}
