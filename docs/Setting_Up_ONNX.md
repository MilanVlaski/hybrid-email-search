# Setting Up ONNX Embeddings with Gemma

This guide explains how to obtain and configure the ONNX model and tokenizer for production-ready embeddings.

## Overview

The `OnnxEmbeddingService` requires two files:
1. **ONNX Model** (`.onnx`) - The neural network model for generating embeddings
2. **Tokenizer** (`.json`) - Converts text into token IDs for the model

By default, the application uses `MockEmbeddingService` which generates deterministic vectors for testing. This guide helps you switch to real embeddings.

## Quick Start

### Option 1: Download Pre-Converted Gemma Model (Recommended)

The easiest approach is to download a pre-converted ONNX model:

1. **Download from Hugging Face:**
   ```bash
   # Install git-lfs first if you don't have it
   git lfs install
   
   # Download the model
   git clone https://huggingface.co/kadir/gemma-2b-embedding-onnx
   cd gemma-2b-embedding-onnx
   ```

2. **Files you'll get:**
   - `model.onnx` - The embedding model
   - `tokenizer.json` - The tokenizer
   - `config.json` - Model configuration

3. **Move to project directory:**
   ```bash
   mkdir -p models
   cp model.onnx tokenizer.json /path/to/advanced-search/models/
   ```

### Option 2: Convert Your Own Model

If you want to convert a different model:

#### Step 1: Install Required Tools

```bash
pip install torch transformers onnx huggingface_hub
```

#### Step 2: Choose a Model

Good options for embeddings:
- `google/gemma-2b` (requires license acceptance)
- `sentence-transformers/all-MiniLM-L6-v2` (no license needed)
- `microsoft/DialoGPT-small` (no license needed)

#### Step 3: Convert to ONNX

```python
from transformers import AutoTokenizer, AutoModel
import torch
import onnx
from pathlib import Path

def convert_to_onnx(model_name, output_dir):
    """Convert a HuggingFace model to ONNX format"""
    
    # Create output directory
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    
    # Load model and tokenizer
    print(f"Loading {model_name}...")
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModel.from_pretrained(model_name)
    model.eval()
    
    # Save tokenizer
    tokenizer.save_pretrained(output_dir)
    
    # Create dummy input
    dummy_input = torch.randint(0, tokenizer.vocab_size, (1, 128))
    
    # Export to ONNX
    print("Converting to ONNX...")
    torch.onnx.export(
        model,
        dummy_input,
        output_path / "model.onnx",
        input_names=['input_ids'],
        output_names=['output'],
        dynamic_axes={
            'input_ids': {0: 'batch', 1: 'sequence'},
            'output': {0: 'batch', 1: 'sequence'}
        },
        opset_version=14
    )
    
    print(f"Model saved to {output_dir}/model.onnx")
    print(f"Tokenizer saved to {output_dir}/tokenizer.json")

# Example usage
convert_to_onnx("sentence-transformers/all-MiniLM-L6-v2", "./models")
```

## Configuration

### Update Indexing Command

When building the search index, use the ONNX service:

```bash
# Instead of (using mock):
mvn exec:java -Dexec.mainClass="search.usecases.IndexEmailsAction" \
  -Dexec.args="emails.db index"

# Use (with ONNX):
mvn exec:java -Dexec.mainClass="search.usecases.IndexEmailsAction" \
  -Dexec.args="emails.db index models/model.onnx models/tokenizer.json"
```

### Update Search Command

```bash
# Instead of:
mvn exec:java -Dexec.mainClass="search.usecases.SearchEmailsAction" \
  -Dexec.args="'project discussion' 'john@enron.com' index"

# Use:
mvn exec:java -Dexec.mainClass="search.usecases.SearchEmailsAction" \
  -Dexec.args="'project discussion' 'john@enron.com' index models/model.onnx models/tokenizer.json"
```

## Model Specifications

### Recommended Models

| Model | Dimensions | Size | Speed | Quality |
|-------|------------|------|-------|---------|
| Gemma 2B Embedding | 768 | ~4GB | Medium | Excellent |
| all-MiniLM-L6-v2 | 384 | ~90MB | Fast | Good |
| MPNet Base | 768 | ~420MB | Medium | Very Good |

**For this project:** Use 768-dimensional models to match the expected vector dimension.

### Hardware Requirements

- **CPU:** ARM64 or x86_64 with AVX2 support (recommended)
- **RAM:** 8GB minimum, 16GB recommended for Gemma 2B
- **Disk:** 5GB free space for model + index

### Model Input/Output Format

The `OnnxEmbeddingService` expects:

**Input:**
```
Name: `input_ids`
Shape: `[batch_size, sequence_length]`
Type: `int64`
Range: `[0, vocab_size]`
```

