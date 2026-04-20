package search.adapters;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.index.Term;

import search.core.Hit;
import search.core.Embedder;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class Search implements AutoCloseable {
    private final IndexReader reader;
    private final IndexSearcher searcher;
    private final Embedder embedder;
    private static final int RRF_K = 60; // RRF constant
    private static final int DEFAULT_MAX_RESULTS = 100;

    public Search(String indexDir, Embedder embedder) throws IOException {
        this.reader = DirectoryReader.open(MMapDirectory.open(Paths.get(indexDir)));
        this.searcher = new IndexSearcher(reader);
        this.embedder = embedder;
    }

    
    public Hit[] performHybridSearch(String userQueryText, String targetEmail, int maxResults) {
        if (maxResults <= 0) maxResults = DEFAULT_MAX_RESULTS;

        try {
            // Component 1: Semantic vector search
            var semanticResults = performSemanticSearch(userQueryText, maxResults);

            // Component 2: Exact keyword search
            var keywordResults = performKeywordSearch(targetEmail, maxResults);

            // Component 3: Reciprocal Rank Fusion
            var rrfScores = computeRRFScores(semanticResults, keywordResults);

            // Sort by RRF score and get top results
            var sortedResults = rrfScores.entrySet().stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                    .limit(maxResults)
                    .toList();

            return convertToSearchResults(sortedResults);

        } catch (Exception e) {
            throw new RuntimeException("Hybrid search failed", e);
        }
    }

    private TopDocs performSemanticSearch(String queryText, int maxResults) throws IOException {
        if (queryText == null || queryText.trim().isEmpty()) {
            return new TopDocs(new TotalHits(0, TotalHits.Relation.EQUAL_TO), new ScoreDoc[0]);
        }

        var queryVector = embedder.embed("query: " + queryText);
        var vectorQuery = new KnnFloatVectorQuery("content_vector", queryVector, maxResults);
        return searcher.search(vectorQuery, maxResults);
    }

    private TopDocs performKeywordSearch(String targetEmail, int maxResults) throws IOException {
        if (targetEmail == null || targetEmail.trim().isEmpty()) {
            return new TopDocs(new TotalHits(0, TotalHits.Relation.EQUAL_TO), new ScoreDoc[0]);
        }

        // Create boolean query for email and phone exact matches
        var queryBuilder = new BooleanQuery.Builder();

        // Exact email match
        var emailQuery = new TermQuery(new Term("sender_email", targetEmail.toLowerCase().trim()));
        queryBuilder.add(emailQuery, BooleanClause.Occur.SHOULD);

        // Exact phone match (if it looks like a phone number)
        var cleanedPhone = extractPhoneNumber(targetEmail);
        if (!cleanedPhone.isEmpty()) {
            var phoneQuery = new TermQuery(new Term("phone_num", cleanedPhone));
            queryBuilder.add(phoneQuery, BooleanClause.Occur.SHOULD);
        }

        // Text body search as fallback
        var parser = new QueryParser("body_text", new StandardAnalyzer());
        try {
            var textQuery = parser.parse(targetEmail);
            queryBuilder.add(textQuery, BooleanClause.Occur.SHOULD);
        } catch (Exception e) {
            // If parsing fails, use a simple term query
            var textQuery = new TermQuery(new Term("body_text", targetEmail.toLowerCase()));
            queryBuilder.add(textQuery, BooleanClause.Occur.SHOULD);
        }

        return searcher.search(queryBuilder.build(), maxResults);
    }

    private Map<Integer, Float> computeRRFScores(TopDocs semanticResults, TopDocs keywordResults) {
        var rrfScores = new HashMap<Integer, Float>();

        // Add semantic results with rank-based scoring
        for (var i = 0; i < semanticResults.scoreDocs.length; i++) {
            var docId = semanticResults.scoreDocs[i].doc;
            var score = 1.0f / (RRF_K + (i + 1));
            rrfScores.put(docId, rrfScores.getOrDefault(docId, 0f) + score);
        }

        // Add keyword results with rank-based scoring
        for (var i = 0; i < keywordResults.scoreDocs.length; i++) {
            var docId = keywordResults.scoreDocs[i].doc;
            var score = 1.0f / (RRF_K + (i + 1));
            rrfScores.put(docId, rrfScores.getOrDefault(docId, 0f) + score);
        }

        return rrfScores;
    }

    private Hit[] convertToSearchResults(List<Map.Entry<Integer, Float>> sortedResults) {
        var results = new ArrayList<Hit>();

        for (var entry : sortedResults) {
            try {
                var doc = reader.document(entry.getKey());
                results.add(mapToHit(doc));
            } catch (IOException e) {
                // Skip document if we can't read it
                System.err.println("Warning: Failed to read document " + entry.getKey() + ": " + e.getMessage());
            }
        }

        return results.toArray(new Hit[0]);
    }

    private Hit mapToHit(Document doc) {
        var bodyContent = doc.get("body_content");
        var bodySnippet = bodyContent != null && bodyContent.length() > 200
                ? bodyContent.substring(0, 200) + "..."
                : bodyContent != null ? bodyContent : "";

        var timestampStr = doc.get("timestamp_ms");
        var timestampMs = 0L;
        if (timestampStr != null && !timestampStr.isEmpty()) {
            try {
                timestampMs = Long.parseLong(timestampStr);
            } catch (NumberFormatException e) {
                timestampMs = System.currentTimeMillis();
            }
        }

        return new Hit(
                doc.get("message_id"),
                doc.get("from_email"),
                doc.get("from_name"),
                doc.get("all_recipients"),
                doc.get("subject"),
                bodySnippet,
                bodyContent,
                timestampMs,
                "1".equals(doc.get("has_attachments")),
                doc.get("labels")
        );
    }

    private String extractPhoneNumber(String text) {
        if (text == null) return "";
        // Remove non-digits and check if it looks like a phone number
        var digits = text.replaceAll("\\D", "");
        return digits.length() >= 7 && digits.length() <= 15 ? digits : "";
    }

    public void close() throws IOException {
        reader.close();
    }
}