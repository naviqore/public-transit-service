package ch.naviqore.utils.search;

import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.*;

/**
 * SearchIndex class for indexing strings and their associated objects.
 * <p>
 * Supports different search strategies on strings, such as STARTS_WITH, ENDS_WITH, CONTAINS, and EXACT.
 *
 * @param <T> the type of objects to be indexed.
 */
@NoArgsConstructor
@Log4j2
public class SearchIndex<T> {

    private final Map<String, T> exactIndex = new HashMap<>();
    private final Trie<T> startsWithIndex = new Trie<>();
    private final Trie<T> endsWithIndex = new Trie<>();
    private final Map<String, Set<T>> containsIndex = new HashMap<>();

    private static String reverse(String text) {
        return new StringBuilder(text).reverse().toString();
    }

    /**
     * Adds a key-value pair to the index.
     *
     * @param key   the string key to be indexed.
     * @param value the value associated with the key.
     * @throws IllegalArgumentException if the key already exists.
     */
    public void add(String key, T value) throws IllegalArgumentException {
        if (exactIndex.containsKey(key)) {
            throw new IllegalArgumentException("Exact search key already exists: " + key);
        }
        log.debug("Adding search key: {}", key);
        exactIndex.put(key, value);
        startsWithIndex.insert(key, value);
        endsWithIndex.insert(reverse(key), value);
        addToContainsIndex(key, value);
    }

    /**
     * Searches for a values matching the query and the search strategy.
     *
     * @param query    the string query to search for.
     * @param strategy the search strategy to use.
     * @return the value associated with the query if found, otherwise null.
     */
    public List<T> search(String query, SearchStrategy strategy) {
        log.debug("Searching for query: '{}' with strategy: {}", query, strategy);

        if (query == null || query.isEmpty()) {
            return List.of();
        }

        return switch (strategy) {
            case EXACT -> exactIndex.containsKey(query) ? List.of(exactIndex.get(query)) : List.of();
            case STARTS_WITH -> startsWithIndex.searchPrefix(query);
            case ENDS_WITH -> endsWithIndex.searchPrefix(reverse(query));
            case CONTAINS -> new ArrayList<>(containsIndex.getOrDefault(query, Set.of()));
        };
    }

    private void addToContainsIndex(String key, T value) {
        for (int i = 0; i <= key.length(); i++) {
            for (int j = i + 1; j <= key.length(); j++) {
                String substring = key.substring(i, j);
                containsIndex.computeIfAbsent(substring, k -> new HashSet<>()).add(value);
            }
        }
    }

    public enum SearchStrategy {
        STARTS_WITH,
        ENDS_WITH,
        CONTAINS,
        EXACT
    }

}
