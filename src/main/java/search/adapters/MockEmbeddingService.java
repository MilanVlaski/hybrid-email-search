package search.adapters;

import search.core.EmbeddingService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MockEmbeddingService implements EmbeddingService {
    private static final int VECTOR_DIMENSION = 768;
    private final MessageDigest digest;

    public MockEmbeddingService() {
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
        byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
        float[] vector = new float[VECTOR_DIMENSION];

        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            int byteIndex = i % hash.length;
            vector[i] = (hash[byteIndex] & 0xFF) / 255.0f * 2 - 1; // Normalize to [-1, 1]
        }

        // Normalize vector
        return normalize(vector);
    }

    private float[] normalize(float[] vector) {
        float norm = 0.0f;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        if (norm < 1e-12f) {
            return vector;
        }

        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }

        return vector;
    }
}
