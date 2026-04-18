package search.core;

public interface HybridSearchEngine {
    EmailSearchResult[] performHybridSearch(String userQueryText, String targetEmail, int maxResults);
}
