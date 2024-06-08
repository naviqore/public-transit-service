package ch.naviqore.utils.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class EvictionCacheTest {

    public static final int CACHE_SIZE = 3;
    public static final int CONCURRENT_ACCESS_COUNT = 1000;

    private static void runConcurrentAccess(
            EvictionCache<String, String> cache) throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<?>[] futures = IntStream.range(0, CONCURRENT_ACCESS_COUNT)
                .mapToObj(i -> CompletableFuture.runAsync(
                        () -> cache.computeIfAbsent("key" + i % CACHE_SIZE, () -> "value" + i)))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).get(1, TimeUnit.MINUTES);
    }

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

            // access "a" again to make it recently used, and change value to avocado
            String cachedValue = lruCache.computeIfAbsent("a", () -> "avocado");
            // "apple" should not have been recomputed to "avocado"
            assertThat(cachedValue).isEqualTo("apple");

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

        @Test
        void shouldHandleConcurrentAccess() throws InterruptedException, ExecutionException, TimeoutException {
            runConcurrentAccess(lruCache);

            assertThat(lruCache.getNumberOfEntries()).isEqualTo(CACHE_SIZE);
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
            // query with other value but same key, should not replace the value in cache
            assertThat(mruCache.computeIfAbsent("a", () -> "avocado")).isEqualTo("apple");
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

        @Test
        void shouldHandleConcurrentAccess() throws InterruptedException, ExecutionException, TimeoutException {
            runConcurrentAccess(mruCache);

            assertThat(mruCache.getNumberOfEntries()).isEqualTo(CACHE_SIZE);
        }
    }
}
