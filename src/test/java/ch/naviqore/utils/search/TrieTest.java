package ch.naviqore.utils.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrieTest {

    public static final String[] KEYS = new String[]{"lorem", "ipsum", "dolor", "sit", "amet", "consectetur",
            "adipiscing", "elit", "sed", "do", "eiusmod", "tempor", "incididunt", "ut", "labore", "et", "dolore",
            "magna", "aliqua", "ut", "enim", "ad", "minim", "veniam", "quis", "nostrud", "exercitation", "ullamco",
            "laboris", "nisi", "ut", "aliquip", "ex", "ea", "commodo", "consequat", "duis", "aute", "irure", "dolor",
            "in", "reprehenderit", "in", "voluptate", "velit", "esse", "cillum", "dolore", "eu", "fugiat", "nulla",
            "pariatur", "excepteur", "sint", "occaecat", "cupidatat", "non", "proident", "sunt", "in", "culpa", "qui",
            "officia", "deserunt", "mollit", "anim", "id", "est", "laborum", "3", "23", "123"};

    private Trie<String> trie;

    private static void assertSuffixKeys(Trie<String> trie) {
        List<String> result1 = trie.search("1");
        assertThat(result1).contains("123");

        List<String> result2 = trie.search("2");
        assertThat(result2).contains("23");

        List<String> result3 = trie.search("3");
        assertThat(result3).contains("3");
    }

    private static void assertExistingKey(Trie<String> trie) {
        List<String> result = trie.search("lorem");
        assertThat(result).hasSize(1).contains("lorem");
    }

    private static void assertCommonPrefixes(Trie<String> trie) {
        List<String> result = trie.search("do");
        assertThat(result).containsExactlyInAnyOrder("do", "dolor", "dolor", "dolore", "dolore");

        List<String> resultA = trie.search("a");
        assertThat(resultA).containsExactlyInAnyOrder("amet", "adipiscing", "aliqua", "ad", "aute", "aliquip", "anim");

        List<String> resultE = trie.search("e");
        assertThat(resultE).containsExactlyInAnyOrder("ea", "esse", "est", "et", "eu", "ex", "excepteur",
                "exercitation", "eiusmod", "elit", "enim");

        List<String> resultI = trie.search("i");
        assertThat(resultI).containsExactlyInAnyOrder("ipsum", "irure", "id", "in", "in", "in", "incididunt");

        List<String> resultC = trie.search("c");
        assertThat(resultC).containsExactlyInAnyOrder("consectetur", "commodo", "consequat", "cillum", "culpa",
                "cupidatat");

        List<String> resultS = trie.search("s");
        assertThat(resultS).containsExactlyInAnyOrder("sit", "sed", "sint", "sunt");
    }

    @BeforeEach
    void setUp() {
        trie = new Trie<>();
        for (String key : KEYS) {
            trie.insert(key, key);
        }
    }

    @Nested
    class Search {
        @Test
        void shouldFindExistingKeys() {
            assertExistingKey(trie);
        }

        @Test
        void shouldFindSuffixKeys() {
            assertSuffixKeys(trie);
        }

        @Test
        void shouldFindMultipleKeysWithCommonPrefix() {
            assertCommonPrefixes(trie);
        }

        @Test
        void shouldNotFindNonExistingPrefix() {
            List<String> result = trie.search("xyz");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class Compress {
        @Test
        void shouldCompress() {
            int initialSize = trie.getSize();
            trie.compress();
            int compressedSize = trie.getSize();
            assertThat(compressedSize).isLessThan(initialSize);
        }

        @Test
        void shouldNotAllowInsertAfterCompression() {
            trie.compress();
            assertThatThrownBy(() -> trie.insert("newkey", "newkey")).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot insert new keys into a compressed Trie.");
        }

        @Test
        void shouldFindAfterCompression() {
            trie.compress();

            assertExistingKey(trie);
            assertCommonPrefixes(trie);
            assertSuffixKeys(trie);
        }
    }

}
