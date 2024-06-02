package ch.naviqore.utils.search;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SearchIndex class for indexing strings and their associated objects.
 * <p>
 * Supports different search strategies on strings, such as STARTS_WITH, ENDS_WITH, CONTAINS, and EXACT.
 *
 * @param <T> the type of objects to be indexed.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Log4j2
public class SearchIndex<T> {

    private final Trie<Entry<T>> suffixTrie;

    public static <T> SearchIndexBuilder<T> builder() {
        return new SearchIndexBuilder<>();
    }

    /**
     * Searches for values matching the query and the search strategy.
     *
     * @param query    the string query to search for.
     * @param strategy the search strategy to use.
     * @return the values associated with the query if found, otherwise an empty list.
     */
    public Set<T> search(String query, SearchStrategy strategy) {
        log.debug("Searching for query: '{}' with strategy: {}", query, strategy);

        if (query == null || query.isEmpty()) {
            return Set.of();
        }

        List<Entry<T>> results = suffixTrie.search(query);

        return switch (strategy) {
            case EXACT -> results.stream()
                    .filter(entry -> entry.key().equals(query))
                    .map(Entry::value)
                    .collect(Collectors.toSet());
            case STARTS_WITH -> results.stream()
                    .filter(entry -> entry.key().startsWith(query))
                    .map(Entry::value)
                    .collect(Collectors.toSet());
            case ENDS_WITH -> results.stream()
                    .filter(entry -> entry.key().endsWith(query))
                    .map(Entry::value)
                    .collect(Collectors.toSet());
            case CONTAINS -> results.stream().map(Entry::value).collect(Collectors.toSet());
        };

    }

    public enum SearchStrategy {
        STARTS_WITH,
        ENDS_WITH,
        CONTAINS,
        EXACT
    }

    record Entry<U>(String key, U value) {
    }

}
