package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.model.User;
import br.com.globoplast.oee.service.LaunchService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LaunchTrashDialogTest {
    @Test
    @SuppressWarnings("unchecked")
    void preservesUserTypeRestoreAndDeleteConfirmation() {
        User user = new User(1, "teste", true, "administrador", "", "pt-BR");
        LaunchRecord record = new LaunchRecord();
        record.setDate(LocalDate.of(2026, 8, 1));
        LaunchService.TrashItem item = new LaunchService.TrashItem(7, "MANUAL", record, "teste", "", "");
        List<String> calls = new ArrayList<>();
        LaunchService service = new LaunchService(null, null, null, null) {
            @Override public List<TrashItem> trash(User actor, String type) {
                assertSame(user, actor);
                assertEquals("MANUAL", type);
                return List.of(item);
            }
            @Override public void restoreTrash(long id, User actor) {
                assertEquals(7, id);
                assertSame(user, actor);
                calls.add("restore");
            }
            @Override public void deleteTrash(long id, User actor) {
                assertEquals(7, id);
                assertSame(user, actor);
                calls.add("delete");
            }
        };
        List<Dialog> dialogs = new ArrayList<>();
        AtomicInteger refreshed = new AtomicInteger();
        UI previous = UI.getCurrent();
        UI.setCurrent(new UI());
        try {
            new LaunchTrashDialog(service, user, "MANUAL", text -> text, title -> {
                Dialog dialog = new Dialog();
                dialog.setHeaderTitle(title);
                dialogs.add(dialog);
                return dialog;
            }, r -> new Span(), r -> new Span(), text -> text,
                    refreshed::incrementAndGet, text -> {}).open();
            Div body = (Div) dialogs.getFirst().getChildren().filter(Div.class::isInstance).findFirst().orElseThrow();
            Grid<LaunchService.TrashItem> grid = (Grid<LaunchService.TrashItem>) body.getComponentAt(1);
            assertEquals(1, grid.getListDataView().getItemCount());
            ComponentRenderer<Component, LaunchService.TrashItem> renderer =
                    (ComponentRenderer<Component, LaunchService.TrashItem>) grid.getColumns().getLast().getRenderer();
            HorizontalLayout actions = (HorizontalLayout) renderer.createComponent(item);
            ((Button) actions.getComponentAt(0)).click();
            assertEquals(List.of("restore"), calls);
            assertEquals(1, refreshed.get());
            ((Button) actions.getComponentAt(1)).click();
            assertEquals(List.of("restore"), calls);
            Dialog confirmation = dialogs.getLast();
            Button delete = (Button) confirmation.getFooter().getElement().getChildren().flatMap(element -> element.getComponent().stream())
                    .filter(c -> c instanceof Button b && b.getText().equals("Apagar definitivamente")).findFirst().orElseThrow();
            delete.click();
            assertEquals(List.of("restore", "delete"), calls);
            assertFalse(confirmation.isOpened());
        } finally {
            UI.setCurrent(previous);
        }
    }
}
