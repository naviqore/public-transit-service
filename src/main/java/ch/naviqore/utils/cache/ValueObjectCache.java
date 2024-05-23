package ch.naviqore.utils.cache;

import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * A generic cache for immutable value objects.
 * <p>
 * The cache stores value objects of type T, ensuring that only one instance of each unique value is retained in
 * memory.
 *
 * @param <T> the type of the value objects to be cached
 */
@NoArgsConstructor
public class ValueObjectCache<T> {

    private final Map<T, T> cache = new HashMap<>();

    /**
     * Retrieves the value from the cache or adds it if it does not exist.
     *
     * @param value the value to be cached
     * @return the cached value
     */
    public T getOrAdd(T value) {
        return cache.computeIfAbsent(value, k -> value);
    }

    /**
     * Clear the cache.
     */
    public void clear() {
        cache.clear();
    }

}
