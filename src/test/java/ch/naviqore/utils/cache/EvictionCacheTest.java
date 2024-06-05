package ch.naviqore.utils.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvictionCacheTest {

    public static final int CACHE_SIZE = 3;

    @Nested
    class LRU {

        private EvictionCache<String, String> lruCache;

        @BeforeEach
        void setUp() {
            lruCache = new EvictionCache<>(CACHE_SIZE, EvictionCache.Strategy.LRU);
        }

        @Test
        void shouldComputeIfAbsent() {
            assertThat(lruCache.isCached("a")).isFalse();
            lruCache.computeIfAbsent("a", () -> "apple");
            assertThat(lruCache.isCached("a")).isTrue();
            assertThat(lruCache.computeIfAbsent("a", () -> "apple")).isEqualTo("apple");
        }

        @Test
        void shouldRemoveLeastRecentlyUsed() {
            lruCache.computeIfAbsent("a", () -> "apple");
            lruCache.computeIfAbsent("b", () -> "banana");
            lruCache.computeIfAbsent("c", () -> "cherry");

            // access "a" again to make it recently used
            lruCache.computeIfAbsent("a", () -> "apple");
            // this should evict "b"
            lruCache.computeIfAbsent("d", () -> "date");

            assertThat(lruCache.isCached("b")).isFalse();
            assertThat(lruCache.isCached("a")).isTrue();
            assertThat(lruCache.isCached("c")).isTrue();
            assertThat(lruCache.isCached("d")).isTrue();
        }

        @Test
        void shouldClear() {
            lruCache.computeIfAbsent("a", () -> "apple");
            lruCache.computeIfAbsent("b", () -> "banana");
            lruCache.computeIfAbsent("c", () -> "cherry");

            lruCache.clear();

            assertThat(lruCache.isCached("a")).isFalse();
            assertThat(lruCache.isCached("b")).isFalse();
            assertThat(lruCache.isCached("c")).isFalse();
        }
    }

    @Nested
    class MRU {

        private EvictionCache<String, String> mruCache;

        @BeforeEach
        void setUp() {
            mruCache = new EvictionCache<>(CACHE_SIZE, EvictionCache.Strategy.MRU);
        }

        @Test
        void shouldComputeIfAbsent() {
            assertThat(mruCache.isCached("a")).isFalse();
            mruCache.computeIfAbsent("a", () -> "apple");
            assertThat(mruCache.isCached("a")).isTrue();
            assertThat(mruCache.computeIfAbsent("a", () -> "apple")).isEqualTo("apple");
        }

        @Test
        void shouldRemoveMostRecentlyUsed() {
            mruCache.computeIfAbsent("a", () -> "apple");
            mruCache.computeIfAbsent("b", () -> "banana");
            mruCache.computeIfAbsent("c", () -> "cherry");

            // this should evict "c" as it is the most recently used
            mruCache.computeIfAbsent("d", () -> "date");

            assertThat(mruCache.isCached("c")).isFalse();
            assertThat(mruCache.isCached("a")).isTrue();
            assertThat(mruCache.isCached("b")).isTrue();
            assertThat(mruCache.isCached("d")).isTrue();
        }

        @Test
        void shouldClear() {
            mruCache.computeIfAbsent("a", () -> "apple");
            mruCache.computeIfAbsent("b", () -> "banana");
            mruCache.computeIfAbsent("c", () -> "cherry");

            mruCache.clear();

            assertThat(mruCache.isCached("a")).isFalse();
            assertThat(mruCache.isCached("b")).isFalse();
            assertThat(mruCache.isCached("c")).isFalse();
        }
    }
}
