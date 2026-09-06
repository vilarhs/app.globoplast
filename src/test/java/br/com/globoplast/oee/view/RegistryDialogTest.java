package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.model.Sector;
import br.com.globoplast.oee.service.CatalogService;
import br.com.globoplast.oee.service.LaunchService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("vaadin-ui")
class RegistryDialogTest {
    @Test
    @SuppressWarnings("unchecked")
    void keepsCapacityRecalculationAfterSuccessfulSaveOnly() {
        List<String> calls = new ArrayList<>();
        Machine machine = new Machine(1, "TESTE", 1000, "EXTRUSÃO");
        CatalogService catalog = new CatalogService(null, null) {
            @Override public List<Sector> sectorEntries() { return List.of(new Sector(1, "EXTRUSÃO")); }
            @Override public List<String> sectors() { return List.of("EXTRUSÃO"); }
            @Override public List<Machine> machines() { return List.of(machine); }
            @Override public void saveMachine(Long id, String name, int capacity, String sector) {
                assertEquals(1L, id);
                assertEquals("TESTE", name);
                assertEquals("EXTRUSÃO", sector);
                if (capacity == 0) throw new IllegalArgumentException("Capacidade inválida");
                calls.add("save:" + capacity);
            }
        };
        LaunchService launches = new LaunchService(null, null, null, null) {
            @Override public void refreshAllMachineCapacities() { calls.add("recalculate"); }
        };
        List<Dialog> dialogs = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        UI previous = UI.getCurrent();
        UI testUi = new UI();
        UI.setCurrent(testUi);
        try {
            new RegistryDialog(catalog, launches, text -> text, title -> {
                Dialog dialog = ViewComponents.dialog(title, "Fechar");
                dialogs.add(dialog);
                return dialog;
            }, field -> {}, Long::toString, () -> calls.add("invalidate"), messages::add).open();
            Dialog root = dialogs.getFirst();
            Tabs tabs = (Tabs) root.getChildren().filter(Tabs.class::isInstance).findFirst().orElseThrow();
            tabs.setSelectedIndex(1);
            Div body = (Div) root.getChildren().filter(Div.class::isInstance).findFirst().orElseThrow();
            Grid<Machine> grid = (Grid<Machine>) body.getComponentAt(1);
            ComponentRenderer<Component, Machine> renderer =
                    (ComponentRenderer<Component, Machine>) grid.getColumns().getLast().getRenderer();
            HorizontalLayout actions = (HorizontalLayout) renderer.createComponent(machine);
            UI.setCurrent(testUi);
            ((Button) actions.getComponentAt(0)).click();
            Dialog editor = dialogs.getLast();
            Div form = (Div) editor.getChildren().filter(Div.class::isInstance).findFirst().orElseThrow();
            IntegerField capacity = (IntegerField) form.getComponentAt(1);
            assertEquals(1000, capacity.getValue());
            Button save = (Button) editor.getFooter().getElement().getChildren()
                    .flatMap(element -> element.getComponent().stream())
                    .filter(c -> c instanceof Button button && button.getText().equals("Salvar")).findFirst().orElseThrow();
            capacity.setValue(0);
            UI.setCurrent(testUi);
            save.click();
            assertTrue(calls.isEmpty());
            assertEquals(List.of("Capacidade inválida"), messages);
            assertTrue(editor.isOpened());
            capacity.setValue(24000);
            UI.setCurrent(testUi);
            save.click();
            assertEquals(List.of("save:24000", "recalculate", "invalidate"), calls);
            assertFalse(editor.isOpened());
            assertTrue(root.isOpened());
        } finally {
            UI.setCurrent(previous);
        }
    }
}
