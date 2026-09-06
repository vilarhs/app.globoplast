package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.service.CatalogService;

final class CatalogMachineResolver {
    private CatalogMachineResolver() {}

    static Machine find(CatalogService catalog, String name) {
        if (catalog == null || name == null || name.isBlank()) return null;
        Machine direct = catalog.machineMap().get(name);
        if (direct != null) return direct;
        String wanted = LaunchInputNormalizer.machineKey(name);
        for (Machine machine : catalog.machines()) {
            if (LaunchInputNormalizer.machineKey(machine.name()).equals(wanted)) return machine;
        }
        return null;
    }
}
