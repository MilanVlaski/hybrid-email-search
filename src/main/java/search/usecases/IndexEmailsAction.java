package search.usecases;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99Codec;
import org.apache.lucene.codecs.lucene99.Lucene99HnswScalarQuantizedVectorsFormat;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;
import search.core.Email;
import search.core.EmbeddingService;
import search.adapters.EmailDatabase;
import search.adapters.MockEmbeddingService;

import java.nio.file.Paths;
import java.sql.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class IndexEmailsAction {
    private static final Logger logger = Logger.getLogger(IndexEmailsAction.class.getName());
    private static final int VECTOR_DIMENSION = 768;
    private static final int MAX_EMAILS_TO_INDEX = 10000; // Limit for MVP

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: IndexEmailsAction <db-file> <index-dir>");
            System.exit(1);
        }

        String dbPath = args[0];
        String indexDir = args[1];

        try {
            // Use MockEmbeddingService for MVP - replace with OnnxEmbeddingService for production
            EmbeddingService embeddingService = new MockEmbeddingService();
            
            // Build index
            new IndexEmailsAction().indexEmails(dbPath, indexDir, embeddingService);
            
            System.out.println("Indexing completed successfully.");
        } catch (Exception e) {
            System.err.println("Failed to index emails: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void indexEmails(String dbPath, String indexDir, EmbeddingService embeddingService) throws Exception {
        // Setup Lucene with int8 quantization
        KnnVectorsFormat quantizedFormat = new Lucene99HnswScalarQuantizedVectorsFormat(
            256, // numMergeWorkers
            1024 // scalarQuantizerBits
        );
        
        Codec customCodec = new Lucene99Codec() {
            @Override
            public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
                return quantizedFormat;
            }
        };

        IndexWriterConfig config = new IndexWriterConfig();
        config.setCodec(customCodec);
        
        try (IndexWriter writer = new IndexWriter(
            FSDirectory.open(Paths.get(indexDir)), config);
             Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM emails");
            int totalEmails = rs.getInt(1);
            rs.close();
            
            System.out.println("Total emails in database: " + totalEmails);
            System.out.println("Will index up to " + MAX_EMAILS_TO_INDEX + " emails.");

            rs = stmt.executeQuery(
                "SELECT message_id, from_email, from_name, all_recipients, " +
                "subject, body_content, timestamp_ms, has_attachments, labels " +
                "FROM emails LIMIT " + MAX_EMAILS_TO_INDEX
            );

            int count = 0;
            while (rs.next()) {
                Email email = mapToEmail(rs);
                Document doc = createDocument(email, embeddingService);
                writer.addDocument(doc);
                
                count++;
                if (count % 100 == 0) {
                    System.out.println("Indexed " + count + " emails...");
                }
            }
            
            System.out.println("Finished indexing " + count + " emails.");
        }
    }

    private Email mapToEmail(ResultSet rs) throws SQLException {
        return new Email(
            rs.getString("message_id"),
            rs.getString("from_email"),
            rs.getString("from_name"),
            rs.getString("all_recipients"),
            rs.getString("subject"),
            rs.getString("body_content"),
            rs.getLong("timestamp_ms"),
            rs.getBoolean("has_attachments"),
            rs.getString("labels")
        );
    }

    private Document createDocument(Email email, EmbeddingService embeddingService) {
        Document doc = new Document();

        // Store fields for retrieval
        doc.add(new StoredField("message_id", email.messageId() != null ? email.messageId() : ""));
        doc.add(new StoredField("from_email", email.fromEmail() != null ? email.fromEmail() : ""));
        doc.add(new StoredField("from_name", email.fromName() != null ? email.fromName() : ""));
        doc.add(new StoredField("all_recipients", email.allRecipients() != null ? email.allRecipients() : ""));
        doc.add(new StoredField("subject", email.subject() != null ? email.subject() : ""));
        doc.add(new StoredField("body_content", email.bodyContent() != null ? email.bodyContent() : ""));
        doc.add(new StoredField("timestamp_ms", email.timestampMs()));
        doc.add(new StoredField("has_attachments", email.hasAttachments() ? 1 : 0));
        doc.add(new StoredField("labels", email.labels() != null ? email.labels() : ""));

        // Indexed fields for search
        // 1. Vector embedding for semantic search
        String subject = email.subject() != null ? email.subject() : "";
        String body = email.bodyContent() != null ? email.bodyContent() : "";
        String textToEmbed = subject + " " + body;
        if (textToEmbed.length() > 512) {
            textToEmbed = textToEmbed.substring(0, 512);
        }
        
        try {
            float[] vector = embeddingService.embed(textToEmbed);
            if (vector.length != VECTOR_DIMENSION) {
                logger.warning("Vector dimension mismatch: expected " + VECTOR_DIMENSION + ", got " + vector.length);
            }
            doc.add(new KnnFloatVectorField("content_vector", vector, VectorSimilarityFunction.COSINE));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to embed email " + email.messageId() + ", using zero vector", e);
            doc.add(new KnnFloatVectorField("content_vector", new float[VECTOR_DIMENSION], VectorSimilarityFunction.COSINE));
        }

        // 2. Sender email for exact match
        if (email.fromEmail() != null && !email.fromEmail().isEmpty()) {
            doc.add(new StringField("sender_email", email.fromEmail(), Field.Store.YES));
        }

        // 3. Phone numbers (extract from body)
        String phoneNumbers = extractPhoneNumbers(email.bodyContent());
        if (!phoneNumbers.isEmpty()) {
            doc.add(new StringField("phone_num", phoneNumbers, Field.Store.YES));
        }

        // 4. Full-text search on body
        if (email.bodyContent() != null && !email.bodyContent().isEmpty()) {
            doc.add(new TextField("body_text", email.bodyContent(), Field.Store.NO));
        }

        return doc;
    }

    private String extractPhoneNumbers(String text) {
        if (text == null) return "";
        // TODO: Implement phone number extraction using regex
        return "";
    }
}