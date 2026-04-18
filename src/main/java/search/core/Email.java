package search.core;

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
    public String bodySnippet() {
        if (bodyContent == null) return "";
        String cleanBody = bodyContent.replaceAll("\\s+", " ").trim();
        return cleanBody.length() > 200 ? cleanBody.substring(0, 200) + "..." : cleanBody;
    }
}
