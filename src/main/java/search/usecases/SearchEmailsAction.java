package search.usecases;

import search.core.EmailSearchResult;
import search.core.EmbeddingService;
import search.core.HybridSearchEngine;
import search.adapters.LuceneHybridSearchEngine;
import search.adapters.MockEmbeddingService;

public class SearchEmailsAction {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: SearchEmailsAction <query> <target-email> <index-dir>");
            System.err.println("Examples:");
            System.err.println("  SearchEmailsAction \"project discussion\" \"john@company.com\" /path/to/index");
            System.err.println("  SearchEmailsAction \"financial report\" \"\" /path/to/index");
            System.exit(1);
        }

        String query = args[0];
        String targetEmail = args[1];
        String indexDir = args[2];

        try {
            EmbeddingService embeddingService = new MockEmbeddingService();
            HybridSearchEngine searchEngine = new LuceneHybridSearchEngine(indexDir, embeddingService);

            System.out.println("Performing hybrid search...");
            System.out.println("Query: " + query);
            System.out.println("Target email: " + (targetEmail.isEmpty() ? "(none)" : targetEmail));
            System.out.println("=".repeat(80));

            EmailSearchResult[] results = searchEngine.performHybridSearch(query, targetEmail, 20);

            if (results.length == 0) {
                System.out.println("No results found.");
                return;
            }

            System.out.println("Found " + results.length + " results:\n");
            
            for (int i = 0; i < results.length; i++) {
                EmailSearchResult result = results[i];
                System.out.println("Result #" + (i + 1));
                System.out.println("-".repeat(40));
                System.out.println("From: " + result.fromName() + " <" + result.fromEmail() + ">");
                System.out.println("To: " + result.allRecipients());
                System.out.println("Subject: " + result.subject());
                System.out.println("Date: " + new java.util.Date(result.timestampMs()));
                System.out.println("Has attachments: " + result.hasAttachments());
                System.out.println("Labels: " + result.labels());
                System.out.println("\nSnippet:");
                System.out.println(result.bodySnippet());
                System.out.println("\n");
            }

            if (searchEngine instanceof LuceneHybridSearchEngine luceneEngine) {
                luceneEngine.close();
            }

        } catch (Exception e) {
            System.err.println("Search failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}