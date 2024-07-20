package ch.naviqore.utils.cache;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * A generic cache that supports LRU (Least Recently Used) and MRU (Most Recently Used) eviction strategies.
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of mapped values
 */
@Slf4j
public class EvictionCache<K, V> {
    @Getter
    private final int size;
    private final Strategy strategy;
    private final Map<K, V> cache;
    private final LinkedHashMap<K, Long> accessOrder;
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();

    /**
     * Constructs a new EvictionCache with the specified size and eviction strategy.
     *
     * @param size     the maximum number of elements the cache can hold
     * @param strategy the eviction strategy to use (LRU or MRU)
     */
    public EvictionCache(int size, Strategy strategy) {
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than 0.");
        }
        this.size = size;
        this.strategy = strategy;
        this.cache = new HashMap<>();
        this.accessOrder = new LinkedHashMap<>();
    }

    /**
     * If the specified key is not already associated with a value (or is mapped to {@code null}), attempts to compute
     * its value using the given supplier and enters it into this cache.
     *
     * @param key      the key with which the specified value is to be associated
     * @param supplier the supplier function to compute the value
     * @return the current (existing or computed) value associated with the specified key
     */
    public V computeIfAbsent(K key, Supplier<V> supplier) {
        readLock.lock();
        try {
            if (cache.containsKey(key)) {
                return retrieveFromCache(key);
            }
        } finally {
            readLock.unlock();
        }

        writeLock.lock();
        try {
            // double-check to avoid re-computation
            if (cache.containsKey(key)) {
                return retrieveFromCache(key);
            }

            if (cache.size() >= size) {
                evict();
            }

            log.debug("No cache hit, computing new instance for key {}", key);
            V value = supplier.get();
            cache.put(key, value);
            updateAccessOrder(key);

            return value;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Clears the cache, removing all key-value mappings.
     */
    public void clear() {
        writeLock.lock();
        try {
            cache.clear();
            accessOrder.clear();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Checks if the specified key is present in the cache.
     *
     * @param key the key whose presence in this cache is to be tested
     * @return {@code true} if this cache contains a mapping for the specified key
     */
    public boolean isCached(K key) {
        readLock.lock();
        try {
            return cache.containsKey(key);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns the current number of entries in the cache.
     */
    public int getNumberOfEntries() {
        readLock.lock();
        try {
            return cache.size();
        } finally {
            readLock.unlock();
        }
    }

    private V retrieveFromCache(K key) {
        log.debug("Cache hit, retrieving cached instance for key {}", key);
        updateAccessOrder(key);
        return cache.get(key);
    }

    private void updateAccessOrder(K key) {
        accessOrder.put(key, System.nanoTime());
    }

    private void evict() {
        K keyToEvict = strategy == Strategy.LRU ? findLRUKey() : findMRUKey();
        if (keyToEvict != null) {
            log.debug("Removing cached key {}, last access at {}", keyToEvict, accessOrder.get(keyToEvict));
            cache.remove(keyToEvict);
            accessOrder.remove(keyToEvict);
        }
    }

    private K findLRUKey() {
        return accessOrder.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private K findMRUKey() {
        return accessOrder.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    /**
     * Enum representing the eviction strategy.
     */
    public enum Strategy {
        LRU,
        MRU
    }

}
