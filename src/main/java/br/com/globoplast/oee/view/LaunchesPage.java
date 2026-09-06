package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.util.Norm;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.LongFunction;

final class LaunchesPage {
    private final Component[] components;

    LaunchesPage(String title, String initialSearch, boolean canAdd,
                 Function<String, String> translate, Function<Button, Popover> filterFactory,
                 Consumer<String> searchChanged, Runnable addLaunch, Runnable showMore,
                 Grid<LaunchRecord> grid) {
        Div titleRow = new Div();
        titleRow.addClassNames("gp-title-row", "gp-title-row-static");
        H2 heading = new H2(title);
        heading.addClassName("gp-section-title");
        titleRow.add(heading);

        TextField search = new TextField(translate.apply("Pesquisar lançamento"));
        search.setPlaceholder(translate.apply("Digite o código, cliente ou Nº da OP"));
        search.setClearButtonVisible(true);
        search.setValue(initialSearch);
        search.setValueChangeMode(ValueChangeMode.EAGER);
        search.setValueChangeTimeout(50);
        search.getElement().setAttribute("autocomplete", "off");
        search.getElement().setAttribute("spellcheck", "false");
        search.addValueChangeListener(event -> searchChanged.accept(event.getValue()));

        Button filter = filterButton(translate);
        Popover filterDropdown = filterFactory.apply(filter);
        Div toolbar = new Div(search, filter);
        toolbar.addClassNames("gp-toolbar", "gp-tab-controls", "gp-search-filter-toolbar-v044", "gp-launch-toolbar-v045");
        if (canAdd) {
            Button add = new Button(translate.apply("Novo Lançamento"), VaadinIcon.PLUS.create());
            add.addThemeVariants(ButtonVariant.PRIMARY);
            add.addClassNames("gp-new-button", "gp-launch-new-inline-v045");
            add.addClickListener(event -> addLaunch.run());
            toolbar.add(add);
        }

        grid.setId("launch-grid");
        Button more = new Button(translate.apply("Mostrar mais"));
        more.setId("launch-more");
        more.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        more.addClassName("gp-show-more");
        more.addClickListener(event -> showMore.run());
        components = new Component[]{titleRow, toolbar, filterDropdown, withStickyHeader(grid), more};
    }

    Component[] components() { return components; }

