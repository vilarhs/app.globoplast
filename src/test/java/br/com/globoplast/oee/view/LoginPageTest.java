package br.com.globoplast.oee.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginPageTest {
    @Test
    void submitsCredentialsAndShowsInvalidMessage() {
        AtomicReference<String> submitted = new AtomicReference<>();
        LoginPage page = new LoginPage(text -> text, new Div(), field -> { }, (username, password) -> {
            submitted.set(username + ":" + password);
            return false;
        });
        TextField username = (TextField) descendants(page).filter(TextField.class::isInstance)
                .filter(component -> !(component instanceof PasswordField)).findFirst().orElseThrow();
        PasswordField password = (PasswordField) descendants(page).filter(PasswordField.class::isInstance)
                .findFirst().orElseThrow();
        Button login = (Button) descendants(page).filter(Button.class::isInstance).findFirst().orElseThrow();
        username.setValue("ADMIN");
        password.setValue("senha");
        login.click();
        assertEquals("ADMIN:senha", submitted.get());
        assertTrue(descendants(page).filter(Span.class::isInstance).map(Span.class::cast)
                .anyMatch(span -> span.getText().equals("Usuário ou senha inválidos.")));
    }

    private static Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(LoginPageTest::descendants));
    }
}
