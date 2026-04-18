## Setup functions
- Reads Enron email dataset into SQLite db
## App functions
- public void indexEmails() // reads from sqlite db (same as above)
- performHybridSearch()
```java
public EmailSearchResult performHybridSearch(String userQueryText, String targetEmail)

public record EmailSearchResult(
    String messageId,
    String fromEmail,
    String fromName,
    String allRecipients,
    String subject,
    String bodySnippet,
    String bodyContent,
    long timestampMs,
    boolean hasAttachments,
    String labels
) {}
```
## Implementation steps and notes

- Come up with a database schema for emails (main Email table, with potentially others).
- Write Java main class to load an enron data set, and parse it into the local SQLite db.
- Provide scripts or something, to verify that the DB indeed contains the email entries.
- Create the two functions of the app — indexing and hybrid searching

The functions of the app can be "main methods" runnable from an IDE. No fluff.

## Deep implementation spec

Here is the refined Implementation Spec in English, structured as a single comprehensive guide for your project.
------------------------------
## Implementation Spec: Hybrid Semantic Search (Lucene & Gemma)## 1. Objective
Build a CPU-optimized email search engine that combines Semantic Understanding (via Gemma embeddings) with Exact Metadata Matching (for emails/phones) using Reciprocal Rank Fusion (RRF) for final ranking.
## 2. Technical Architecture## A. Indexing Strategy

| Field | Lucene Type | Indexing Logic | Purpose |
|---|---|---|---|
| content_vector | KnnFloatVectorField | HNSW + int8 Quantization | Semantic/Human intent |
| sender_email | StringField | No Tokenization | Exact email match |
| phone_num | StringField | No Tokenization | Exact phone match |
| body_text | TextField | StandardAnalyzer | Classic keyword search |

## B. CPU & Memory Optimizations

* Vector Compression: Use int8 scalar quantization to reduce RAM usage by 75% (from 4 bytes to 1 byte per dimension).
* HNSW Tuning: Reduce M (max connections) to 12 and beamWidth to 60 to speed up indexing on consumer CPUs.
* Model Choice: Use embedding-gemma-300m via ONNX Runtime for low-latency CPU inference.

------------------------------
## 3. Java Implementation Reference## Step 1: Optimized IndexWriter Setup

// Define the 1-byte quantization format to save RAM/CPU
KnnVectorsFormat quantizedFormat = new Lucene99HnswScalarQuantizedVectorsFormat();
// Apply the format to a custom Codec
Codec customCodec = new Lucene99Codec() {
@Override
public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
return quantizedFormat;
}
};

IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
config.setCodec(customCodec);
IndexWriter writer = new IndexWriter(FSDirectory.open(Paths.get("/index")), config);

## Step 2: Indexing Document with Gemma

Document doc = new Document();// Gemma embedding (768 dimensions)float[] vector = gemmaModel.embed(emailBody);

doc.add(new KnnFloatVectorField("content_vector", vector, VectorSimilarityFunction.COSINE));
doc.add(new StringField("sender", "dev@example.com", Field.Store.YES));
doc.add(new TextField("body", emailBody, Field.Store.YES));

writer.addDocument(doc);

## Step 3: Hybrid Search with RRF Ranking

// 1. Get Top 10 Semantic Results
Query vQuery = new KnnFloatVectorQuery("content_vector", queryVector, 10);
TopDocs semanticHits = searcher.search(vQuery, 10);
// 2. Get Top 10 Keyword Results
Query kQuery = new TermQuery(new Term("sender", "dev@example.com"));
TopDocs keywordHits = searcher.search(kQuery, 10);
// 3. Merge using RRF (Score = 1 / (60 + rank))
Map<Integer, Float> rrfMap = new HashMap<>();for (int i = 0; i < semanticHits.scoreDocs.length; i++) {
rrfMap.put(semanticHits.scoreDocs[i].doc, 1.0f / (60 + (i + 1)));
}for (int i = 0; i < keywordHits.scoreDocs.length; i++) {
int id = keywordHits.scoreDocs[i].doc;
rrfMap.put(id, rrfMap.getOrDefault(id, 0f) + (1.0f / (60 + (i + 1))));
}

------------------------------
## 4. Key Takeaways

* Precision: Using StringField for emails/phones bypasses the AI's "fuzziness," ensuring you find exact contacts.
* Speed: Quantization and HNSW tuning make the vector search performant on standard servers without GPUs.
* Ranking: RRF eliminates the need to manually "weight" vector vs. text scores, as it relies on rank position rather than raw score.