**Output:**
```
Name: `output` (or `last_hidden_state`)
Shape: `[batch_size, sequence_length, hidden_size]`
Type: `float32`
```

Where `hidden_size` should be 768.

## Troubleshooting

### "Model loading failed"

**Problem:** ONNX Runtime can't load the model

**Solutions:**
1. Verify model file exists: `ls -lh models/model.onnx`
2. Check ONNX Runtime version matches model opset:
   ```bash
   python -c "import onnx; print(onnx.load('models/model.onnx').opset_import)"
   ```
3. Try a different model format (onnxruntime-gpu vs onnxruntime)

### "Tokenizer not found"

**Problem:** tokenizer.json is missing

**Solutions:**
1. Ensure tokenizer.json is in the same directory as model.onnx
2. Check file permissions: `chmod 644 models/tokenizer.json`
3. Verify JSON is valid: `python -m json.tool models/tokenizer.json > /dev/null`

### Poor embedding quality

**Problem:** Search results don't make sense

**Solutions:**
1. Verify vector dimension matches (must be 768):
   ```python
   import onnx
   model = onnx.load("models/model.onnx")
   dim = model.graph.output[0].type.tensor_type.shape.dim[2].dim_value
   print(f"Vector dimension: {dim}")
   ```
2. Use a better-suited model (Gemma 2B > MiniLM > Word2Vec)
3. Ensure model is for embeddings, not generation
4. Check input preprocessing matches model expectations

### Out of memory

**Problem:** Java heap error during indexing

**Solutions:**
1. Increase Java heap space:
   ```bash
   export MAVEN_OPTS="-Xmx8g -Xms4g"
   ```
2. Reduce batch size in `IndexEmailsAction.java`
3. Use a smaller model (MiniLM instead of Gemma)

## Performance Tuning

### Indexing Speed

To improve indexing performance:

1. **Increase batch size** in `IndexEmailsAction.java`:
   ```java
   private static final int BATCH_SIZE = 5000; // Increase from 1000
   ```

2. **Use SSD** for index directory

3. **Parallel processing** (future enhancement):
   ```java
   // Use parallel streams or multiple threads
   emails.parallelStream().forEach(email -> {
       Document doc = createDocument(email, embeddingService);
       writer.addDocument(doc);
   });
   ```

### Search Speed

To improve search latency:

1. **Reduce RRF candidates** in `LuceneHybridSearchEngine.java`:
   ```java
   private static final int SEMANTIC_CANDIDATES = 50; // Reduce from 100
   private static final int KEYWORD_CANDIDATES = 50;
   ```

2. **Use MMapDirectory** for larger indices:
   ```java
   Directory directory = new MMapDirectory(Paths.get(indexDir));
   ```

## Verification

Test that your setup works:

```bash
# Generate embedding for test text
java -cp target/classes:target/dependency/* \
  -c 'search.adapters.OnnxEmbeddingService models/model.onnx models/tokenizer.json'

# Expected: 768-dimensional float array printed
```

Or use Python:

```python
import onnxruntime as ort
import json

# Load model
session = ort.InferenceSession("models/model.onnx")
print(f"Model loaded: {session.get_modelmeta().description}")

# Test tokenizer
tokenizer = json.load(open("models/tokenizer.json"))
print(f"Vocab size: {len(tokenizer.get('model', {}).get('vocab', {}))}")
```

## Alternative: Using OpenAI/DJL Instead

If ONNX is too complex, consider these alternatives:

### OpenAI Embeddings
```java
// Add dependency
// implementation 'com.theokanning.openai-gpt3-java:service:0.18.2'

public class OpenAIEmbeddingService implements EmbeddingService {
    private final OpenAiService service;
    
    public float[] embed(String text) {
        Embedding embedding = service.createEmbedding(
            new EmbeddingRequest("text-embedding-ada-002", text)
        );
        return embedding.getValues();
    }
}
```

### DJL (Deep Java Library)
```java
// Add dependency
// implementation 'ai.djl.huggingface:tokenizers'
// implementation 'ai.djl.onnxruntime:onnxruntime-engine'

public class DjlEmbeddingService implements EmbeddingService {
    private final Predictor<String, float[]> predictor;
    
    public float[] embed(String text) {
        return predictor.predict(text);
    }
}
```

## Next Steps

Once you have ONNX working:

1. **Re-index your data** with real embeddings for better search quality
2. **Experiment with different models** to find the best quality/speed tradeoff
3. **Add phone number extraction** to `EmailParser.java` for better metadata search
4. **Tune RRF parameters** for your specific use case

## Support

- **ONNX Runtime:** https://onnxruntime.ai/
- **Hugging Face Models:** https://huggingface.co/models?other=onnx
- **ONNX Repository:** https://github.com/onnx/models
- **Project Issues:** [Link to your GitHub issues]
