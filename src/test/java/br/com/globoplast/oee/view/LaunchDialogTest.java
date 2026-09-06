package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.model.User;
import br.com.globoplast.oee.service.CatalogService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("vaadin-ui")
class LaunchDialogTest {
    @Test
    void viewingPreservesUnknownErpMachineAndDoesNotModifyRecord() {
        UI previous = UI.getCurrent();
        UI ui = new UI();
        UI.setCurrent(ui);
        try {
            CatalogService catalog = new CatalogService(null, null) {
                @Override public List<Machine> machines() { return List.of(); }
            };
            LaunchRecord record = new LaunchRecord();
            record.setErp(true);
            record.setDate(LocalDate.of(2026, 8, 15));
            record.setMachine("MÁQUINA ERP NÃO CADASTRADA");
            record.setCapacity24h(12000);
            User user = new User(1, "TESTE", true, "administrador", null, "pt-BR");
            LaunchCells cells = new LaunchCells(value -> value, Long::toString,
                    Double::toString, Double::toString);
            Dialog dialog = new LaunchDialog(catalog, null, user, "pt-BR", cells,
                    ignored -> fail("Visualizar não deve salvar"),
                    () -> fail("Visualizar não deve excluir"), ignored -> {}).view(record);
            ComboBox<?> machine = (ComboBox<?>) descendants(dialog).filter(ComboBox.class::isInstance)
                    .findFirst().orElseThrow();
            assertEquals(record.getMachine(), machine.getValue());
            assertTrue(machine.isReadOnly());
            assertTrue(descendants(dialog).filter(TextField.class::isInstance)
                    .map(TextField.class::cast).allMatch(TextField::isReadOnly));
            DateRangePicker date = (DateRangePicker) descendants(dialog)
                    .filter(DateRangePicker.class::isInstance).findFirst().orElseThrow();
            assertEquals(record.getDate(), date.getValue());
            assertTrue(date.hasClassName("gp-launch-standard-field-v054"));
            assertEquals(12000, record.getCapacity24h());
            dialog.close();
        } finally { UI.setCurrent(previous); }
    }

    private Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::descendants));
    }
}
