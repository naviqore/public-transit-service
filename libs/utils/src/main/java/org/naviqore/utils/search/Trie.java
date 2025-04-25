package org.naviqore.utils.search;

import java.util.List;

/**
 * Trie data structure for storing values with associated string keys, which supports inserting values and searching by
 * prefix. Duplicates are allowed, meaning multiple values can be associated with a single key.
 *
 * @param <T> the type of the values that can be inserted into the Trie.
 */
public interface Trie<T> {

    /**
     * Inserts a value into the Trie associated with a specific key. If the key already exists, the value is added to
     * the list of values for that key, allowing for duplicate values under the same key.
     *
     * @param key   the key associated with the value to insert.
     * @param value the value to insert into the trie.
     */
    void insert(String key, T value);

    /**
     * Searches for all values associated with keys that start with the given prefix. If no values are found, returns an
     * empty list.
     *
     * @param prefix the prefix of the key to search for.
     * @return a list of values whose keys start with the given prefix.
     */
    List<T> startsWith(String prefix);

    /**
     * Retrieves all nodes currently in the Trie.
     *
     * @return a list of all nodes in the trie.
     */
    List<Node<T>> getNodes();

    /**
     * Gets the number of unique keys in the Trie.
     *
     * @return the number of unique keys stored in the trie.
     */
    int size();

    /**
     * Node within the Trie structure, containing a list of children and values.
     *
     * @param <V> the type of values stored in the node.
     */
    interface Node<V> {

        /**
         * Retrieves all child nodes of this node.
         *
         * @return a list of all child nodes.
         */
        List<Node<V>> getChildren();

        /**
         * Retrieves all values stored in this node.
         *
         * @return a list of values stored in this node.
         */
        List<V> getValues();

    }
}
