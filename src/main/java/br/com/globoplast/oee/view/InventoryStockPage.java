package br.com.globoplast.oee.view;

import br.com.globoplast.oee.service.StockService;
import br.com.globoplast.oee.service.StockService.StockItem;
import br.com.globoplast.oee.util.Norm;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;

/** Consulta o estoque físico atual sincronizado de ENDERECO_EST. */
final class InventoryStockPage {
    private final StockService stock;
    private final Function<String,String> translate;
    private final LongFunction<String> formatInteger;
    private final TextField search;
    private final Span state=new Span();
    private final Grid<StockItem> grid=new Grid<>(StockItem.class,false);
    private final Component[] components;

    InventoryStockPage(StockService stock,String initialSearch,Consumer<String> rememberSearch,
                       Function<String,String> translate,LongFunction<String> formatInteger,
                       Function<String,Component> fullTextCell){
        this.stock=stock;this.translate=translate;this.formatInteger=formatInteger;
        H2 title=new H2(t("Consultar Estoque"));title.addClassName("gp-section-title");
        Paragraph explanation=new Paragraph(t("Itens atualmente armazenados no estoque físico do ERP."));
        explanation.addClassName("gp-muted");
        search=new TextField(t("Pesquisar item"));
        search.setPlaceholder(t("Digite OP, código, descrição, lote ou localização"));
        search.setValue(initialSearch==null?"":initialSearch);search.setClearButtonVisible(true);
        search.getElement().setAttribute("autocomplete","off");
        Button button=new Button(t("Consultar"),VaadinIcon.SEARCH.create());button.addThemeVariants(ButtonVariant.PRIMARY);
        Runnable apply=()->{rememberSearch.accept(search.getValue());refresh();};
        button.addClickListener(event->apply.run());search.addKeyPressListener(Key.ENTER,event->apply.run());
        search.addValueChangeListener(event->{if(event.getValue()==null||event.getValue().isBlank()){rememberSearch.accept("");refresh();}});
        Div controls=new Div(search,button);controls.addClassName("gp-order-production-search-v110");
        state.addClassName("gp-muted");
        grid.addClassName("gp-order-production-grid-v110");grid.addThemeVariants(GridVariant.LUMO_NO_BORDER,GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(StockItem::order).setHeader(t("Nº OP")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(row->fullTextCell.apply(row.product())))
                .setHeader(t("Código Produto")).setWidth("170px").setFlexGrow(0);
        grid.addColumn(new ComponentRenderer<>(row->fullTextCell.apply(row.description())))
                .setHeader(t("Descrição")).setWidth("300px").setFlexGrow(1);
        grid.addColumn(StockItem::lot).setHeader(t("Lote")).setAutoWidth(true);
        grid.addColumn(StockItem::location).setHeader(t("Localização")).setAutoWidth(true);
        grid.addColumn(row->formatInteger.apply(row.boxes())).setHeader(t("Caixas")).setAutoWidth(true);
        grid.addColumn(StockItem::content).setHeader(t("Conteúdo")).setAutoWidth(true);
        grid.addColumn(row->formatInteger.apply(row.quantityPcs())).setHeader(t("Quantidade (pçs)")).setAutoWidth(true);
        grid.addColumn(row->row.productionDate()==null?"":Norm.br(row.productionDate())).setHeader(t("Data Produção")).setAutoWidth(true);
        grid.setAllRowsVisible(true);
        components=new Component[]{title,explanation,controls,state,grid};
        refresh();
    }

    Component[] components(){return components;}
    private String t(String text){return translate.apply(text);}
    private void refresh(){
        List<StockItem> rows=stock.search(search.getValue());grid.setItems(rows);
        state.setText(search.getValue().isBlank()?t("Informe uma OP, produto, lote ou localização."):
                rows.isEmpty()?t("Nenhum item encontrado no estoque físico."):t("Posições encontradas")+": "+rows.size());
    }
}
