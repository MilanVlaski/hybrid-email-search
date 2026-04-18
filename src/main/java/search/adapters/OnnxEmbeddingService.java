package search.adapters;

import ai.onnxruntime.*;
import org.json.JSONArray;
import org.json.JSONObject;
import search.core.EmbeddingService;

import java.io.BufferedReader;
import java.io.FileReader;
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
        initializeTokenizerFromJson(tokenizerPath);
    }

    private void initializeTokenizerFromJson(String tokenizerPath) throws Exception {
        // Read the tokenizer JSON file
        StringBuilder jsonContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(tokenizerPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
        }

        JSONObject tokenizerJson = new JSONObject(jsonContent.toString());

        // Extract the vocabulary from the model section
        JSONObject model = tokenizerJson.getJSONObject("model");
        JSONObject vocab = model.getJSONObject("vocab");

        // Clear and populate the tokenToId map
        tokenToId.clear();

        // Add all tokens from the vocabulary
        Iterator<String> keys = vocab.keys();
        while (keys.hasNext()) {
            String token = keys.next();
            long id = vocab.getLong(token);
            tokenToId.put(token, id);
        }

        // Also extract special tokens if they exist separately
        if (tokenizerJson.has("added_tokens")) {
            JSONArray addedTokens = tokenizerJson.getJSONArray("added_tokens");
            for (int i = 0; i < addedTokens.length(); i++) {
                JSONObject tokenObj = addedTokens.getJSONObject(i);
                String content = tokenObj.getString("content");
                long id = tokenObj.getLong("id");
                // Only add if not already in vocab (vocab takes precedence)
                if (!tokenToId.containsKey(content)) {
                    tokenToId.put(content, id);
                }
            }
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

            long[] attentionMask = new long[tokenIds.length];
            Arrays.fill(attentionMask, 1L);

            try (OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenIds), shape);
                 OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape)) {

                Map<String, OnnxTensor> inputs = Map.of(
                        "input_ids", inputIdsTensor,
                        "attention_mask", attentionMaskTensor
                );

                try (OrtSession.Result results = session.run(inputs)) {
                    var output = results.get(0).getValue();
                    float[][] tokenEmbeddings;
                    if (output instanceof float[][][] floatArray3D) {
                        tokenEmbeddings = floatArray3D[0]; // Take the first batch
                    } else if (output instanceof float[][] floatArray2D) {
                        tokenEmbeddings = floatArray2D;
                    } else {
                        throw new RuntimeException("Unexpected output type from ONNX model: " + output.getClass());
                    }
                    float[] pooled = averagePool(tokenEmbeddings);
                    return normalize(pooled);
                }
            }
        } catch (OrtException e) {
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    private long[] tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return new long[]{tokenToId.get("<bos>"), tokenToId.get("<eos>")};
        }

        // Normalize text (lowercase and trim)
        String normalized = text.toLowerCase().trim();

        // Split into words (whitespace)
        String[] words = normalized.split("\\s+");

        List<Long> allTokens = new ArrayList<>();
        allTokens.add(tokenToId.get("<bos>"));

        for (String word : words) {
            if (word.isEmpty()) continue;
            Long tokenId = tokenToId.get(word);
            if (tokenId != null) {
                allTokens.add(tokenId);
            } else {
                // Use unknown token
                allTokens.add(tokenToId.getOrDefault(" &#x242;", 1L)); // fallback to the stub's unk token
            }
        }

        allTokens.add(tokenToId.get("<eos>"));
        return allTokens.stream().mapToLong(Long::longValue).toArray();
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