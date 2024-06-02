package ch.naviqore.utils.search;

import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompressedTrie<T> implements Trie<T> {
    private final Node<T> root;
    private int size;

    public CompressedTrie() {
        this.root = new Node<>(null, "");
    }

    @Override
    public void insert(String key, T value) {
        Node<T> currentNode = root;
        for (int i = 0; i < key.length(); ) {
            String segment = key.substring(i);
            Node<T> child = currentNode.getChildStartingWith(segment);
            if (child == null) {
                Node<T> newNode = new Node<>(value, segment);
                currentNode.addChild(newNode);
                size++;
                return;
            }

            int commonLength = child.getCommonPrefixLength(segment);
            if (commonLength < child.segment.length()) {
                child = child.split(commonLength);
            }

            if (commonLength < segment.length()) {
                currentNode = child;
                i += commonLength;
            } else {
                child.addValue(value);
                return;
            }
        }
    }

    @Override
    public List<T> startsWith(String prefix) {
        Node<T> currentNode = root;
        List<T> results = new ArrayList<>();

        int index = 0;
        while (index < prefix.length()) {
            char currentChar = prefix.charAt(index);
            Node<T> nextNode = currentNode.children.get(currentChar);

            // if no node starts with this character, return empty list
            if (nextNode == null) {
                return results;
            }

            String segment = nextNode.segment;
            int segmentLength = segment.length();
            int prefixRemaining = prefix.length() - index;

            // calculate the maximum length to compare
            int compareLength = Math.min(segmentLength, prefixRemaining);

            // check if the segment matches the prefix part it needs to match
            for (int i = 0; i < compareLength; i++) {
                if (segment.charAt(i) != prefix.charAt(index + i)) {
                    // if there is a mismatch, return empty list
                    return results;
                }
            }

            // if the whole segment matches but is shorter than the remaining prefix,
            // move to the next node
            if (compareLength == segmentLength && segmentLength < prefixRemaining) {
                currentNode = nextNode;
                index += segmentLength;
            } else {
                // if the end of the prefix has been reached, collect all values from here
                nextNode.collectAllValues(results);
                return results;
            }
        }

        // edge case: If the prefix is empty, collect all values starting from the root
        if (prefix.isEmpty()) {
            currentNode.collectAllValues(results);
        }

        return results;
    }

    @Override
    public List<Trie.Node<T>> getNodes() {
        List<Node<T>> nodes = new ArrayList<>();
        root.collectNodes(nodes);
        return new ArrayList<>(nodes);
    }

    @Override
    public int size() {
        return size;
    }

    @ToString
    static class Node<V> implements Trie.Node<V> {
        private Map<Character, Node<V>> children;
        private List<V> values;
        private String segment;

        Node(V value, String segment) {
            this.children = new HashMap<>();
            this.values = new ArrayList<>();
            if (value != null) {
                this.values.add(value);
            }
            this.segment = segment;
        }

        void addChild(Node<V> child) {
            children.put(child.segment.charAt(0), child);
        }

        Node<V> getChildStartingWith(String segment) {
            return children.get(segment.charAt(0));
        }

        void addValue(V value) {
            values.add(value);
        }

        int getCommonPrefixLength(String other) {
            int minLength = Math.min(segment.length(), other.length());
            for (int i = 0; i < minLength; i++) {
                if (segment.charAt(i) != other.charAt(i)) {
                    return i;
                }
            }
            return minLength;
        }

        Node<V> split(int index) {
            String newSegment = segment.substring(index);
            segment = segment.substring(0, index);
            Node<V> newNode = new Node<>(null, newSegment);
            newNode.children = this.children;
            newNode.values = this.values;
            this.children = new HashMap<>();
            this.values = new ArrayList<>();
            this.addChild(newNode);
            return this;
        }

        void collectAllValues(List<V> results) {
            results.addAll(values);
            for (Node<V> child : children.values()) {
                child.collectAllValues(results);
            }
        }

        void collectNodes(List<Node<V>> nodes) {
            nodes.add(this);
            for (Node<V> child : children.values()) {
                child.collectNodes(nodes);
            }
        }

        @Override
        public List<Trie.Node<V>> getChildren() {
            return new ArrayList<>(children.values());
        }

        @Override
        public List<V> getValues() {
            return new ArrayList<>(values);
        }
    }
}
