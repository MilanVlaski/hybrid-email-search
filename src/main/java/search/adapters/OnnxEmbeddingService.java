package search.adapters;

import ai.onnxruntime.*;
import search.core.EmbeddingService;

import java.nio.LongBuffer;
import java.util.*;

public class OnnxEmbeddingService implements EmbeddingService {
    private final OrtEnvironment env;
    private final OrtSession session;
    private final Map<String, Long> tokenToId;
    private static final int VECTOR_DIMENSION = 768;

    public OnnxEmbeddingService(String modelPath, String tokenizerPath) throws Exception {
        this.env = OrtEnvironment.getEnvironment();

        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

        session = env.createSession(modelPath, opts);

        tokenToId = new HashMap<>();
        initializeSimpleTokenizer(tokenizerPath);
    }

    private void initializeSimpleTokenizer(String tokenizerPath) throws Exception {
        tokenToId.put("<pad>", 0L);
        tokenToId.put("<unk>", 1L);
        tokenToId.put("<bos>", 2L);
        tokenToId.put("<eos>", 3L);

        String[] commonWords = {
                "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
                "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
                "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
                "or", "an", "will", "my", "one", "all", "would", "there", "their", "what"
        };

        for (int i = 0; i < commonWords.length; i++) {
            tokenToId.put(commonWords[i], (long) (i + 4));
        }
    }

    @Override
    public float[] embed(String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                return new float[VECTOR_DIMENSION];
            }

            long[] tokenIds = tokenize(text);
            long[] shape = {1, tokenIds.length};

            try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenIds), shape)) {
                Map<String, OnnxTensor> inputs = Map.of("input_ids", inputTensor);

                try (OrtSession.Result results = session.run(inputs)) {
                    var output = results.get(0).getValue();
                    if (!(output instanceof float[][] floatArray)) {
                        throw new RuntimeException("Unexpected output type from ONNX model: " + output.getClass());
                    }
                    float[] pooled = averagePool(floatArray);
                    return normalize(pooled);
                }
            }
        } catch (OrtException e) {
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    private long[] tokenize(String text) {
        String[] words = text.toLowerCase().trim().split("\\s+");
        List<Long> tokens = new ArrayList<>();
        tokens.add(tokenToId.get("<bos>"));

        for (String word : words) {
            if (word.length() > 0) {
                Long tokenId = tokenToId.get(word);
                tokens.add(tokenId != null ? tokenId : tokenToId.get("<unk>"));
            }
        }

        tokens.add(tokenToId.get("<eos>"));
        return tokens.stream().mapToLong(Long::longValue).toArray();
    }

    private float[] averagePool(float[][] tokenEmbeddings) {
        if (tokenEmbeddings.length == 0) return new float[VECTOR_DIMENSION];

        int hiddenSize = tokenEmbeddings[0].length;
        float[] pooled = new float[hiddenSize];

        for (float[] tokenEmbedding : tokenEmbeddings) {
            for (int i = 0; i < hiddenSize; i++) {
                pooled[i] += tokenEmbedding[i];
            }
        }

        for (int i = 0; i < hiddenSize; i++) {
            pooled[i] /= tokenEmbeddings.length;
        }

        return pooled;
    }

    private float[] normalize(float[] vector) {
        float norm = 0.0f;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        if (norm < 1e-12) return vector;

        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / norm;
        }

        return normalized;
    }

    public void close() {
        try {
            session.close();
            env.close();
        } catch (OrtException e) {
            throw new RuntimeException("Failed to close ONNX resources", e);
        }
    }
}