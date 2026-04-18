package search.core;

public interface HybridSearchEngine extends AutoCloseable {
    EmailSearchResult[] performHybridSearch(
            String userQueryText, String targetEmail, int maxResults);
}
