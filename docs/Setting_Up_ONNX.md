# Setting Up ONNX Embeddings with Gemma

This guide explains how to obtain and configure the ONNX model and tokenizer for production-ready embeddings.

## Overview

The `OnnxEmbeddingService` requires two files:
1. **ONNX Model** (`.onnx`) - The neural network model for generating embeddings
2. **Tokenizer** (`.json`) - Converts text into token IDs for the model

By default, the application uses `MockEmbeddingService` which generates deterministic vectors for testing. This guide helps you switch to real embeddings.

## Quick Start (Recommended Model: google/embeddinggemma-300m)

The project is designed to work with the `google/embeddinggemma-300m` model, a 300M parameter embedding-optimized version of Google's Gemma. This model provides an excellent balance of quality and speed on CPU.

### Step 1: Download the Model and Tokenizer

**Important Prerequisite (Gated Model):**
The `google/embeddinggemma-300m` model is gated. Before downloading, you must complete these steps in order:
1. **Create an account:** Sign up or log in at [Hugging Face](https://huggingface.co/).
2. **Accept the terms:** Visit the model page at [https://huggingface.co/google/embeddinggemma-300m](https://huggingface.co/google/embeddinggemma-300m) and explicitly accept the repository's terms of use.
3. **Get a key:** Generate an access token at [https://huggingface.co/settings/tokens](https://huggingface.co/settings/tokens) (a "Read" token is sufficient).
4. **Authenticate:** Run `huggingface-cli login` in your terminal and provide the token, OR export it directly using `export HF_TOKEN="your_token_here"`.

Download the model and tokenizer from Hugging Face:

```bash
# Create models directory
mkdir -p models

# Note: These are Safetensors format files, not ONNX
# You'll need to convert the model (see "Converting to ONNX" section below)
curl -L -o models/model.safetensors \
  "https://huggingface.co/google/embeddinggemma-300m/resolve/main/model.safetensors"

curl -L -o models/tokenizer.json \
  "https://huggingface.co/google/embeddinggemma-300m/resolve/main/tokenizer.json"

curl -L -o models/config.json \
  "https://huggingface.co/google/embeddinggemma-300m/resolve/main/config.json"
```

**Alternative: Manual download from browser:**
1. Visit: https://huggingface.co/google/embeddinggemma-300m
2. Download `model.safetensors`, `tokenizer.json`, and `config.json`
3. Place all files in the `models/` directory
4. Convert to ONNX using the script below

### Step 2: Convert to ONNX (Required)

The model is distributed in Safetensors format. Convert it to ONNX:

```bash
# Create and activate a virtual environment (recommended to avoid conflicts)
python3 -m venv venv
source venv/bin/activate

# Install dependencies
pip install torch transformers onnx optimum[onnxruntime]

# Convert to ONNX
optimum-cli export onnx \
  --model google/embeddinggemma-300m \
  --task feature-extraction \
  ./models/onnx/
```

After conversion, you'll have:
- `models/onnx/model.onnx` - The embedding model
- `models/onnx/tokenizer.json` - The tokenizer (copied from source)
- `models/onnx/config.json` - Model configuration

### Step 3: Verify Model Files

Check that the files are present and valid:

```bash
# Check file sizes (model ~600MB, tokenizer ~1MB)
ls -lh models/onnx/

# Verify ONNX model
python3 -c "import onnx; m = onnx.load('models/onnx/model.onnx'); print(f'Opset: {m.opset_import}')"
```

## **YOU WILL FIND ONLY AI GIBBERISH BELOW**

## Model Specifications

### google/embeddinggemma-300m

| Property | Value |
|----------|-------|
| **Parameters** | 300M |
| **Vector Dimensions** | 768 |
| **Context Length** | 2,048 tokens |
| **Model Size** | ~600MB (Safetensors), ~1.2GB (ONNX) |
| **License** | Gemma Terms of Use |
| **Optimal Use** | Document embeddings, semantic search |
| **Base Architecture** | Gemma3TextModel |

### Why google/embeddinggemma-300m?

1. **Purpose-built for embeddings** - Unlike base Gemma models, this variant is optimized specifically for generating embeddings
2. **CPU-optimized** - Runs efficiently on consumer-grade CPUs without GPU
3. **High quality** - Outperforms general-purpose sentence transformers on many tasks
4. **Right-sized** - 300M parameters strike a balance between quality and inference speed
5. **768-dimensional output** - Matches the Lucene vector index configuration

### Hardware Requirements

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| **CPU** | x86_64 with AVX2 | ARM64 or x86_64 with AVX-512 |
| **RAM** | 4GB | 8GB |
| **Disk** | 2GB free | 5GB free (model + index) |

### Alternative Models

If google/embeddinggemma-300m is unavailable, these alternatives also work with 768-dimensional vectors:

| Model | Dimensions | Size | Speed | Quality |
|-------|------------|------|-------|---------|
| sentence-transformers/all-mpnet-base-v2 | 768 | ~420MB | Medium | Very Good |
| BAAI/bge-base-en-v1.5 | 768 | ~420MB | Fast | Very Good |
| nomic-ai/nomic-embed-text-v1.5 | 768 | ~540MB | Fast | Excellent |

**Note:** Using a non-768-dimensional model requires modifying `VECTOR_DIMENSION` in `OnnxEmbeddingService.java` and re-indexing all data.

## Converting to ONNX (Detailed)

The Safetensors format model must be converted to ONNX for use with the Java ONNX Runtime.

### Prerequisites

```bash
# Create and activate a virtual environment
python3 -m venv venv
source venv/bin/activate

# Install dependencies
pip install torch transformers onnx optimum[exporters]

optimum-cli export onnx \
  --model google/embeddinggemma-300m \
  --task feature-extraction \
  --optimize O2 \
  ./models/onnx/
```

### Conversion Output

After conversion, verify the model structure:

```python
import onnx

model = onnx.load("models/onnx/model.onnx")

# Check inputs
print("Inputs:")
for input in model.graph.input:
    print(f"  {input.name}: {[d.dim_value if d.dim_value else 'dynamic' for d in input.type.tensor_type.shape.dim]}")

# Check outputs
print("Outputs:")
for output in model.graph.output:
    print(f"  {output.name}: {[d.dim_value if d.dim_value else 'dynamic' for d in output.type.tensor_type.shape.dim]}")
```

Expected output:
```
Inputs:
  input_ids: [dynamic, dynamic]
Outputs:
  last_hidden_state: [dynamic, dynamic, 768]
```

## Troubleshooting

### "Model loading failed"

**Problem:** ONNX Runtime can't load the model

**Solutions:**
1. Verify model file exists: `ls -lh models/onnx/model.onnx`
2. Check file integrity:
   ```bash
   python3 -c "import onnx; onnx.load('models/onnx/model.onnx')" && echo "Valid ONNX"
   ```
3. Check ONNX Runtime version matches model opset:
   ```bash
   python3 -c "import onnx; print(onnx.load('models/onnx/model.onnx').opset_import)"
   ```
4. Ensure you converted the model correctly (Safetensors models won't load directly)
5. Try regenerating the ONNX file with `--optimize O1` instead of `O2`

### "Tokenizer not found"

**Problem:** tokenizer.json is missing or invalid

**Solutions:**
1. Ensure tokenizer.json is in the same directory as model.onnx
2. Check file permissions: `chmod 644 models/onnx/tokenizer.json`
3. Verify JSON is valid:
   ```bash
   python3 -m json.tool models/onnx/tokenizer.json > /dev/null && echo "Valid JSON"
   ```
4. Re-download the tokenizer from the source model

### Poor embedding quality

**Problem:** Search results don't make sense

**Solutions:**
1. Verify vector dimension is 768 (required by the index):
   ```bash
   python3 << 'EOF'
   import onnx
   model = onnx.load("models/onnx/model.onnx")
   for output in model.graph.output:
       dims = [d.dim_value for d in output.type.tensor_type.shape.dim if d.dim_value]
       if dims:
           print(f"Output '{output.name}' last dimension: {dims[-1]}")
   EOF
   ```
2. Ensure you're using an embedding model, not a text generation model
3. Check that the tokenizer matches the model (mixing tokenizer/model from different sources causes issues)
4. Verify the model outputs reasonable embeddings by running the verification test below

### Out of memory

**Problem:** Java heap error during indexing

**Solutions:**
1. Increase Java heap space:
   ```bash
   export MAVEN_OPTS="-Xmx4g -Xms2g"
   ```
2. Process emails in smaller batches in `IndexEmailsAction.java`
3. Use a smaller model (BAAI/bge-small-en-v1.5 at 384 dimensions requires code changes)

### "Token ID out of range"

**Problem:** Tokenizer produces IDs not recognized by model

**Solutions:**
1. Ensure tokenizer.json matches the model (download both from same source)
2. Check tokenizer vocabulary size matches model's expected input range
3. Verify the tokenizer is the Gemma tokenizer, not a different model's tokenizer

### "No tokenizer.json found"

**Problem:** Conversion didn't copy tokenizer.json

**Solutions:**
1. Manually copy tokenizer.json to the output directory:
   ```bash
   huggingface-cli download google/embeddinggemma-300m tokenizer.json --local-dir ./models/onnx
   ```
2. Or download it directly:
   ```bash
   curl -L -o models/onnx/tokenizer.json \
     "https://huggingface.co/google/embeddinggemma-300m/resolve/main/tokenizer.json"
   ```

## Verification

Test that your setup works:

### Java Test

Create `src/main/java/search/TestOnnx.java`:

```java
package search;

import search.adapters.OnnxEmbeddingService;

public class TestOnnx {
    public static void main(String[] args) throws Exception {
        try (var service = new OnnxEmbeddingService(
                "models/onnx/model.onnx",
                "models/onnx/tokenizer.json")) {

            String[] testSentences = {
                "The quick brown fox jumps over the lazy dog",
                "A fast auburn canine leaps above the idle hound",
                "Machine learning is transforming search technology"
            };

            for (String sentence : testSentences) {
                float[] embedding = service.embed(sentence);
                System.out.println("Sentence: " + sentence);
                System.out.println("  Dimension: " + embedding.length);
                System.out.println("  First 5 values: " +
                    java.util.Arrays.toString(java.util.Arrays.copyOf(embedding, 5)));
            }

            // Test semantic similarity
            float[] emb1 = service.embed("The cat sat on the mat");
            float[] emb2 = service.embed("A feline rested on the rug");
            float[] emb3 = service.embed("Stock prices rose today");

            double sim12 = cosineSimilarity(emb1, emb2);
            double sim13 = cosineSimilarity(emb1, emb3);

            System.out.println("\nSemantic similarity test:");
            System.out.println("  Similar sentences similarity: " + sim12);
            System.out.println("  Different sentences similarity: " + sim13);
            System.out.println("  (Higher = more similar, should see sim12 > sim13)");
        }
    }

    static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

Run:
```bash
mvn compile exec:java -Dexec.mainClass="search.TestOnnx"
```

Expected output:
```
Sentence: The quick brown fox jumps over the lazy dog
  Dimension: 768
  First 5 values: [0.023, -0.015, 0.008, ...]
...
Semantic similarity test:
  Similar sentences similarity: 0.823
  Different sentences similarity: 0.234
  (Higher = more similar, should see sim12 > sim13)
```

### Python Test

```python
import onnxruntime as ort
import numpy as np
from transformers import AutoTokenizer

# Load model
session = ort.InferenceSession("models/onnx/model.onnx")
print(f"Inputs: {[i.name for i in session.get_inputs()]}")
print(f"Outputs: {[o.name for o in session.get_outputs()]}")

# Load tokenizer
tokenizer = AutoTokenizer.from_pretrained("models/onnx/")

# Test inference
text = "Test sentence for embedding"
tokens = tokenizer(text, return_tensors="np", padding=True, truncation=True)
outputs = session.run(None, {"input_ids": tokens["input_ids"]})

print(f"Output shape: {outputs[0].shape}")
print(f"Embedding dimension: {outputs[0].shape[-1]}")
```

## Performance Tuning

### Indexing Speed

To improve indexing performance:

1. **Batch processing**: Modify `IndexEmailsAction.java` to collect emails into batches and use `writer.addDocuments()` instead of `writer.addDocument()`
2. **Use SSD** for the index directory
3. **Disable normalization during inference** if model already outputs normalized vectors (comment out normalization in `OnnxEmbeddingService.embed()`)

### Search Speed

To improve search latency:

1. **Reduce maxResults parameter**: Pass a smaller value when calling `performHybridSearch()`
2. **Use MMapDirectory** for larger indices (automatic in current implementation)
3. **Enable ONNX Runtime optimizations** (already enabled in `SessionOptions`)

## Model Input/Output Format

The `OnnxEmbeddingService` expects models with this interface:

**Input:**
```
Name: input_ids
Shape: [batch_size, sequence_length]
Type: int64
```

**Output:**
```
Name: last_hidden_state (or output)
Shape: [batch_size, sequence_length, hidden_size]
Type: float32
```

Where `hidden_size` must be 768 to match the Lucene index configuration.

## Important: Tokenizer Implementation Status

**Current Limitation:** The `OnnxEmbeddingService` class contains a stub tokenizer implementation (`initializeSimpleTokenizer()`) with only 44 common words. This is not suitable for production use.

**To use the full tokenizer:**

1. Implement proper tokenizer loading from the `tokenizer.json` file, OR
2. Use a library like [tokenizers-java](https://github.com/huggingface/tokenizers) (bindings available via JNI)

For now, the service will work with simple English text but may produce poor embeddings for technical terms, proper nouns, or non-English text.

## Alternative: Using External API

If local ONNX is not feasible:

### OpenAI Embeddings

Add to `pom.xml`:
```xml
<dependency>
    <groupId>com.theokanning.openai-gpt3-java</groupId>
    <artifactId>service</artifactId>
    <version>0.18.2</version>
</dependency>
```

Create `OpenAIEmbeddingService.java`:
```java
package search.adapters;

import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.embedding.EmbeddingRequest;
import search.core.EmbeddingService;

public class OpenAIEmbeddingService implements EmbeddingService {
    private final OpenAiService service;
    private static final String MODEL = "text-embedding-3-small";

    public OpenAIEmbeddingService(String apiKey) {
        this.service = new OpenAiService(apiKey);
    }

    @Override
    public float[] embed(String text) {
        var request = new EmbeddingRequest(MODEL, text);
        var embedding = service.createEmbedding(request).getData().get(0);
        return toFloatArray(embedding.getEmbedding());
    }

    private float[] toFloatArray(java.util.List<Double> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i).floatValue();
        }
        return arr;
    }
}
```

## Next Steps

Once you have ONNX working:

1. **Re-index your data** with real embeddings for better search quality
2. **Experiment with RRF parameters** (currently k=60) for your specific use case
3. **Add phone number extraction** to `EmailParser.java` for better metadata search
4. **Monitor indexing performance** and adjust batch sizes as needed
5. **Implement full tokenizer loading** for production use

## Support

- **ONNX Runtime:** https://onnxruntime.ai/
- **Hugging Face Models:** https://huggingface.co/models?other=onnx
- **Google Embedding Gemma:** https://huggingface.co/google/embeddinggemma-300m
- **ONNX Repository:** https://github.com/onnx/models
