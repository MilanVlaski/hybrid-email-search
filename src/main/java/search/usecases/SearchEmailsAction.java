package search.usecases;

import search.adapters.*;
import search.core.EmailSearchResult;
import search.core.EmbeddingService;
import search.core.HybridSearchEngine;

public class SearchEmailsAction {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: SearchEmailsAction <query> <target-email> <index-dir>");
            System.err.println("Use - as a placeholder for empty values:");
            System.err.println("  SearchEmailsAction \"project discussion\" \"john@company.com\" /path/to/index");
            System.err.println("  SearchEmailsAction \"project discussion\" - /path/to/index   (search all emails)");
            System.err.println("  SearchEmailsAction - \"john@company.com\" /path/to/index   (all content filter)");
            System.exit(1);
        }

        String query = args[0].equals("-") ? "" : args[0];
        String targetEmail = args[1].equals("-") ? "" : args[1];
        String indexDir = args[2];

        EmbeddingService embeddingService = new OnnxEmbeddingService(
                "models/onnx/model.onnx",
                "models/onnx/tokenizer.json"
        );
        try (HybridSearchEngine searchEngine = new LuceneHybridSearchEngine(indexDir, embeddingService)) {
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
        }
    }
}