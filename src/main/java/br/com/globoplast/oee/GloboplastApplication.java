package br.com.globoplast.oee;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.component.page.Meta;
import com.vaadin.flow.component.page.TargetElement;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@StyleSheet(Lumo.STYLESHEET)
@StyleSheet("globoplast.css")
@StyleSheet("globoplast-mobile.css")
@CssImport(value = "./styles/gp-menu-bar-button.css", themeFor = "vaadin-menu-bar-button")
@CssImport(value = "./styles/gp-menu-bar-button-mobile.css", themeFor = "vaadin-menu-bar-button")
@CssImport(value = "./styles/gp-menu-bar-item.css", themeFor = "vaadin-menu-bar-item")
@CssImport(value = "./styles/gp-menu-bar-item-mobile.css", themeFor = "vaadin-menu-bar-item")
@Meta(name = "theme-color", content = "#ffffff")
public class GloboplastApplication implements AppShellConfigurator {
    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addInlineWithContents(
                TargetElement.HEAD,
                Inline.Position.PREPEND,
                """
                (() => {
                    try {
                        const stored = localStorage.getItem('globoplast_theme');
                        const mode = ['system', 'light', 'dark'].includes(stored) ? stored : 'system';
                        const resolved = mode === 'system'
                            ? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
                            : mode;
                        const root = document.documentElement;
                        root.setAttribute('theme', resolved);
                        root.setAttribute('data-gp-theme', resolved);
                        root.setAttribute('data-gp-theme-mode', mode);
                        root.style.colorScheme = resolved;
                        root.style.backgroundColor = resolved === 'dark' ? '#0e1117' : '#ffffff';
                    } catch (ignored) {}
                })();
                """,
                Inline.Wrapping.JAVASCRIPT
        );
    }

    public static void main(String[] args) {
        SpringApplication.run(GloboplastApplication.class, args);
    }
}
