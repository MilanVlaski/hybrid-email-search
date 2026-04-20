package search.usecases;

import search.adapters.*;
import search.core.Hit;
import search.core.Embedder;

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

        var query = args[0].equals("-") ? "" : args[0];
        var targetEmail = args[1].equals("-") ? "" : args[1];
        var indexDir = args[2];

        var embedder = new OnnxEmbedder(
                "models/onnx/model.onnx",
                "models/onnx/tokenizer.json"
        );
        try (var search = new Search(indexDir, embedder)) {
            System.out.println("Performing hybrid search...");
            System.out.println("Query: " + query);
            System.out.println("Target email: " + (targetEmail.isEmpty() ? "(none)" : targetEmail));
            System.out.println("=".repeat(80));

            var results = search.performHybridSearch(query, targetEmail, 20);

            if (results.length == 0) {
                System.out.println("No results found.");
                return;
            }

            System.out.println("Found " + results.length + " results:\n");
            
            for (var i = 0; i < results.length; i++) {
                var result = results[i];
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