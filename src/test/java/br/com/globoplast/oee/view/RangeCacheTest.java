package br.com.globoplast.oee.view;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RangeCacheTest {
    @Test
    void keepsOnlyTheConfiguredNumberOfRangesAndClears() {
        RangeCache<Integer> cache = new RangeCache<>(2);
        cache.put("a", List.of(1));
        cache.put("b", List.of(2));
        cache.put("c", List.of(3));

        assertNull(cache.get("a"));
        assertEquals(List.of(2), cache.get("b"));
        assertEquals(List.of(3), cache.get("c"));

        cache.clear();
        assertNull(cache.get("b"));
        assertNull(cache.get("c"));
    }
}
