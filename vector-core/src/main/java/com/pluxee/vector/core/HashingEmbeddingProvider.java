package com.pluxee.vector.core;

import java.nio.charset.StandardCharsets;

public class HashingEmbeddingProvider implements EmbeddingProvider {

    private final int dimensions;

    public HashingEmbeddingProvider(int dimensions) {
        if (dimensions < 32) {
            throw new IllegalArgumentException("dimensions must be at least 32");
        }
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String content) {
        float[] vector = new float[dimensions];
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            int index = Math.floorMod((bytes[i] + i * 31), dimensions);
            vector[index] += 1.0f;
        }
        normalize(vector);
        return vector;
    }

    private void normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        double magnitude = Math.sqrt(sum);
        if (magnitude == 0) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / magnitude);
        }
    }
}

