package search.usecases;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class VerifyDatabaseAction {
    public static void main(String[] args) throws Exception {
        var dbPath = args.length > 0 ? args[0] : "emails.db";
        var dbUrl = "jdbc:sqlite:" + dbPath;
        try (var conn = DriverManager.getConnection(dbUrl);
             var stmt = conn.createStatement()) {

            var rsCount = stmt.executeQuery("SELECT count(*) FROM emails");
            System.out.println("Total emails in database: " + rsCount.getInt(1));
            rsCount.close();

            System.out.println("\nSample Records:");
            var rs = stmt.executeQuery(
                "SELECT id, message_id, from_email, subject, " +
                "length(body_content) as body_length FROM emails LIMIT 3"
            );
            while (rs.next()) {
                System.out.printf("ID: %d%n", rs.getInt("id"));
                System.out.printf("Message ID: %s%n", rs.getString("message_id"));
                System.out.printf("From: %s%n", rs.getString("from_email"));
                System.out.printf("Subject: %s%n", rs.getString("subject"));
                System.out.printf("Body Length: %d%n", rs.getInt("body_length"));
                System.out.println("-".repeat(40));
            }
            rs.close();
        }
    }
}
