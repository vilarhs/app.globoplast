package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.util.Norm;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class LaunchFilterOptions {
    private LaunchFilterOptions() {}

    static List<String> machines(List<Machine> machines, Collection<String> sectors) {
        Set<String> selected = sectors == null ? Set.of() : new LinkedHashSet<>(sectors);
        if (machines == null) return List.of();
        return machines.stream()
                .filter(machine -> selected.isEmpty()
                        || (machine.sector() != null && selected.stream()
                        .anyMatch(sector -> sector.equalsIgnoreCase(machine.sector()))))
                .map(Machine::name)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    static List<String> clients(Collection<LaunchRecord> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        return rows.stream()
                .map(LaunchRecord::getClientErp)
                .map(Norm::text)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
