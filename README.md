# Advanced Hybrid Search

This system implements a hybrid search architecture using Apache Lucene for high-performance indexing and embeddings for generating 768-dimensional vectors. It utilizes HNSW graph-based vector search with int8 scalar quantization for CPU efficiency, merged with classic keyword indexing via Reciprocal Rank Fusion (RRF).

## Prerequisites

- **Java 21** or higher
- **Maven**
- **Enron Dataset**: You must download the Enron dataset from [here](https://www.kaggle.com/datasets/wcukierski/enron-email-dataset), and extract `emails.csv` into the root directory of this project.
- Follow the guide to [set up ONNX](/docs/Setting_Up_ONNX.md)

## Building the Project

To compile the Java source code, run the following command:

```bash
mvn clean compile
```

## Running the Application

### 1. Load Data into SQLite Database

To parse the `emails.csv` dataset and load it into a local SQLite database (`emails.db`), run:

```bash
mvn exec:java -Dexec.mainClass="search.usecases.LoadEmailsCsvAction" -Dexec.args="emails.csv emails.db"
```

*(Note: The current ingestion process is configured to load a sample size of 10,000 records for fast verification.)*

### 2. Verify Database Contents

To query the database and verify that the emails were successfully loaded and parsed, run:

```bash
mvn exec:java -Dexec.mainClass="search.usecases.VerifyDatabaseAction"
```

### 3. Build Lucene Search Index

Create the hybrid search index from the SQLite database. This enables both semantic (vector) and keyword search:

```bash
mvn exec:java -Dexec.mainClass="search.usecases.IndexEmailsAction" -Dexec.args="emails.db index"
```

This will create an `index` directory containing the Lucene index with:
- Vector embeddings for semantic search
- Keyword fields for exact email matching  
- Full-text fields for body text search
- Reciprocal Rank Fusion (RRF) for combined ranking

### 4. Perform Hybrid Search

Search emails using a combination of semantic understanding and exact keyword matching:

```bash
# Search by semantic query only
mvn exec:java -Dexec.mainClass="search.usecases.SearchEmailsAction" -Dexec.args="\"project discussion\" - index"

# Search with both semantic query and target email for exact matching
mvn exec:java -Dexec.mainClass="search.usecases.SearchEmailsAction" -Dexec.args="\"financial meeting\" \"john@enron.com\" index"

# Search with just an email to find all messages from/to a specific address
mvn exec:java -Dexec.mainClass="search.usecases.SearchEmailsAction" -Dexec.args="- \"jane@enron.com\" index"
```

Results are ranked using Reciprocal Rank Fusion (RRF), which intelligently combines:
- Semantic similarity scores (from vector embeddings)
- Exact keyword matches (emails, phone numbers)
- Full-text relevance (body content)

## Architecture

The system follows **Hexagonal Architecture (Ports & Adapters)**:

- **Core Domain** (`search.core`): Pure Java with no framework dependencies
  - `Email`: Domain entity
  - `EmailSearchResult`: Search result DTO
  - `EmbeddingService`: Port for vector generation
  - `HybridSearchEngine`: Port for search operations

- **Adapters** (`search.adapters`): Framework-specific implementations
  - `EmailDatabase`: SQLite persistence
  - `MockEmbeddingService`: Test vector generation (SHA-256 based)
  - `OnnxEmbeddingService`: ONNX Runtime with Gemma model (production-ready)
  - `LuceneHybridSearchEngine`: Lucene-based hybrid search with RRF

- **Use Cases** (`search.usecases`): Application layer orchestration
  - `LoadEmailsCsvAction`: Data ingestion CLI
  - `IndexEmailsAction`: Index building CLI  
  - `SearchEmailsAction`: Hybrid search CLI
  - `VerifyDatabaseAction`: Data verification CLI

All core business logic can run without databases or web servers, enabling true "headless" unit testing.

## Configuration

### Embedding Model

For production use, replace `MockEmbeddingService` with `OnnxEmbeddingService`:

```java
// Requires ONNX model file (e.g., embedding-gemma-300m)
EmbeddingService embeddingService = new OnnxEmbeddingService("/path/to/model.onnx", "/path/to/tokenizer.json");
```

MockEmbeddingService provides deterministic vectors for testing without model dependencies.

### Index Optimization

By default, the index uses:
- **Vector quantization**: int8 scalar quantization (75% memory reduction)
- **HNSW parameters**: M=12, beamWidth=60 (CPU-optimized)
- **Batch size**: 1000 documents per batch
- **Limit**: 10,000 emails indexed (configurable in `IndexEmailsAction`)

Tune these parameters in `IndexEmailsAction.java` for your hardware and dataset size.

## License

[Your License Here]
