package br.com.globoplast.oee.view;

import com.vaadin.flow.component.textfield.TextField;

import java.util.Locale;

/** Shared client/server normalization for free-text fields. */
final class InputControls {
    private InputControls() { }

    static void forceUppercase(TextField field) {
        if (field == null) return;
        field.addClassName("gp-uppercase-input");
        field.addValueChangeListener(e -> {
            String current = e.getValue() == null ? "" : e.getValue();
            String upper = current.toUpperCase(Locale.ROOT);
            if (!upper.equals(current)) field.setValue(upper);
        });
        field.addAttachListener(e -> field.getElement().executeJs("""
            (() => {
                const host = this;
                const install = () => {
                    const input = host.inputElement || host.shadowRoot?.querySelector('input');
                    if (!input) { setTimeout(install, 0); return; }
                    if (input.__gpUppercaseInstalled) return;
                    input.__gpUppercaseInstalled = true;
                    const uppercaseNow = (redispatch) => {
                        const value = input.value || '';
                        const upper = value.toLocaleUpperCase('pt-BR');
                        if (value === upper) return;
                        const start = input.selectionStart;
                        const end = input.selectionEnd;
                        input.value = upper;
                        if (start !== null && end !== null) {
                            try { input.setSelectionRange(start, end); } catch (_) {}
                        }
                        if (redispatch) {
                            input.dispatchEvent(new Event('input', {bubbles:true, composed:true}));
                        }
                    };
                    input.addEventListener('input', () => uppercaseNow(true), true);
                    uppercaseNow(false);
                };
                requestAnimationFrame(install);
            })();
        """));
    }
}
