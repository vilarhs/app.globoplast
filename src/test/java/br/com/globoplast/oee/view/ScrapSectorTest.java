package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScrapSectorTest {
    @Test
    void prefersMachineThenUserThenProductMapping() {
        Machine machine = new Machine(1, "TESTE", 1, "Impressão");
        User operator = new User(1, "OP", false, "padrão", "Extrusão", "pt-BR");
        User admin = new User(2, "ADMIN", true, "administrador", null, "pt-BR");

        assertEquals("Impressão", ScrapSector.resolve(machine, operator, "771502"));
        assertEquals("Extrusão", ScrapSector.resolve(null, operator, "771502"));
        assertEquals("Impressão", ScrapSector.resolve(null, admin, "771502"));
        assertEquals("", ScrapSector.resolve(null, admin, ""));
    }
}
