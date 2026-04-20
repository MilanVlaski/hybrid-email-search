# Lucene & Gemma Hybrid Search Optimizations

This document outlines the performance tuning techniques and best practices for running hybrid search with Apache Lucene and the EmbeddingGemma model on CPU. These optimizations reduce memory footprint, decrease CPU load during indexing, and improve semantic search accuracy.

## 1. Int8 Quantization & HNSW Parameter Tuning

By default, 768-dimensional `float32` vectors consume significant memory and CPU cycles during distance calculations. Int8 quantization reduces memory usage by 75%. Lowering the HNSW graph parameters (`m` and `beamWidth`) reduces CPU overhead during index construction.

```java
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99Codec;
import org.apache.lucene.codecs.lucene99.Lucene99HnswScalarQuantizedVectorsFormat;

// 1. Set tuned parameters to lower CPU load during indexing
int maxConn = 12;    // Reduced from default 16
int beamWidth = 60;  // Reduced from default 100

// 2. Create Quantized Format (compresses float32 to int8)
KnnVectorsFormat quantizedFormat = new Lucene99HnswScalarQuantizedVectorsFormat(maxConn, beamWidth);

// 3. Wrap in Custom Codec
Lucene99Codec customCodec = new Lucene99Codec() {
    @Override
    public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
        if ("content_vector".equals(field)) {
            return quantizedFormat;
        }
        return super.getKnnVectorsFormatForField(field);
    }
};

// 4. Apply to IndexWriterConfig
IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
config.setCodec(customCodec);
```

## 2. MMapDirectory for Memory Safety

When running vector search on CPU, avoid `ByteBuffersDirectory` or standard `FSDirectory` which can cause Java Heap OutOfMemory (OOM) errors. Use `MMapDirectory` so the OS handles memory mapping efficiently directly to the disk cache.

```java
import org.apache.lucene.store.MMapDirectory;
import java.nio.file.Paths;

IndexWriter writer = new IndexWriter(MMapDirectory.open(Paths.get("/path/to/index")), config);
```

## 3. Gemma Instructional Prompts

The EmbeddingGemma model performs best when given instructional prefixes to distinguish between the data being indexed and the query being searched.

*   **Indexing**: Prefix text with `"document: "`
*   **Searching**: Prefix user queries with `"query: "`

```java
// During Indexing
float[] vector = predictor.predict("document: " + email.body());
doc.add(new KnnFloatVectorField("content_vector", vector, VectorSimilarityFunction.COSINE));

// During Searching
float[] queryVector = predictor.predict("query: " + userQuery);
Query vectorQuery = new KnnFloatVectorQuery("content_vector", queryVector, 10);
```

## 4. Reciprocal Rank Fusion (RRF)

When combining vector search (semantic similarity) and classic keyword search (e.g., exact email/phone matches), use RRF instead of adding them directly into a standard `BooleanQuery`. Vector scores (cosine similarity) and Keyword scores (BM25) are on totally different scales, which can cause one field to drown out the other.

RRF resolves this by caring only about the rank position (e.g., #1, #2), not the raw score.

```java
// Standard RRF formula implementation over TopDocs results
// For both vector and keyword TopDocs:
for (int rank = 0; rank < results.scoreDocs.length; rank++) {
    int docID = results.scoreDocs[rank].doc;
    // K is typically 60
    float rrfScore = 1.0f / (60 + (rank + 1));
    // Accumulate rrfScore in a Map<Integer, Float> for final ranking
}
```

## 5. Field Mapping for Hybrid Search

*   **Vector Search**: `KnnFloatVectorField` (768 dimensions, Cosine Similarity) for body text.
*   **Exact Matches**: `StringField` (not tokenized) for metadata like emails and phone numbers to prevent `@` and `-` from being stripped by analyzers.
*   **Keyword Search**: `TextField` for classic tokenized text search (BM25) on body text as a fallback.