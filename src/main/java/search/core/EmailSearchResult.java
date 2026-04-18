package search.core;

public record EmailSearchResult(
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
