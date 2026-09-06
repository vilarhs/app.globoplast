package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.model.User;
import br.com.globoplast.oee.util.Norm;

import java.util.Locale;

final class ScrapSector {
    private ScrapSector() {}

    static String resolve(Machine machine, User user, String product) {
        if (machine != null) return machine.sector();
        if (user != null && !user.isAdmin()) return user.sector();
        return product == null || product.isBlank() ? "" : Norm.scrapSector(product);
    }

    static String canonical(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
