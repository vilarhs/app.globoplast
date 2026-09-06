package br.com.globoplast.oee.view;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pequeno cache LRU por inserção, limitado para não crescer com os filtros. */
final class RangeCache<T> {
    private final int limit;
    private final Map<String, List<T>> values = new LinkedHashMap<>();

    RangeCache(int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        this.limit = limit;
    }

    List<T> get(String key) {
        return values.get(key);
    }

    void put(String key, List<T> value) {
        values.put(key, value);
        while (values.size() > limit) values.remove(values.keySet().iterator().next());
    }

    void clear() {
        values.clear();
    }
}
