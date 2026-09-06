package br.com.globoplast.oee.view;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;

final class FilterActivity {
    private FilterActivity() {}

    static boolean dateChanged(LocalDate start, LocalDate end, LocalDate[] bounds) {
        return bounds == null || bounds.length < 2
                || !Objects.equals(start, bounds[0]) || !Objects.equals(end, bounds[1]);
    }

    @SafeVarargs
    static boolean any(Collection<?>... selections) {
        if (selections == null) return false;
        for (Collection<?> selection : selections) {
            if (selection != null && !selection.isEmpty()) return true;
        }
        return false;
    }
}
