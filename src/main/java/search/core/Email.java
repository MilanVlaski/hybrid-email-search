package search.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;

public record Email(
    String messageId,
    String fromEmail,
    String fromName,
    String allRecipients,
    String subject,
    String bodyContent,
    long timestampMs,
    boolean hasAttachments,
    String labels
) {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("EEE, d MMM yyyy HH:mm:ss Z (z)", Locale.US)
            .withZone(ZoneId.systemDefault());

    public String bodySnippet() {
        if (bodyContent == null) return "";
        var cleanBody = bodyContent.replaceAll("\\s+", " ").trim();
        return cleanBody.length() > 200 ? cleanBody.substring(0, 200) + "..." : cleanBody;
    }

    public static Email parse(String rawMessage, String labels) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return new Email("", "", "", "", "", "", 0L, false, labels);
        }

        var lines = rawMessage.split("\\r?\\n");
        var headers = new HashMap<String, String>();
        var body = new StringBuilder();

        var isHeader = true;
        String lastHeaderKey = null;

        for (var line : lines) {
            if (isHeader) {
                if (line.isEmpty()) {
                    isHeader = false;
                    continue;
                }
                var isContinuation = line.startsWith(" ") || line.startsWith("\t");
                if (isContinuation && lastHeaderKey != null) {
                    var currentValue = headers.get(lastHeaderKey);
                    headers.put(lastHeaderKey, currentValue + " " + line.trim());
                } else {
                    var colonIndex = line.indexOf(':');
                    if (colonIndex != -1) {
                        var key = line.substring(0, colonIndex).trim().toLowerCase();
                        var value = line.substring(colonIndex + 1).trim();
                        headers.put(key, value);
                        lastHeaderKey = key;
                    }
                }
            } else {
                body.append(line).append("\n");
            }
        }

        var messageId = headers.getOrDefault("message-id", "");
        var fromEmail = headers.getOrDefault("from", "");
        var fromName = extractName(headers.get("x-from"));
        var to = headers.getOrDefault("to", "");
        var cc = headers.getOrDefault("cc", "");
        var bcc = headers.getOrDefault("bcc", "");

        var allRecipients = to;
        if (!cc.isEmpty()) allRecipients += ", " + cc;
        if (!bcc.isEmpty()) allRecipients += ", " + bcc;

        var subject = headers.getOrDefault("subject", "");
        var timestampMs = parseDate(headers.get("date"));
        var contentType = headers.getOrDefault("content-type", "");
        var hasAttachments = contentType.toLowerCase().contains("multipart");

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

    private static String extractName(String xFrom) {
        if (xFrom == null) return "";
        var idx = xFrom.indexOf('<');
        if (idx != -1) {
            return xFrom.substring(0, idx).trim();
        }
        return xFrom.trim();
    }

    private static long parseDate(String dateStr) {
        if (dateStr == null) return System.currentTimeMillis();
        try {
            var instant = Instant.from(DATE_FORMAT.parse(dateStr));
            return instant.toEpochMilli();
        } catch (DateTimeParseException e) {
            return System.currentTimeMillis();
        }
    }
}
