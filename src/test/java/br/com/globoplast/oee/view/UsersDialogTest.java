package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.User;
import br.com.globoplast.oee.service.AuthService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("vaadin-ui")
class UsersDialogTest {
    @Test
    @SuppressWarnings("unchecked")
    void protectsCurrentUserAndChecksPasswordBeforeSaving() {
        User current = new User(1, "TESTE", true, "administrador", null, "pt-BR");
        List<String> saves = new ArrayList<>();
        AuthService auth = new AuthService(null, null) {
            @Override public List<User> users() { return List.of(current); }
            @Override public void saveUser(Long id, String username, String profile, String sector, String password) {
                assertEquals(1L, id);
                assertEquals("TESTE", username);
                assertEquals("Administrador", profile);
                saves.add(password);
            }
        };
        List<Dialog> dialogs = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        UI previous = UI.getCurrent();
        UI testUi = new UI();
        UI.setCurrent(testUi);
        try {
            new UsersDialog(auth, current, () -> List.of("Extrusão"), text -> text, title -> {
                Dialog dialog = ViewComponents.dialog(title, "Fechar");
                dialogs.add(dialog);
                return dialog;
            }, field -> {}, messages::add).open();
            Dialog parent = dialogs.getFirst();
            Grid<User> grid = (Grid<User>) parent.getChildren().filter(Grid.class::isInstance).findFirst().orElseThrow();
            ComponentRenderer<Component, User> renderer =
                    (ComponentRenderer<Component, User>) grid.getColumns().getLast().getRenderer();
            HorizontalLayout actions = (HorizontalLayout) renderer.createComponent(current);
            assertFalse(((Button) actions.getComponentAt(1)).isEnabled());
            UI.setCurrent(testUi);
            ((Button) actions.getComponentAt(0)).click();
            Dialog editor = dialogs.getLast();
            Div form = (Div) editor.getChildren().filter(Div.class::isInstance).findFirst().orElseThrow();
            PasswordField password = (PasswordField) form.getComponentAt(3);
            PasswordField confirmation = (PasswordField) form.getComponentAt(4);
            Button save = (Button) editor.getFooter().getElement().getChildren()
                    .flatMap(element -> element.getComponent().stream())
                    .filter(c -> c instanceof Button button && button.getText().equals("Salvar")).findFirst().orElseThrow();
            password.setValue("senha-simulada");
            confirmation.setValue("diferente");
            UI.setCurrent(testUi);
            save.click();
            assertTrue(saves.isEmpty());
            assertEquals(List.of("A confirmação da nova senha não confere."), messages);
            assertTrue(editor.isOpened());
            confirmation.setValue("senha-simulada");
            UI.setCurrent(testUi);
            save.click();
            assertEquals(List.of("senha-simulada"), saves);
            assertFalse(editor.isOpened());
            assertFalse(parent.isOpened());
            assertTrue(dialogs.getLast().isOpened());
            assertEquals("Usuários", dialogs.getLast().getHeaderTitle());
        } finally {
            UI.setCurrent(previous);
        }
    }
}
