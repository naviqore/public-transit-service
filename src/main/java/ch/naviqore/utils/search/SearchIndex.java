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

    // TODO: On the complete stops of the GTFS of Switzerland, this index uses a lot of memory,
    //  therefore we should try to limit search to a minimum search key length and a maximum tree depth
    private final Map<String, List<T>> exactIndex = new HashMap<>();
    private final Trie<T> startsWithIndex = new Trie<>();
    private final Trie<T> endsWithIndex = new Trie<>();
    // TODO: This most of the memory, also limit the length
    private final Map<String, Set<T>> containsIndex = new HashMap<>();

    private static String reverse(String text) {
        return new StringBuilder(text).reverse().toString();
    }

    /**
     * Adds a key-value pair to the index.
     *
     * @param key   the string key to be indexed.
     * @param value the value associated with the key.
     */
    public void add(String key, T value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty.");
        }
        log.debug("Adding search key: {}", key);
        exactIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        startsWithIndex.insert(key, value);
        endsWithIndex.insert(reverse(key), value);
        addToContainsIndex(key, value);
    }

    /**
     * Searches for values matching the query and the search strategy.
     *
     * @param query    the string query to search for.
     * @param strategy the search strategy to use.
     * @return the values associated with the query if found, otherwise an empty list.
     */
    public List<T> search(String query, SearchStrategy strategy) {
        log.debug("Searching for query: '{}' with strategy: {}", query, strategy);

        if (query == null || query.isEmpty()) {
            return List.of();
        }

        return switch (strategy) {
            case EXACT -> exactIndex.getOrDefault(query, List.of());
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
