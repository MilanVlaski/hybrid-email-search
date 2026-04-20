package search.adapters;

import search.core.Embedder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MockEmbedder implements Embedder {
    private static final int VECTOR_DIMENSION = 768;
    private final MessageDigest digest;

    public MockEmbedder() {
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[VECTOR_DIMENSION];
        }

        // Deterministic "embedding" based on text hash for testing
        var hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
        var vector = new float[VECTOR_DIMENSION];

        for (var i = 0; i < VECTOR_DIMENSION; i++) {
            var byteIndex = i % hash.length;
            vector[i] = (hash[byteIndex] & 0xFF) / 255.0f * 2 - 1; // Normalize to [-1, 1]
        }

        // Normalize vector
        return normalize(vector);
    }

    private float[] normalize(float[] vector) {
        var norm = 0.0f;
        for (var v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        if (norm < 1e-12f) {
            return vector;
        }

        for (var i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }

        return vector;
    }
}
