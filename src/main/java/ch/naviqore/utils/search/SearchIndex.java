package ch.naviqore.utils.search;

import lombok.NoArgsConstructor;
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
@NoArgsConstructor
@Log4j2
public class SearchIndex<T> {

    private final Trie<TrieEntry<T>> suffixTrie = new Trie<>();

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
        TrieEntry<T> entry = new TrieEntry<>(key, value);
        for (int i = key.length() - 1; i >= 0; i--) {
            suffixTrie.insert(key.substring(i), entry);
        }
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

        List<TrieEntry<T>> results = suffixTrie.searchPrefix(query);

        return switch (strategy) {
            case EXACT -> results.stream()
                    .filter(entry -> entry.key().equals(query))
                    .map(TrieEntry::value)
                    .collect(Collectors.toSet());
            case STARTS_WITH -> results.stream()
                    .filter(entry -> entry.key().startsWith(query))
                    .map(TrieEntry::value)
                    .collect(Collectors.toSet());
            case ENDS_WITH -> results.stream()
                    .filter(entry -> entry.key().endsWith(query))
                    .map(TrieEntry::value)
                    .collect(Collectors.toSet());
            case CONTAINS -> results.stream().map(TrieEntry::value).collect(Collectors.toSet());
        };

    }

    public enum SearchStrategy {
        STARTS_WITH,
        ENDS_WITH,
        CONTAINS,
        EXACT
    }

    private record TrieEntry<U>(String key, U value) {
    }
}
