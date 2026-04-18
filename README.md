# Advanced Hybrid Search

This system implements a hybrid search architecture using Apache Lucene for high-performance indexing and Gemma (via ONNX or DJL) for generating 768-dimensional embeddings. It utilizes HNSW graph-based vector search with int8 scalar quantization and MMapDirectory for CPU efficiency, merged with classic keyword indexing via Reciprocal Rank Fusion (RRF).

## Prerequisites

- **Java 21** or higher
- **Maven**
- **Enron Dataset**: You must download the Enron dataset (`emails.csv`) and place it in the root directory of this project.

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
