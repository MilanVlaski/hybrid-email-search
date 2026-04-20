package search.usecases;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import search.adapters.Database;
import search.core.Email;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;

public class LoadEmailsCsvAction {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: LoadEmailsCsvAction <csv-file> <db-file>");
            System.exit(1);
        }

        var csvPath = args[0];
        var dbPath = args[1];

        var database = new Database(dbPath);
        database.initialize();

        try (var reader = new FileReader(csvPath);
             var csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).build())) {

            Iterable<Email> emailIterable = () -> new Iterator<>() {
                private final Iterator<CSVRecord> recordIterator = csvParser.iterator();
                private Email nextEmail = null;
                private int readCount = 0;

                private void advance() {
                    if (recordIterator.hasNext() && readCount < 10000) {
                        var record = recordIterator.next();
                        var rawMessage = record.get("message");
                        var fileLabel = record.get("file");
                        nextEmail = Email.parse(rawMessage, fileLabel);
                        readCount++;
                    } else {
                        nextEmail = null;
                    }
                }

                {
                    advance();
                }

                @Override
                public boolean hasNext() {
                    return nextEmail != null;
                }

                @Override
                public Email next() {
                    var current = nextEmail;
                    advance();
                    return current;
                }
            };

            System.out.println("Starting import into " + dbPath
                    + " (limiting to 10,000 records for review)");
            database.insertEmails(emailIterable);
            System.out.println("Import complete.");

        } catch (IOException e) {
            throw new RuntimeException("Failed to read CSV", e);
        }
    }
}
