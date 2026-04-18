package search.usecases;

import search.core.Email;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EmailParser {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
            "EEE, d MMM yyyy HH:mm:ss Z (z)", Locale.US
    );

    public Email parse(String rawMessage, String labels) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return new Email("", "", "", "",
                    "", "", 0L, false, labels);
        }

        String[] lines = rawMessage.split("\\r?\\n");
        Map<String, String> headers = new HashMap<>();
        StringBuilder body = new StringBuilder();

        boolean isHeader = true;
        String lastHeaderKey = null;

        for (String line : lines) {
            if (isHeader) {
                if (line.isEmpty()) {
                    isHeader = false;
                    continue;
                }
                boolean isContinuation = line.startsWith(" ") || line.startsWith("\t");
                if (isContinuation && lastHeaderKey != null) {
                    String currentValue = headers.get(lastHeaderKey);
                    headers.put(lastHeaderKey, currentValue + " " + line.trim());
                } else {
                    int colonIndex = line.indexOf(':');
                    if (colonIndex != -1) {
                        String key = line.substring(0, colonIndex).trim().toLowerCase();
                        String value = line.substring(colonIndex + 1).trim();
                        headers.put(key, value);
                        lastHeaderKey = key;
                    }
                }
            } else {
                body.append(line).append("\n");
            }
        }

        String messageId = headers.getOrDefault("message-id", "");
        String fromEmail = headers.getOrDefault("from", "");
        String fromName = extractName(headers.get("x-from"));
        String to = headers.getOrDefault("to", "");
        String cc = headers.getOrDefault("cc", "");
        String bcc = headers.getOrDefault("bcc", "");

        String allRecipients = to;
        if (!cc.isEmpty()) allRecipients += ", " + cc;
        if (!bcc.isEmpty()) allRecipients += ", " + bcc;

        String subject = headers.getOrDefault("subject", "");
        long timestampMs = parseDate(headers.get("date"));
        String contentType = headers.getOrDefault("content-type", "");
        boolean hasAttachments = contentType.toLowerCase().contains("multipart");

        return new Email(
                messageId,
                fromEmail,
                fromName,
                allRecipients,
                subject,
                body.toString().trim(),
                timestampMs,
                hasAttachments,
                labels
        );
    }

    private String extractName(String xFrom) {
        if (xFrom == null) return "";
        int idx = xFrom.indexOf('<');
        if (idx != -1) {
            return xFrom.substring(0, idx).trim();
        }
        return xFrom.trim();
    }

    private long parseDate(String dateStr) {
        if (dateStr == null) return System.currentTimeMillis();
        try {
            Date d = DATE_FORMAT.parse(dateStr);
            return d.getTime();
        } catch (ParseException e) {
            // Fallback parsing or just return current time for simplicity
            return System.currentTimeMillis();
        }
    }
}
