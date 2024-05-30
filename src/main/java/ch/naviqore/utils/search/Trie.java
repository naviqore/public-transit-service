package ch.naviqore.utils.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A generic Trie (prefix tree) implementation that stores key-value pairs where the key is a string.
 *
 * @param <T> The type of value that the trie will store.
 */
class Trie<T> {

    private final Node<T> root = new Node<>();

    public void insert(String key, T value) {
        Node<T> node = root;
        for (char c : key.toCharArray()) {
            node.children.putIfAbsent(c, new Node<>());
            node = node.children.get(c);
        }
        node.isEndOfWord = true;
        node.value = value;
    }

    public List<T> searchPrefix(String prefix) {
        List<T> results = new ArrayList<>();
        Node<T> node = root;
        for (char c : prefix.toCharArray()) {
            node = node.children.get(c);
            if (node == null) {
                return results;
            }
        }
        collectAllWords(node, results);
        return results;
    }

    private void collectAllWords(Node<T> node, List<T> results) {
        if (node.isEndOfWord) {
            results.add(node.value);
        }
        for (Node<T> child : node.children.values()) {
            collectAllWords(child, results);
        }
    }

    private static class Node<T> {
        Map<Character, Node<T>> children = new HashMap<>();
        T value = null;
        boolean isEndOfWord = false;
    }
}
