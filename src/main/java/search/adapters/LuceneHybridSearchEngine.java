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

import search.core.EmailSearchResult;
import search.core.EmbeddingService;
import search.core.HybridSearchEngine;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class LuceneHybridSearchEngine implements HybridSearchEngine {
    private final IndexReader reader;
    private final IndexSearcher searcher;
    private final EmbeddingService embeddingService;
    private static final int RRF_K = 60; // RRF constant
    private static final int DEFAULT_MAX_RESULTS = 100;

    public LuceneHybridSearchEngine(String indexDir, EmbeddingService embeddingService) throws IOException {
        this.reader = DirectoryReader.open(MMapDirectory.open(Paths.get(indexDir)));
        this.searcher = new IndexSearcher(reader);
        this.embeddingService = embeddingService;
    }

    @Override
    public EmailSearchResult[] performHybridSearch(String userQueryText, String targetEmail, int maxResults) {
        if (maxResults <= 0) maxResults = DEFAULT_MAX_RESULTS;

        try {
            // Component 1: Semantic vector search
            TopDocs semanticResults = performSemanticSearch(userQueryText, maxResults);

            // Component 2: Exact keyword search
            TopDocs keywordResults = performKeywordSearch(targetEmail, maxResults);

            // Component 3: Reciprocal Rank Fusion
            Map<Integer, Float> rrfScores = computeRRFScores(semanticResults, keywordResults);

            // Sort by RRF score and get top results
            List<Map.Entry<Integer, Float>> sortedResults = rrfScores.entrySet().stream()
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

        float[] queryVector = embeddingService.embed("query: " + queryText);
        Query vectorQuery = new KnnFloatVectorQuery("content_vector", queryVector, maxResults);
        return searcher.search(vectorQuery, maxResults);
    }

    private TopDocs performKeywordSearch(String targetEmail, int maxResults) throws IOException {
        if (targetEmail == null || targetEmail.trim().isEmpty()) {
            return new TopDocs(new TotalHits(0, TotalHits.Relation.EQUAL_TO), new ScoreDoc[0]);
        }

        // Create boolean query for email and phone exact matches
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

        // Exact email match
        Query emailQuery = new TermQuery(new Term("sender_email", targetEmail.toLowerCase().trim()));
        queryBuilder.add(emailQuery, BooleanClause.Occur.SHOULD);

        // Exact phone match (if it looks like a phone number)
        String cleanedPhone = extractPhoneNumber(targetEmail);
        if (!cleanedPhone.isEmpty()) {
            Query phoneQuery = new TermQuery(new Term("phone_num", cleanedPhone));
            queryBuilder.add(phoneQuery, BooleanClause.Occur.SHOULD);
        }

        // Text body search as fallback
        QueryParser parser = new QueryParser("body_text", new StandardAnalyzer());
        try {
            Query textQuery = parser.parse(targetEmail);
            queryBuilder.add(textQuery, BooleanClause.Occur.SHOULD);
        } catch (Exception e) {
            // If parsing fails, use a simple term query
            Query textQuery = new TermQuery(new Term("body_text", targetEmail.toLowerCase()));
            queryBuilder.add(textQuery, BooleanClause.Occur.SHOULD);
        }

        return searcher.search(queryBuilder.build(), maxResults);
    }

    private Map<Integer, Float> computeRRFScores(TopDocs semanticResults, TopDocs keywordResults) {
        Map<Integer, Float> rrfScores = new HashMap<>();

        // Add semantic results with rank-based scoring
        for (int i = 0; i < semanticResults.scoreDocs.length; i++) {
            int docId = semanticResults.scoreDocs[i].doc;
            float score = 1.0f / (RRF_K + (i + 1));
            rrfScores.put(docId, rrfScores.getOrDefault(docId, 0f) + score);
        }

        // Add keyword results with rank-based scoring
        for (int i = 0; i < keywordResults.scoreDocs.length; i++) {
            int docId = keywordResults.scoreDocs[i].doc;
            float score = 1.0f / (RRF_K + (i + 1));
            rrfScores.put(docId, rrfScores.getOrDefault(docId, 0f) + score);
        }

        return rrfScores;
    }

    private EmailSearchResult[] convertToSearchResults(List<Map.Entry<Integer, Float>> sortedResults) {
        List<EmailSearchResult> results = new ArrayList<>();

        for (Map.Entry<Integer, Float> entry : sortedResults) {
            try {
                Document doc = reader.document(entry.getKey());
                results.add(mapToEmailSearchResult(doc));
            } catch (IOException e) {
                // Skip document if we can't read it
                System.err.println("Warning: Failed to read document " + entry.getKey() + ": " + e.getMessage());
            }
        }

        return results.toArray(new EmailSearchResult[0]);
    }

    private EmailSearchResult mapToEmailSearchResult(Document doc) {
        String bodyContent = doc.get("body_content");
        String bodySnippet = bodyContent != null && bodyContent.length() > 200
                ? bodyContent.substring(0, 200) + "..."
                : bodyContent != null ? bodyContent : "";

        String timestampStr = doc.get("timestamp_ms");
        long timestampMs = 0L;
        if (timestampStr != null && !timestampStr.isEmpty()) {
            try {
                timestampMs = Long.parseLong(timestampStr);
            } catch (NumberFormatException e) {
                timestampMs = System.currentTimeMillis();
            }
        }

        return new EmailSearchResult(
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
        String digits = text.replaceAll("\\D", "");
        return digits.length() >= 7 && digits.length() <= 15 ? digits : "";
    }

    public void close() throws IOException {
        reader.close();
    }
}