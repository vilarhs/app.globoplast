package br.com.globoplast.oee.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

final class LoginPage extends Div {
    LoginPage(Function<String, String> translate, Component logos, Consumer<TextField> uppercase,
              BiPredicate<String, String> authenticate) {
        addClassName("gp-login-page");
        TextField username = new TextField(translate.apply("Usuário"));
        PasswordField password = new PasswordField(translate.apply("Senha"));
        username.setValueChangeMode(ValueChangeMode.EAGER);
        password.setValueChangeMode(ValueChangeMode.EAGER);
        username.setWidthFull();
        password.setWidthFull();
        username.addClassNames("gp-login-field", "gp-login-username");
        password.addClassName("gp-login-field");
        uppercase.accept(username);
        enforceContrast(username);
        enforceContrast(password);

        Button login = new Button(translate.apply("Entrar"));
        login.addThemeVariants(ButtonVariant.PRIMARY);
        login.setWidthFull();
        Span error = new Span();
        error.addClassName("gp-login-error");
        Runnable submit = () -> {
            if (!authenticate.test(username.getValue(), password.getValue())) {
                error.setText(translate.apply("Usuário ou senha inválidos."));
            }
        };
        login.addClickListener(event -> submit.run());
        password.addKeyPressListener(Key.ENTER, event -> submit.run());
        Div card = new Div(logos, username, password, login, error);
        card.addClassName("gp-login-card");
        add(card);
    }

    private static void enforceContrast(Component field) {
        field.addAttachListener(event -> field.getElement().executeJs("""
            (() => {
                const host = this;
                const root = document.documentElement;
                const apply = () => {
                    const dark = root.getAttribute('data-gp-theme') === 'dark' ||
                                 (root.getAttribute('theme') || '').split(/\s+/).includes('dark');
                    const color = dark ? '#ffffff' : '#31333f';
                    const input = host.inputElement || host.shadowRoot?.querySelector('input');
                    if (input) {
                        input.style.setProperty('color', color, 'important');
                        input.style.setProperty('-webkit-text-fill-color', color, 'important');
                        input.style.setProperty('caret-color', color, 'important');
                    }
                    host.style.setProperty('--vaadin-input-field-value-color', color, 'important');
                    host.style.setProperty('--vaadin-input-field-label-color', color, 'important');
                    host.style.setProperty('--lumo-body-text-color', color, 'important');
                };
                requestAnimationFrame(apply);
                setTimeout(apply, 0);
                if (!host.__gpLoginThemeObserver) {
                    host.__gpLoginThemeObserver = new MutationObserver(apply);
                    host.__gpLoginThemeObserver.observe(root, {
                        attributes: true,
                        attributeFilter: ['theme', 'data-gp-theme']
                    });
                }
            })();
        """));
    }
}
