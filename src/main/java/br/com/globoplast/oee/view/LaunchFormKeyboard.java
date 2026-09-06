package br.com.globoplast.oee.view;

import com.vaadin.flow.component.html.Div;

final class LaunchFormKeyboard {
    private LaunchFormKeyboard() {}

    static void install(Div root) {
        if (root == null) return;
        root.getElement().executeJs(
                """
                (() => {
                    const root = this;

                    root.querySelectorAll('[data-gp-inputmode]').forEach(host => {
                        const input = host.inputElement || host.shadowRoot?.querySelector('input');
                        if (!input) return;
                        input.setAttribute('inputmode', host.getAttribute('data-gp-inputmode') || 'text');
                        const order = Number(host.getAttribute('data-gp-launch-order'));
                        input.setAttribute('enterkeyhint', order === 15 ? 'done' : 'next');
                    });

                    if (root.__gpLaunchKeyboardHandler) {
                        root.removeEventListener('keydown', root.__gpLaunchKeyboardHandler, true);
                    }
                    root.__gpLaunchKeyboardHandler = (event) => {
                        if (!['Enter','Tab'].includes(event.key) ||
                            event.isComposing || event.ctrlKey || event.altKey || event.metaKey) return;

                        const path = typeof event.composedPath === 'function' ? event.composedPath() : [];
                        const current = path.find(el =>
                            el instanceof Element && el.hasAttribute?.('data-gp-launch-order')
                        );
                        if (!current) return;

                        if (event.key === 'Enter' &&
                            current.classList.contains('gp-launch-machine-field-v055') &&
                            current.opened) return;

                        const direction = event.shiftKey ? -1 : 1;
                        const currentIndex = Number(current.getAttribute('data-gp-launch-order'));
                        const controls = Array.from(root.querySelectorAll('[data-gp-launch-order]'))
                            .sort((a,b) =>
                                Number(a.getAttribute('data-gp-launch-order')) -
                                Number(b.getAttribute('data-gp-launch-order'))
                            );
                        const next = controls.find(el =>
                            Number(el.getAttribute('data-gp-launch-order')) === currentIndex + direction
                        );
                        if (!next) return;

                        event.preventDefault();
                        event.stopPropagation();
                        event.stopImmediatePropagation();
                        const focusTarget = next.classList.contains('gp-period-picker')
                            ? next.querySelector('.gp-period-field')
                            : next;
                        focusTarget?.focus({preventScroll:false});
                    };
                    root.addEventListener('keydown', root.__gpLaunchKeyboardHandler, true);
                })();
                """
        );
    }
}
