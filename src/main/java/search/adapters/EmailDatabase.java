package search.adapters;

import search.core.Email;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class EmailDatabase {

    private final String dbUrl;

    public EmailDatabase(String dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
    }

    public void initialize() {
        try (Connection conn = DriverManager.getConnection(dbUrl); Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS emails (" + "id INTEGER PRIMARY KEY AUTOINCREMENT," + "message_id TEXT," + "from_email TEXT," + "from_name TEXT," + "all_recipients TEXT," + "subject TEXT," + "body_content TEXT," + "timestamp_ms INTEGER," + "has_attachments INTEGER," + "labels TEXT" + ")");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public void insertEmails(Iterable<Email> emails) {
        String sql = "INSERT INTO emails(message_id, from_email, from_name, " + "all_recipients, subject, body_content, timestamp_ms, " + "has_attachments, labels) VALUES(?,?,?,?,?,?,?,?,?)";

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            conn.setAutoCommit(false);
            int count = 0;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (Email email : emails) {
                    pstmt.setString(1, email.messageId());
                    pstmt.setString(2, email.fromEmail());
                    pstmt.setString(3, email.fromName());
                    pstmt.setString(4, email.allRecipients());
                    pstmt.setString(5, email.subject());
                    pstmt.setString(6, email.bodyContent());
                    pstmt.setLong(7, email.timestampMs());
                    pstmt.setInt(8, email.hasAttachments() ? 1 : 0);
                    pstmt.setString(9, email.labels());
                    pstmt.addBatch();

                    count++;
                    if (count % 1000 == 0) {
                        pstmt.executeBatch();
                        conn.commit();
                        System.out.printf("Inserted %d emails...%n", count);
                    }
                }
                pstmt.executeBatch();
                conn.commit();
                System.out.printf("Finished inserting %d emails.%n", count);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert emails batch", e);
        }
    }
}
