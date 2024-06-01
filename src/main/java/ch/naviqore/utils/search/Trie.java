package ch.naviqore.utils.search;

import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A generic Trie (prefix tree) implementation that stores key-value pairs where the key is a string.
 *
 * @param <T> The type of value that the trie will store.
 */
@NoArgsConstructor
@Log4j2
class Trie<T> {

    private final Node<T> root = new Node<>();
    private boolean isCompressed = false;

    public void insert(String key, T value) {
        if (isCompressed) {
            throw new IllegalStateException("Cannot insert new keys into a compressed Trie.");
        }

        Node<T> node = root;
        for (char c : key.toCharArray()) {
            node.children.putIfAbsent(c, new Node<>());
            node = node.children.get(c);
        }

        node.isEndOfWord = true;
        node.values.add(value);
    }

    public List<T> searchPrefix(String prefix) {
        List<T> results = new ArrayList<>();
        Node<T> node = root;
        int i = 0;

        while (i < prefix.length()) {
            char c = prefix.charAt(i);
            node = node.children.get(c);

            if (node == null) {
                return results;
            }
            i++;

            if (!node.prefix.isEmpty() && i < prefix.length() && prefix.startsWith(node.prefix, i)) {
                i += node.prefix.length();
            } else if (!node.prefix.isEmpty()) {
                return results;
            }
        }

        collectAllWords(node, results);
        return results;
    }

    /**
     * Compresses the trie by collapsing chains of nodes where each node has only one child. After calling this method,
     * no more words can be added to the Trie, and it will become immutable. This process optimizes the Trie structure
     * by reducing the number of nodes.
     */
    public void compress() {
        compressNode(root);
        isCompressed = true;
    }

    private void compressNode(Node<T> node) {
        for (Map.Entry<Character, Node<T>> entry : node.children.entrySet()) {
            Node<T> child = entry.getValue();
            compressNode(child);
        }

        if (node.children.size() == 1 && !node.isEndOfWord) {
            Map.Entry<Character, Node<T>> entry = node.children.entrySet().iterator().next();
            Character key = entry.getKey();
            Node<T> child = entry.getValue();

            node.children = child.children;
            node.prefix += key + child.prefix;
            node.isEndOfWord = child.isEndOfWord;
            node.values = child.values;
        }
    }

    private void collectAllWords(Node<T> node, List<T> results) {
        if (node.isEndOfWord) {
            results.addAll(node.values);
        }
        for (Node<T> child : node.children.values()) {
            collectAllWords(child, results);
        }
    }

    /**
     * Returns the number of nodes in the trie.
     *
     * @return the size of the trie.
     */
    public int getSize() {
        return countNodes(root);
    }

    private int countNodes(Node<T> node) {
        int count = 1;
        for (Node<T> child : node.children.values()) {
            count += countNodes(child);
        }

        return count;
    }

    private static class Node<T> {
        Map<Character, Node<T>> children = new HashMap<>();
        List<T> values = new ArrayList<>();
        boolean isEndOfWord = false;
        String prefix = "";
    }
}
