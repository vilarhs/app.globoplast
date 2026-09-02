package br.com.globoplast.oee;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Meta;
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
    public static void main(String[] args) {
        SpringApplication.run(GloboplastApplication.class, args);
    }
}
