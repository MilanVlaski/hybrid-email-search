package search.core;

public record Hit(
    String messageId,
    String fromEmail,
    String fromName,
    String allRecipients,
    String subject,
    String bodySnippet,
    String bodyContent,
    long timestampMs,
    boolean hasAttachments,
    String labels
) {}
