package ch.naviqore.utils.search;

import lombok.extern.log4j.Log4j2;

/**
 * Builder class for creating a SearchIndex with key-value pairs.
 *
 * @param <T> the type of objects to be indexed.
 */
@Log4j2
public class SearchIndexBuilder<T> {

    private final Trie<SearchIndex.Entry<T>> suffixTrie = new Trie<>();
    int count = 0;

    /**
     * Adds a key-value pair to the builder.
     *
     * @param key   the string key to be indexed.
     * @param value the value associated with the key.
     * @return the builder instance.
     */
    public SearchIndexBuilder<T> add(String key, T value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty.");
        }

        log.debug("Adding search key: {}", key);
        SearchIndex.Entry<T> entry = new SearchIndex.Entry<>(key, value);
        for (int i = key.length() - 1; i >= 0; i--) {
            suffixTrie.insert(key.substring(i), entry);
        }
        count++;

        return this;
    }

    /**
     * Builds the SearchIndex by compressing the underlying Trie.
     *
     * @return the built and compressed SearchIndex.
     */
    public SearchIndex<T> build() {
        int initialSize = suffixTrie.getSize();
        suffixTrie.compress();

        log.info("Building search index for {} entries with compressed suffix trie from {} to {} nodes", count,
                initialSize, suffixTrie.getSize());
        return new SearchIndex<>(suffixTrie);
    }

}
