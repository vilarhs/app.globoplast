package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.RefugoRecord;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Pure option generation for scrap filters. */
final class ScrapFilterOptions {
    private ScrapFilterOptions() { }

    static List<String> values(List<RefugoRecord> rows, Function<RefugoRecord, String> getter) {
        if (rows == null || rows.isEmpty()) return List.of();
        return rows.stream().map(getter).filter(Objects::nonNull).filter(v -> !v.isBlank())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
