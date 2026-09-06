package br.com.globoplast.oee.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

final class ScrapAnalysisPage extends Div {
    private final Function<String, String> translate;
    private final BooleanSupplier hasMonthlyComparison;
    private final BooleanSupplier hasYearlyComparison;
    private final Consumer<String> dimensionSelected;
    private final Div titleRow;
    private final Div toolbar;
    private final Div kpis = new Div();
    private final Div chart = new Div();
    private final Div details = new Div();
    private final Div recent = new Div();
    private final Map<Tab, String> dimensions = new LinkedHashMap<>();
    private final Tab yearlyComparisonTab;
    private final Tab monthlyComparisonTab;
    private final Tabs tabs;
    private final Button filter;

    ScrapAnalysisPage(Function<String, String> translate, String initialSearch, String initialDimension,
                      Button filter, BooleanSupplier hasMonthlyComparison,
                      BooleanSupplier hasYearlyComparison, Consumer<String> searchChanged,
                      Runnable tabChanged, Consumer<String> dimensionSelected) {
        this.translate = translate;
        this.filter = filter;
        this.hasMonthlyComparison = hasMonthlyComparison;
        this.hasYearlyComparison = hasYearlyComparison;
        this.dimensionSelected = dimensionSelected;

        H2 title = new H2(t("Análise de Refugo"));
        title.addClassName("gp-section-title");
        titleRow = new Div(title);
        titleRow.addClassNames("gp-title-row", "gp-title-row-static");

        TextField search = new TextField(t("Pesquisar refugo"));
        search.setPlaceholder(t("Ordem, produto ou descrição"));
        search.setClearButtonVisible(true);
        search.setValue(initialSearch);
        configureFilterButton();
        toolbar = new Div(search, filter);
        toolbar.addClassNames("gp-toolbar", "gp-tab-controls", "gp-search-filter-toolbar-v044",
                "gp-refugo-search-toolbar-v071", "gp-refugo-search-toolbar-v072",
                "gp-refugo-search-toolbar-v073", "gp-refugo-search-toolbar-v074");

        kpis.setId("scrap-kpis");
        kpis.addClassNames("gp-kpis", "gp-refugo-kpis-v056");
        chart.setId("scrap-chart");
        chart.addClassName("gp-refugo-analysis");
        details.setId("scrap-details");
        details.addClassName("gp-refugo-details");
        recent.setId("scrap-recent");
        recent.addClassName("gp-refugo-recent");

        yearlyComparisonTab = tab("Anual", "Comparativo Anual");
        monthlyComparisonTab = tab("Mensal", "Comparativo Mensal");
        tabs = new Tabs(yearlyComparisonTab, monthlyComparisonTab,
                tab("Setor", "Setor"), tab("Máquina", "Máquina"), tab("Turno", "Turno"),
                tab("Descrição", "Descrição"), tab("Motivo", "Motivo"));
        tabs.addClassName("gp-inner-tabs");
        installScrollPreservation();
        dimensions.entrySet().stream()
                .filter(entry -> Objects.equals(entry.getValue(), initialDimension))
                .findFirst().ifPresent(entry -> tabs.setSelectedTab(entry.getKey()));

        search.addValueChangeListener(event -> {
            searchChanged.accept(event.getValue());
            refreshSelected();
        });
        tabs.addSelectedChangeListener(event -> {
            tabChanged.run();
            refreshSelected();
            restoreScroll();
        });
        addClassNames("gp-refugo-page", "gp-refugo-page-v065", "gp-refugo-page-v066",
                "gp-refugo-page-v067", "gp-refugo-page-v068", "gp-refugo-page-v069", "gp-refugo-page-v070");
        rebuild(null);
    }

    Button filterButton() { return filter; }

    void setFilterDropdown(Popover dropdown) { rebuild(dropdown); }

    void refreshSelected() {
        monthlyComparisonTab.setVisible(hasMonthlyComparison.getAsBoolean());
        yearlyComparisonTab.setVisible(hasYearlyComparison.getAsBoolean());
        String selected = selectedDimension();
        if (("Comparativo Mensal".equals(selected) && !monthlyComparisonTab.isVisible())
                || ("Comparativo Anual".equals(selected) && !yearlyComparisonTab.isVisible())) {
            dimensions.entrySet().stream().filter(entry -> "Setor".equals(entry.getValue()))
                    .findFirst().ifPresent(entry -> tabs.setSelectedTab(entry.getKey()));
            selected = "Setor";
        }
        dimensionSelected.accept(selected);
    }

    private void rebuild(Popover dropdown) {
        removeAll();
        if (dropdown == null) add(titleRow, toolbar, kpis, tabs, chart, details, recent);
        else add(titleRow, toolbar, dropdown, kpis, tabs, chart, details, recent);
    }

    private Tab tab(String label, String dimension) {
        Tab tab = new Tab(t(label));
        dimensions.put(tab, dimension);
        return tab;
    }

    private String selectedDimension() {
        return dimensions.getOrDefault(tabs.getSelectedTab(), "Setor");
    }

    private void configureFilterButton() {
        filter.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        filter.addClassName("gp-filter-button");
        filter.setAriaLabel(t("Filtros"));
        filter.setTooltipText(t("Filtros")).withPosition(Tooltip.TooltipPosition.TOP);
    }

    private void installScrollPreservation() {
        tabs.addAttachListener(event -> tabs.getElement().executeJs("""
            if(!this.__gpKeepPageV065){
              this.__gpKeepPageV065=true;
              const capture=()=>{
                window.__gpScrapScrollV065=window.scrollY;
                window.__gpScrapTabsTopV065=this.getBoundingClientRect().top;
              };
              this.addEventListener('pointerdown',capture,{capture:true});
              this.addEventListener('selected-changed',capture,{capture:true});
            }
        """));
    }

    private void restoreScroll() {
        tabs.getElement().executeJs("""
            const restore=()=>{
              const y=Number(window.__gpScrapScrollV065);
              if(Number.isFinite(y)) window.scrollTo(window.scrollX,y);
            };
            restore();
            requestAnimationFrame(()=>{restore();requestAnimationFrame(restore);});
            setTimeout(restore,60);
        """);
    }

    private String t(String text) { return translate.apply(text); }
}
