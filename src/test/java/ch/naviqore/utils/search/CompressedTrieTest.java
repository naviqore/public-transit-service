package ch.naviqore.utils.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompressedTrieTest {

    private Trie<String> trie;

    @BeforeEach
    void setUp() {
        trie = new CompressedTrie<>();
    }

    @Nested
    class Suffix {

        @BeforeEach
        void setUp() {
            trie.insert("test", "test");
            trie.insert("testing", "testing");
            trie.insert("duplicate", "duplicate1");
            trie.insert("duplicate", "duplicate2");
            trie.insert("hello", "hello");
            trie.insert("ello", "hello");
            trie.insert("llo", "hello");
            trie.insert("lo", "hello");
            trie.insert("o", "hello");
            trie.insert("d", "world");
            trie.insert("ld", "world");
            trie.insert("rld", "world");
            trie.insert("orld", "world");
            trie.insert("world", "world");
            trie.insert("rod", "rod");
        }

        @Test
        void shouldInsert() {
            trie.insert("insert", "insert");
            assertThat(trie.startsWith("insert")).containsExactly("insert");
        }

        @Test
        void shouldGetNodes() {
            List<Trie.Node<String>> nodes = trie.getNodes();
            assertThat(nodes).isNotEmpty();
            assertThat(nodes.size()).isEqualTo(17);
        }

        @Test
        void shouldGetSize() {
            assertThat(trie.size()).isEqualTo(15);
        }

        @Nested
        class FindPrefix {

            @Test
            void shouldFind() {
                assertThat(trie.startsWith("t")).containsExactlyInAnyOrder("test", "testing");
                assertThat(trie.startsWith("test")).containsExactlyInAnyOrder("test", "testing");
                assertThat(trie.startsWith("testi")).containsExactly("testing");
            }

            @Test
            void shouldFindSuffixes() {
                assertThat(trie.startsWith("ll")).containsExactly("hello");
                assertThat(trie.startsWith("rl")).containsExactly("world");
            }

            @Test
            void shouldFindDuplicates() {
                List<String> results = trie.startsWith("dupl");
                assertThat(results).containsExactlyInAnyOrder("duplicate1", "duplicate2");
            }
        }
    }

    @Nested
    class LoremIpsum {

        public static final String[] KEYS = new String[]{"lorem", "ipsum", "dolor", "sit", "amet", "consectetur",
                "adipiscing", "elit", "sed", "do", "eiusmod", "tempor", "incididunt", "ut", "labore", "et", "dolore",
                "magna", "aliqua", "ut", "enim", "ad", "minim", "veniam", "quis", "nostrud", "exercitation", "ullamco",
                "laboris", "nisi", "ut", "aliquip", "ex", "ea", "commodo", "consequat", "duis", "aute", "irure",
                "dolor", "in", "reprehenderit", "in", "voluptate", "velit", "esse", "cillum", "dolore", "eu", "fugiat",
                "nulla", "pariatur", "excepteur", "sint", "occaecat", "cupidatat", "non", "proident", "sunt", "in",
                "culpa", "qui", "officia", "deserunt", "mollit", "anim", "id", "est", "laborum", "3", "23", "123"};

        @BeforeEach
        void setUp() {
            for (String key : KEYS) {
                trie.insert(key, key);
            }
        }

        @Test
        void shouldAddAll() {
            assertThat(trie.size()).isEqualTo(KEYS.length);
        }

        @Test
        void shouldFindExistingKeys() {
            List<String> result = trie.startsWith("lorem");
            assertThat(result).hasSize(1).contains("lorem");
        }

        @Test
        void shouldFindSuffixKeys() {
            List<String> result1 = trie.startsWith("1");
            assertThat(result1).contains("123");

            List<String> result2 = trie.startsWith("2");
            assertThat(result2).contains("23");

            List<String> result3 = trie.startsWith("3");
            assertThat(result3).contains("3");
        }

        @Test
        void shouldFindMultipleKeysWithCommonPrefix() {
            List<String> result = trie.startsWith("do");
            assertThat(result).containsExactlyInAnyOrder("do", "dolor", "dolor", "dolore", "dolore");

            List<String> resultA = trie.startsWith("a");
            assertThat(resultA).containsExactlyInAnyOrder("amet", "adipiscing", "aliqua", "ad", "aute", "aliquip",
                    "anim");

            List<String> resultE = trie.startsWith("e");
            assertThat(resultE).containsExactlyInAnyOrder("ea", "esse", "est", "et", "eu", "ex", "excepteur",
                    "exercitation", "eiusmod", "elit", "enim");

            List<String> resultI = trie.startsWith("i");
            assertThat(resultI).containsExactlyInAnyOrder("ipsum", "irure", "id", "in", "in", "in", "incididunt");

            List<String> resultC = trie.startsWith("c");
            assertThat(resultC).containsExactlyInAnyOrder("consectetur", "commodo", "consequat", "cillum", "culpa",
                    "cupidatat");

            List<String> resultS = trie.startsWith("s");
            assertThat(resultS).containsExactlyInAnyOrder("sit", "sed", "sint", "sunt");
        }

        @Test
        void shouldNotFindNonExistingPrefix() {
            List<String> result = trie.startsWith("xyz");
            assertThat(result).isEmpty();
        }
    }
}
