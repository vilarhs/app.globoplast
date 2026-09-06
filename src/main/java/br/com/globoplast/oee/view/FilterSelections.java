package br.com.globoplast.oee.view;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

final class FilterSelections {
    private FilterSelections() {}

    static void replace(Set<String> target, Collection<String> values) {
        target.clear();
        if (values != null) {
            values.stream()
                    .filter(Objects::nonNull)
                    .filter(value -> !value.isBlank())
                    .forEach(target::add);
        }
    }

    static Set<String> copy(Collection<String> values) {
        Set<String> copy = new LinkedHashSet<>();
        if (values != null) replace(copy, values);
        return copy;
    }
}
