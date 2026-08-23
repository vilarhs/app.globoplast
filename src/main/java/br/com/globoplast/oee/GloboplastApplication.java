package br.com.globoplast.oee;

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
@Meta(name = "theme-color", content = "#ffffff")
public class GloboplastApplication implements AppShellConfigurator {
    public static void main(String[] args) {
        SpringApplication.run(GloboplastApplication.class, args);
    }
}
