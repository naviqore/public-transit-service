package org.naviqore.utils.cache;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ValueObjectCacheTest {

    @Test
    void shouldReturnSameInstanceForSameValue() {
        ValueObjectCache<LocalDate> cache = new ValueObjectCache<>();
        LocalDate date1 = LocalDate.of(2023, 1, 1);
        LocalDate date2 = LocalDate.of(2023, 1, 1);

        // add date1 to the cache
        LocalDate cachedDate1 = cache.getOrAdd(date1);
        assertEquals(date1, cachedDate1);

        // add date2 to the cache and check if it's the same instance as date1
        LocalDate cachedDate2 = cache.getOrAdd(date2);
        assertSame(cachedDate1, cachedDate2);
    }

    @Test
    void shouldClearCacheAndReturnNewInstanceForSameValue() {
        ValueObjectCache<LocalDate> cache = new ValueObjectCache<>();
        LocalDate date1 = LocalDate.of(2023, 1, 1);
        LocalDate date2 = LocalDate.of(2023, 1, 1);

        // add date1 to the cache
        LocalDate cachedDate1 = cache.getOrAdd(date1);

        // clear the cache and add date2 to the cache
        cache.clear();
        LocalDate cachedDate2 = cache.getOrAdd(date2);

        // ensure the cache adds the instance as new
        assertNotSame(cachedDate1, cachedDate2);
        // ensure the date values are the same
        assertEquals(cachedDate1, cachedDate2);
    }
}