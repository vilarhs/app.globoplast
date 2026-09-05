package br.com.globoplast.oee.view;

import br.com.globoplast.oee.service.LaunchService;
import br.com.globoplast.oee.util.Norm;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;

/** Owns the stock lookup UI while preserving the existing page DOM and shared styles. */
final class OrderStockPage {
    private final LaunchService launches;
    private final Function<String, String> translate;
    private final LongFunction<String> formatNumber;
    private final Span state = new Span();
    private final Grid<LaunchService.OrderProcessProgress> grid =
            new Grid<>(LaunchService.OrderProcessProgress.class, false);
    private final Component[] components;
    private String selectedOrder;

    OrderStockPage(LaunchService launches, String initialOrder, Consumer<String> rememberOrder,
                   Function<String, String> translate, LongFunction<String> formatNumber,
                   Function<String, Component> fullTextCell) {
        this.launches = launches;
        this.translate = translate;
        this.formatNumber = formatNumber;
        this.selectedOrder = initialOrder;
        H2 title = new H2(t("Estoque por OP"));
        title.addClassName("gp-section-title");

        Paragraph explanation = new Paragraph(t("Dados do ERP separados por OP e processo. OPs atuais usam Planejamento/Estoque; OPs encerradas ausentes do planejamento usam os Apontamentos do ERP."));
        explanation.addClassName("gp-muted");

        TextField order = new TextField(t("Nº da OP"));
        order.setPlaceholder(t("Digite o Nº da OP"));
        order.setValue(initialOrder);
        order.setClearButtonVisible(true);
        order.getElement().setAttribute("autocomplete", "off");
        Button search = new Button(t("Buscar"), VaadinIcon.SEARCH.create());
        search.addThemeVariants(ButtonVariant.PRIMARY);
        Runnable apply = () -> {
            selectedOrder = Norm.order(order.getValue());
            rememberOrder.accept(selectedOrder);
            order.setValue(selectedOrder);
            refresh();
        };
        search.addClickListener(e -> apply.run());
        order.addKeyPressListener(Key.ENTER, e -> apply.run());
        order.addValueChangeListener(e -> {
            if (e.getValue() == null || e.getValue().isBlank()) {
                selectedOrder = "";
                rememberOrder.accept(selectedOrder);
                refresh();
            }
        });

        Div searchRow = new Div(order, search);
        searchRow.addClassName("gp-order-production-search-v110");


        state.setId("order-production-state");
        state.addClassName("gp-muted");


        grid.setId("order-production-grid");
        grid.addClassName("gp-order-production-grid-v110");
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(LaunchService.OrderProcessProgress::process).setHeader(t("Processo")).setAutoWidth(true);
        grid.addColumn(r -> t(r.processName())).setHeader(t("Etapa")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(r -> fullTextCell.apply(r.product())))
                .setHeader(t("Código Produto")).setWidth("160px").setFlexGrow(0);
        grid.addColumn(new ComponentRenderer<>(r -> fullTextCell.apply(r.description())))
                .setHeader(t("Descrição")).setWidth("300px").setFlexGrow(1);
        grid.addColumn(r -> formatInt(r.plannedPcs())).setHeader(compactHeader(t("Programado (OP)"))).setWidth("150px").setFlexGrow(0);
        grid.addColumn(r -> formatInt(r.producedPcs())).setHeader(t("Produzido (OP)")).setAutoWidth(true);
        grid.addColumn(r -> formatInt(r.remainingPcs())).setHeader(t("Falta (OP)")).setAutoWidth(true);
        grid.addColumn(r -> productionPeriod(r.firstDate(), r.lastDate())).setHeader(t("Período")).setAutoWidth(true);
        grid.setAllRowsVisible(true);

        components = new Component[]{title, explanation, searchRow, state, grid};
        refresh();
    }


    Component[] components() { return components; }

    private String t(String text) { return translate.apply(text); }
    private String formatInt(long value) { return formatNumber.apply(value); }

    private static Span compactHeader(String text) {
        Span header = new Span(text);
        header.addClassName("gp-grid-compact-header-v210");
        return header;
    }

    void refresh() {
        if (selectedOrder == null || selectedOrder.isBlank()) {
            grid.setItems(List.of());
            state.setText(t("Informe uma OP para consultar os processos 770, 771, 772, 773, 775 e 776."));
            return;
        }
        List<LaunchService.OrderProcessProgress> rows = launches.orderProcessProgress(selectedOrder);
        grid.setItems(rows);
        state.setText(rows.isEmpty()
                    ? t("Nenhum dado de produção encontrado no ERP para esta OP.")
                    : t("OP") + " " + selectedOrder + " · " + rows.size() + " " + t(rows.size() == 1 ? "processo encontrado" : "processos encontrados"));
    }


    private String productionPeriod(LocalDate first, LocalDate last) {
        if (first == null && last == null) return "—";
        if (Objects.equals(first, last)) return Norm.br(first);
        return Norm.br(first) + " – " + Norm.br(last);
    }


}