    static Grid<LaunchRecord> grid(Function<String, String> translate,
                                   LongFunction<String> formatInteger, DoubleFunction<String> formatDecimal,
                                   Function<String, Component> fullTextCell,
                                   Function<LaunchRecord, Component> productCell,
                                   Function<LaunchRecord, Component> orderCell,
                                   Function<LaunchRecord, Component> oeeCell,
                                   Function<LaunchRecord, Component> actions) {
        Grid<LaunchRecord> grid = new Grid<>(LaunchRecord.class, false);
        grid.addClassName("gp-launch-grid-v059");
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(record -> Norm.br(record.getDate())).setHeader(translate.apply("Data")).setWidth("108px").setFlexGrow(0);
        grid.addColumn(new ComponentRenderer<>(record -> fullTextCell.apply(record.getMachine())))
                .setHeader(translate.apply("Máquina")).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(new ComponentRenderer<>(productCell::apply)).setHeader(translate.apply("Código Produto")).setWidth("140px").setFlexGrow(0);
        grid.addColumn(new ComponentRenderer<>(orderCell::apply)).setHeader(translate.apply("Nº OP")).setWidth("84px").setFlexGrow(0);
        grid.addColumn(record -> formatInteger.apply(record.getTotalProduced())).setHeader(translate.apply("Total Lançamento")).setAutoWidth(true);
        grid.addColumn(record -> formatDecimal.apply(record.getScrapTotalKg())).setHeader(translate.apply("Refugo (kg)")).setAutoWidth(true);
        grid.addColumn(record -> formatInteger.apply(record.getScrapTotalPcs())).setHeader(translate.apply("Refugo (pçs)")).setAutoWidth(true);
        grid.addColumn(record -> formatDecimal.apply(record.getScrapPct()) + "%").setHeader(translate.apply("Refugo (%)")).setAutoWidth(true);
        grid.addColumn(record -> record.isOrderProgressAvailable() ? formatInteger.apply(record.getOrderPlannedPcs()) : "—")
                .setHeader(compactHeader(translate.apply("Programado (OP)"))).setWidth("150px").setFlexGrow(0);
        grid.addColumn(record -> record.isOrderProgressAvailable() ? formatInteger.apply(record.getOrderLaunchedPcs()) : "—")
                .setHeader(translate.apply("Produzido (OP)")).setAutoWidth(true);
        grid.addColumn(record -> record.isOrderProgressAvailable() ? formatInteger.apply(record.getOrderRemainingPcs()) : "—")
                .setHeader(translate.apply("Falta (OP)")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(oeeCell::apply)).setHeader("OEE").setWidth("120px").setFlexGrow(0);
        grid.addColumn(new ComponentRenderer<>(actions::apply)).setHeader(translate.apply("Ações"))
                .setWidth("116px").setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);
        grid.setAllRowsVisible(true);
        return grid;
    }

    private static Button filterButton(Function<String, String> translate) {
        Button button = new Button();
        Span glyph = new Span();
        glyph.addClassName("gp-search-filter-funnel-v044");
        glyph.getElement().setAttribute("aria-hidden", "true");
        glyph.getElement().setProperty("innerHTML",
                "<svg viewBox=\"0 0 24 24\" width=\"18\" height=\"18\" aria-hidden=\"true\" focusable=\"false\">" +
                "<path d=\"M3.5 5.25h17l-6.7 7.55v5.15l-3.6 1.8V12.8L3.5 5.25Z\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linejoin=\"round\"/>" +
                "</svg>");
        button.setIcon(glyph);
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.addClassNames("gp-search-filter-button-v044", "gp-filter-button");
        button.setAriaLabel(translate.apply("Filtros"));
        button.setTooltipText(translate.apply("Filtros")).withPosition(Tooltip.TooltipPosition.TOP);
        return button;
    }

    private static Span compactHeader(String text) {
        Span header = new Span(text);
        header.addClassName("gp-grid-compact-header-v210");
        return header;
    }

    private static Div withStickyHeader(Grid<LaunchRecord> grid) {
        Div stickyHeader = new Div();
        stickyHeader.addClassName("gp-launch-sticky-header-v115");
        stickyHeader.getElement().setAttribute("aria-hidden", "true");
        Div wrapper = new Div(stickyHeader, grid);
        wrapper.addClassName("gp-launch-grid-wrapper-v115");
        grid.addAttachListener(event -> grid.getElement().executeJs(STICKY_HEADER_SCRIPT));
        return wrapper;
    }

    private static final String STICKY_HEADER_SCRIPT = """
            const grid = this;
            const sticky = grid.parentElement?.querySelector('.gp-launch-sticky-header-v115');
            if (!sticky) return;
            let attempts = 0;
            const install = () => {
              const root = grid.shadowRoot;
              const table = root?.querySelector('#table');
              const header = root?.querySelector('#header');
              const row = header?.querySelector('tr:last-child');
              if (!table || !header || !row || row.children.length === 0) {
                if (grid.isConnected && attempts++ < 120) requestAnimationFrame(install);
                return;
              }
              grid.__gpLaunchStickyCleanupV115?.();
              const viewport = document.createElement('div');
              viewport.className = 'gp-launch-sticky-viewport-v115';
              const track = document.createElement('div');
              track.className = 'gp-launch-sticky-track-v115';
              viewport.append(track);
              sticky.replaceChildren(viewport);
              const headerText = (cell) => {
                const assigned = [...cell.querySelectorAll('slot')].flatMap(slot => slot.assignedNodes({flatten: true}));
                return assigned.map(node => node.textContent || '').join(' ').replace(/\\s+/g, ' ').trim();
              };
              const sourceContent = (cell) => [...cell.querySelectorAll('slot')]
                .flatMap(slot => slot.assignedElements({flatten: true}))[0] || cell;
              let frame = 0;
              const sync = () => {
                frame = 0;
                if (!grid.isConnected) return;
                const cells = [...row.children].filter(cell => !cell.hidden);
                if (track.children.length !== cells.length) {
                  track.replaceChildren(...cells.map(() => {
                    const mirror = document.createElement('div');
                    mirror.className = 'gp-launch-sticky-cell-v115';
                    const label = document.createElement('span');
                    label.className = 'gp-launch-sticky-label-v115';
                    mirror.append(label);
                    return mirror;
                  }));
                }
                let trackWidth = 0;
                cells.forEach((cell, index) => {
                  const mirror = track.children[index];
                  const label = mirror.firstElementChild;
                  const width = cell.getBoundingClientRect().width;
                  const cellStyle = getComputedStyle(cell);
                  const contentStyle = getComputedStyle(sourceContent(cell));
                  trackWidth += width;
                  mirror.style.flex = `0 0 ${width}px`;
                  mirror.style.width = `${width}px`;
                  mirror.style.backgroundColor = cellStyle.backgroundColor;
                  label.textContent = headerText(cell);
                  label.style.padding = contentStyle.padding;
                  label.style.fontFamily = contentStyle.fontFamily;
                  label.style.fontSize = contentStyle.fontSize;
                  label.style.fontWeight = contentStyle.fontWeight;
                  label.style.lineHeight = contentStyle.lineHeight;
                  label.style.color = contentStyle.color;
                  label.style.textAlign = contentStyle.textAlign;
                });
                const height = Math.ceil(header.getBoundingClientRect().height);
                const gridRect = grid.getBoundingClientRect();
                const originalHeaderTop = header.getBoundingClientRect().top;
                const fixed = originalHeaderTop <= 0 && gridRect.bottom > height;
                const atBottom = originalHeaderTop <= 0 && gridRect.bottom <= height;
                sticky.style.setProperty('--gp-launch-sticky-height-v115', `${height}px`);
                track.style.width = `${trackWidth}px`;
                track.style.transform = `translate3d(${-table.scrollLeft}px,0,0)`;
                sticky.classList.add('gp-ready-v115');
                sticky.classList.toggle('gp-fixed-v115', fixed);
                sticky.classList.toggle('gp-bottom-v115', atBottom);
                if (fixed) {
                  sticky.style.left = `${gridRect.left}px`;
                  sticky.style.width = `${gridRect.width}px`;
                } else {
                  sticky.style.left = '0';
                  sticky.style.width = '100%';
                }
              };
              const schedule = () => { if (!frame) frame = requestAnimationFrame(sync); };
              const resizeObserver = new ResizeObserver(schedule);
              resizeObserver.observe(grid);
              resizeObserver.observe(header);
              resizeObserver.observe(row);
              const mutationObserver = new MutationObserver(schedule);
              mutationObserver.observe(row, {childList: true, subtree: true, characterData: true, attributes: true});
              table.addEventListener('scroll', schedule, {passive: true});
              window.addEventListener('scroll', schedule, {passive: true});
              const connectionObserver = new MutationObserver(() => {
                if (!grid.isConnected) grid.__gpLaunchStickyCleanupV115?.();
              });
              connectionObserver.observe(document.body, {childList: true, subtree: true});
              grid.__gpLaunchStickyCleanupV115 = () => {
                if (frame) cancelAnimationFrame(frame);
                resizeObserver.disconnect();
                mutationObserver.disconnect();
                connectionObserver.disconnect();
                table.removeEventListener('scroll', schedule);
                window.removeEventListener('scroll', schedule);
              };
              schedule();
            };
            requestAnimationFrame(install);
            """;
}
