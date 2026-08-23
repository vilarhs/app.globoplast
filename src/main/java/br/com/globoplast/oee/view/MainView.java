package br.com.globoplast.oee.view;

import br.com.globoplast.oee.config.AppConfig;
import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.model.RefugoRecord;
import br.com.globoplast.oee.model.Sector;
import br.com.globoplast.oee.model.User;
import br.com.globoplast.oee.service.AuthService;
import br.com.globoplast.oee.service.CatalogService;
import br.com.globoplast.oee.service.I18n;
import br.com.globoplast.oee.service.LaunchService;
import br.com.globoplast.oee.service.RefugoService;
import br.com.globoplast.oee.service.SyncService;
import br.com.globoplast.oee.util.Norm;
import br.com.globoplast.oee.util.DisplayFormat;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Route("")
@PageTitle("GLOBOPLAST APP")
public class MainView extends VerticalLayout {
    private static final String TAB_AUTH_STORAGE_KEY = "globoplast_tab_auth_v078";

    private final AuthService auth;
    private final CatalogService catalog;
    private final LaunchService launches;
    private final RefugoService scraps;
    private final SyncService sync;

    private User user;
    private String language = "pt-BR";
    private final Div shell = new Div();
    private final Div content = new Div();
    private Tabs mainTabs;
    private final Map<Tab, String> tabKeys = new LinkedHashMap<>();
    private String renderedTabKey = "";

    private LocalDate launchStart = Norm.productiveToday();
    private LocalDate launchEnd = Norm.productiveToday();
    private final Set<String> launchSectors = new LinkedHashSet<>();
    private final Set<String> launchMachines = new LinkedHashSet<>();
    private final Set<String> launchClients = new LinkedHashSet<>();
    private String launchSearch = "";
    private String productionOrderSearch = "";
    private int launchLimit = AppConfig.PAGE_SIZE;

    private LocalDate scrapStart = Norm.productiveToday();
    private LocalDate scrapEnd = Norm.productiveToday();
    private final Set<String> scrapSectors = new LinkedHashSet<>();
    private final Set<String> scrapOrders = new LinkedHashSet<>();
    private final Set<String> scrapMachines = new LinkedHashSet<>();
    private final Set<String> scrapProducts = new LinkedHashSet<>();
    private final Set<String> scrapDescriptions = new LinkedHashSet<>();
    private final Set<String> scrapClients = new LinkedHashSet<>();
    private final Set<String> scrapShifts = new LinkedHashSet<>();
    private final Set<String> scrapOperators = new LinkedHashSet<>();
    private final Set<String> scrapMotives = new LinkedHashSet<>();
    private final Set<String> scrapExcludedIds = new LinkedHashSet<>();
    private final Map<String, Integer> scrapPages = new LinkedHashMap<>();
    private String scrapSelectedDimension = "";
    private String scrapSelectedKey = "";
    private boolean scrapShowLaunches = false;
    private String scrapActiveDimension = "Setor";
    private String scrapSearch = "";
    private int monthLimit = AppConfig.PAGE_SIZE;
    private LocalDate summaryDayDate = Norm.productiveToday();
    private final Set<String> summaryDaySectors = new LinkedHashSet<>();
    private final Set<String> summaryDayMachines = new LinkedHashSet<>();
    private final Set<String> summaryDayShifts = new LinkedHashSet<>();
    private YearMonth summaryMonth = YearMonth.now(AppConfig.ZONE);
    private String summaryMonthSector = null;
    private String lastSyncSignature = "";
    private Runnable activeDayRefresh = null;
    private Runnable activeMonthRefresh = null;

    // Cache por sessão para várias faixas: trocar de aba e voltar não relê
    // nem recalcula o mesmo período do SQLite. O limite evita crescimento livre.
    private static final int RANGE_CACHE_LIMIT = 8;
    private long dataRevision = 0;
    private final Map<String, List<LaunchRecord>> launchRangeCache = new LinkedHashMap<>();
    private final Map<String, List<RefugoRecord>> scrapRangeCache = new LinkedHashMap<>();
    private long launchBoundsRevision = -1;
    private LocalDate[] launchBoundsCache;
    private long scrapBoundsRevision = -1;
    private LocalDate[] scrapBoundsCache;
    private boolean syncRefreshRunning = false;
    private MenuItem menuSyncItem = null;

    public MainView(AuthService auth, CatalogService catalog, LaunchService launches, RefugoService scraps, SyncService sync) {
        this.auth = auth;
        this.catalog = catalog;
        this.launches = launches;
        this.scraps = scraps;
        this.sync = sync;
        setWidthFull();
        getStyle().set("min-height", "100vh");
        getStyle().set("height", "auto");
        setPadding(false);
        setSpacing(false);
        addClassName("gp-root");
        shell.addClassName("gp-shell");
        content.addClassName("gp-content");
        add(shell);
        initializeTabAuthentication();
    }

    /**
     * O login pertence à aba, não ao cookie persistente isoladamente.
     * sessionStorage sobrevive a F5/reload, mas é descartado pelo navegador
     * quando a aba é fechada. Assim, uma nova aba nunca restaura silenciosamente
     * uma autenticação encerrada, enquanto uma atualização mantém o acesso.
     */
    private void initializeTabAuthentication() {
        User candidate = auth.current();
        UI.getCurrent().getPage()
                .executeJs("return sessionStorage.getItem($0) || '';", TAB_AUTH_STORAGE_KEY)
                .then(String.class, tabUserId -> {
                    boolean sameAuthenticatedTab = candidate != null
                            && String.valueOf(candidate.id()).equals(tabUserId);
                    if (sameAuthenticatedTab) {
                        user = candidate;
                        language = user.language();
                        buildApp();
                        return;
                    }

                    // Pode existir um cookie antigo de outra aba. Sem a marca
                    // desta aba ele não concede acesso e é invalidado.
                    if (candidate != null) auth.logout();
                    user = null;
                    language = AppConfig.DEFAULT_LANGUAGE;
                    clearTabAuthentication();
                    buildLogin();
                });
    }

    private void markTabAuthenticated(User authenticated) {
        UI.getCurrent().getPage().executeJs(
                "sessionStorage.setItem($0,$1);",
                TAB_AUTH_STORAGE_KEY,
                String.valueOf(authenticated.id()));
    }

    private void clearTabAuthentication() {
        UI.getCurrent().getPage().executeJs(
                "sessionStorage.removeItem($0);",
                TAB_AUTH_STORAGE_KEY);
    }

    private String t(String text) {
        return I18n.tr(language, text);
    }

    private void buildLogin() {
        applyThemeMode();
        ensureFavicon();
        UI.getCurrent().setLocale(locale());
        shell.removeAll();
        Div page = new Div();
        page.addClassName("gp-login-page");
        Div logos = logoPair("gp-login-logo");
        TextField username = new TextField(t("Usuário"));
        PasswordField password = new PasswordField(t("Senha"));
        username.setWidthFull();
        password.setWidthFull();
        username.addClassNames("gp-login-field", "gp-login-username");
        password.addClassName("gp-login-field");
        forceUppercase(username);
        enforceLoginInputContrast(username);
        enforceLoginInputContrast(password);
        Button login = new Button(t("Entrar"));
        login.addThemeVariants(ButtonVariant.PRIMARY);
        login.setWidthFull();
        Span error = new Span();
        error.addClassName("gp-login-error");
        Runnable act = () -> {
            User authenticated = auth.authenticate(username.getValue(), password.getValue());
            if (authenticated == null) {
                error.setText(t("Usuário ou senha inválidos."));
                return;
            }
            user = authenticated;
            language = user.language();
            markTabAuthenticated(authenticated);
            buildApp();
        };
        login.addClickListener(e -> act.run());
        password.addKeyPressListener(Key.ENTER, e -> act.run());
        Div card = new Div(logos, username, password, login, error);
        card.addClassName("gp-login-card");
        page.add(card);
        shell.add(page);
    }

    private void buildApp() {
        shell.removeAll();
        renderedTabKey = "";
        applyThemeMode();
        ensureFavicon();
        UI.getCurrent().setLocale(locale());
        shell.add(header(), navigation(), content, footer());
        String selected = null;
        try {
            Map<String,List<String>> params = UI.getCurrent().getInternals().getActiveViewLocation().getQueryParameters().getParameters();
            List<String> values = params.get("aba");
            if(values!=null&&!values.isEmpty()) selected=values.get(values.size()-1);
        } catch(Exception ignored) { }
        if(selected==null||selected.isBlank()) selected=(String)VaadinSession.getCurrent().getAttribute("gp_tab");
        if(selected==null||selected.isBlank()) selected=user.canSeeSummaries()?"dia":"lancamentos";
        selectTab(selected);
        lastSyncSignature = syncSignature();
        UI.getCurrent().setPollInterval(30000);
        UI.getCurrent().addPollListener(e -> {
            if (syncRefreshRunning) return;
            syncRefreshRunning = true;
            try {
                String selectedTab = String.valueOf(VaadinSession.getCurrent().getAttribute("gp_tab"));
                refreshMenuSyncStatus();
                String currentSignature = syncSignature();
                if (!currentSignature.isBlank() && !Objects.equals(currentSignature, lastSyncSignature)) {
                    lastSyncSignature = currentSignature;
                    invalidateDataCaches();

                    // Evita reconstruir a página inteira a cada sincronização.
                    // Lançamentos e Refugo atualizam somente os componentes de dados.
                    if ("lancamentos".equals(selectedTab)) {
                        refreshLaunchGrid();
                        refreshMenuSyncStatus();
                    } else if ("estoque".equals(selectedTab) || "producao".equals(selectedTab)) {
                        refreshOrderProduction();
                    } else if ("refugo".equals(selectedTab)) {
                        refreshScrap(scrapActiveDimension);
                    } else if ("dia".equals(selectedTab)) {
                        if (activeDayRefresh != null) activeDayRefresh.run();
                    } else if ("mes".equals(selectedTab)) {
                        if (activeMonthRefresh != null) activeMonthRefresh.run();
                    }
                }
            } finally {
                syncRefreshRunning = false;
            }
        });
    }

    private Div header() {
        H1 title = new H1(t("Gestão de Produção - OEE"));
        title.addClassName("gp-main-title");
        Div header = new Div(title, logoPair("gp-logo"));
        header.addClassName("gp-main-header");
        return header;
    }

    private Div logoPair(String cssClass) {
        Image light = new Image("/images/globoplast-logo.png", "Globoplast");
        light.addClassNames(cssClass, "gp-logo-light");
        Image dark = new Image("/images/globoplast-logo-white.png", "Globoplast");
        dark.addClassNames(cssClass, "gp-logo-dark");
        Div pair = new Div(light, dark);
        pair.addClassName("gp-logo-pair");
        return pair;
    }

    private Div navigation() {
        tabKeys.clear();
        List<Tab> tabs = new ArrayList<>();
        String productionDefault = user.canSeeSummaries() ? "dia" : "lancamentos";
        Tab productionTab = tab("🏭 " + t("Produção") + " ▾", productionDefault);
        installProductionMenu(productionTab);
        tabs.add(productionTab);
        tabs.add(tab("📦 " + t("Estoque"), "estoque"));
        mainTabs = new Tabs(tabs.toArray(Tab[]::new));
        mainTabs.addClassName("gp-main-tabs");
        mainTabs.setWidthFull();
        mainTabs.addSelectedChangeListener(e -> {
            String key = tabKeys.get(e.getSelectedTab());
            if (key != null) activateTab(key);
        });
        mainTabs.addAttachListener(e -> mainTabs.getElement().executeJs("""
            if(this.__gpInstantTabsV061)return;
            this.__gpInstantTabsV061=true;
            this.addEventListener('pointerdown',event=>{
              const tab=event.composedPath().find(node=>node?.tagName==='VAADIN-TAB');
              if(!tab)return;
              const tabs=Array.from(this.querySelectorAll('vaadin-tab'));
              const index=tabs.indexOf(tab);
              if(index>=0 && this.selected!==index)this.selected=index;
            },{passive:true});
        """));
        Div nav = new Div(mainTabs, menu());
        nav.addClassName("gp-navigation");
        return nav;
    }

    private Tab tab(String label, String key) {
        Tab tab = new Tab(label);
        tabKeys.put(tab, key);
        return tab;
    }

    private void installProductionMenu(Tab productionTab) {
        ContextMenu dropdown = new ContextMenu();
        dropdown.setTarget(productionTab);
        dropdown.setOpenOnClick(true);
        dropdown.addItem(t("Lançamentos"), e -> openProductionSubPage("lancamentos"));
        if (user.canSeeSummaries()) {
            dropdown.addItem(t("Resumo do Dia"), e -> openProductionSubPage("dia"));
            dropdown.addItem(t("Resumo do Mês"), e -> openProductionSubPage("mes"));
        }
        dropdown.addItem(t("Refugo"), e -> openProductionSubPage("refugo"));
        if (user != null && user.canModifyLaunches()) {
            dropdown.addItem(t("Lixeira"), e -> showLaunchTrash());
        }
        installContextMenuHoverOnly(productionTab);
    }

    private void openProductionSubPage(String key) {
        renderedTabKey = "";
        activateTab(key);
    }

    private void selectTab(String key) {
        String normalizedKey = "producao".equals(key) ? "estoque" : key;
        String selectedKey = Set.of("lancamentos", "dia", "mes", "refugo").contains(normalizedKey)
                ? (user.canSeeSummaries() ? "dia" : "lancamentos")
                : normalizedKey;
        for (var e : tabKeys.entrySet()) {
            if (e.getValue().equals(selectedKey)) {
                mainTabs.setSelectedTab(e.getKey());
                renderedTabKey = "";
                activateTab(normalizedKey);
                return;
            }
        }
        mainTabs.setSelectedIndex(0);
        activateTab(tabKeys.get(mainTabs.getSelectedTab()));
    }

    private void activateTab(String key) {
        if (key == null || key.isBlank() || Objects.equals(renderedTabKey, key)) return;
        renderedTabKey = key;
        VaadinSession.getCurrent().setAttribute("gp_tab", key);
        UI.getCurrent().getPage().executeJs(
                "history.replaceState(null,'',location.pathname)");
        render(key);
    }


    private Component menu() {
        Button trigger = new Button("•••");
        trigger.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        trigger.addClassNames("gp-menu-button", "gp-system-menu-trigger-v065", "gp-system-menu-trigger-v066");
        trigger.setAriaLabel(t("Menu"));
        ContextMenu dropdown = new ContextMenu();
        dropdown.setTarget(trigger);
        dropdown.setOpenOnClick(true);
        MenuItem me = dropdown.addItem(user.username());
        me.addClassName("gp-menu-caption");
        me.setEnabled(false);
        if (user.isAdmin()) {
            dropdown.addItem(t("Cadastro"), e -> showRegistry());
            dropdown.addItem(t("Usuários"), e -> showUsers());
        } else {
            dropdown.addItem(t("Alterar Senha"), e -> showOwnPassword());
        }
        dropdown.addItem(t("Relatórios"), e -> showReports());
        MenuItem languageMenu = dropdown.addItem("pt-BR / en-US");
        languageMenu.getSubMenu().addItem("pt-BR", e -> changeLanguage("pt-BR"));
        languageMenu.getSubMenu().addItem("en-US", e -> changeLanguage("en-US"));
        MenuItem theme = dropdown.addItem(t("Tema"));
        theme.getSubMenu().addItem(t("Sistema"), e -> setThemeMode("system"));
        theme.getSubMenu().addItem(t("Claro"), e -> setThemeMode("light"));
        theme.getSubMenu().addItem(t("Escuro"), e -> setThemeMode("dark"));
        dropdown.addItem(t("Sair"), e -> {
            clearTabAuthentication();
            auth.logout();
            user = null;
            buildLogin();
        });
        menuSyncItem = dropdown.addItem("");
        menuSyncItem.setEnabled(false);
        menuSyncItem.addClassName("gp-menu-sync-info-v045");
        refreshMenuSyncStatus();
        installContextMenuHoverOnly(trigger);
        return trigger;
    }

    private void changeLanguage(String lang) {
        auth.saveLanguage(user.id(), lang);
        user = auth.current();
        language = user.language();
        buildApp();
    }


    private void setThemeMode(String mode) {
        String safe = List.of("system", "light", "dark").contains(mode) ? mode : "system";
        VaadinSession.getCurrent().setAttribute("gp_theme", safe);
        applyThemeMode();
    }

    private void applyThemeMode() {
        Object saved = VaadinSession.getCurrent().getAttribute("gp_theme");
        String requested = saved == null ? "" : String.valueOf(saved);
        UI.getCurrent().getPage().executeJs(
                """
                (() => {
                    const root = document.documentElement;
                    const explicit = String($0 || '').trim();
                    const stored = localStorage.getItem('globoplast_theme');
                    const mode = ['system','light','dark'].includes(explicit)
                        ? explicit
                        : (['system','light','dark'].includes(stored) ? stored : 'system');

                    const media = window.matchMedia('(prefers-color-scheme: dark)');
                    const apply = () => {
                        const resolved = mode === 'system'
                            ? (media.matches ? 'dark' : 'light')
                            : mode;
                        root.setAttribute('theme', resolved);
                        root.setAttribute('data-gp-theme', resolved);
                        root.setAttribute('data-gp-theme-mode', mode);
                        root.style.colorScheme = resolved;
                    };

                    if (window.__globoplastThemeMedia && window.__globoplastThemeHandler) {
                        try {
                            window.__globoplastThemeMedia.removeEventListener(
                                'change', window.__globoplastThemeHandler
                            );
                        } catch (e) {}
                    }

                    if (explicit) localStorage.setItem('globoplast_theme', mode);
                    apply();

                    if (mode === 'system') {
                        window.__globoplastThemeMedia = media;
                        window.__globoplastThemeHandler = apply;
                        try {
                            media.addEventListener('change', apply);
                        } catch (e) {
                            media.addListener?.(apply);
                        }
                    }
                })();
                """, requested);
    }

    private void ensureFavicon() {
        UI.getCurrent().getPage().executeJs(
                "document.querySelectorAll('link[rel~=icon],link[rel=\"shortcut icon\"]').forEach(x=>x.remove());" +
                "const l=document.createElement('link');" +
                "l.rel='icon';l.type='image/png';l.setAttribute('data-gp-favicon','1');" +
                "l.href='/favicon.png?v=075-20260821';document.head.appendChild(l);");
    }

    private String syncSignature() {
        try { return sync.status().toString(); } catch (Exception ignored) { return ""; }
    }

    private Component footer() {
        Div footer = new Div();
        footer.addClassName("gp-footer");
        Span signature = new Span("v.");
        signature.addClassName("gp-footer-signature");
        String versionText = "globoplast.app " + AppConfig.VERSION;
        Tooltip.forComponent(signature).withText(versionText).withPosition(Tooltip.TooltipPosition.TOP);
        signature.getElement().setAttribute("aria-label", versionText);
        footer.add(signature);
        return footer;
    }

    private void render(String key) {
        activeDayRefresh = null;
        activeMonthRefresh = null;
        switch (key) {
            case "dia" -> renderDay();
            case "mes" -> renderMonth();
            case "refugo" -> renderScrap();
            case "estoque", "producao" -> renderOrderProduction();
            default -> renderLaunches();
        }
    }

    private void renderLaunches() {
        content.removeAll();
        Div titleRow = new Div();
        titleRow.addClassNames("gp-title-row", "gp-title-row-static");
        H2 title = new H2(t("Lançamentos"));
        title.addClassName("gp-section-title");
        titleRow.add(title);

        TextField search = new TextField(t("Pesquisar lançamento"));
        search.setPlaceholder(t("Digite o código, cliente ou Nº da OP"));
        search.setClearButtonVisible(true);
        search.setValue(launchSearch);
        search.setValueChangeMode(ValueChangeMode.EAGER);
        search.setValueChangeTimeout(50);
        search.getElement().setAttribute("autocomplete", "off");
        search.getElement().setAttribute("spellcheck", "false");
        search.addValueChangeListener(e -> {
            launchSearch = e.getValue();
            launchLimit = AppConfig.PAGE_SIZE;
            refreshLaunchGrid();
        });
        Button filter = searchFilterButton();
        filter.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        filter.addClassName("gp-filter-button");
        filter.setAriaLabel(t("Filtros"));
        filter.setTooltipText(t("Filtros")).withPosition(Tooltip.TooltipPosition.TOP);
        Popover filterDropdown = launchFilterDropdown(filter);
        Div toolbar = new Div(search, filter);
        toolbar.addClassNames("gp-toolbar", "gp-tab-controls", "gp-search-filter-toolbar-v044", "gp-launch-toolbar-v045");
        if (user.canModifyLaunches()) {
            Button add = new Button(t("Novo Lançamento"), VaadinIcon.PLUS.create());
            add.addThemeVariants(ButtonVariant.PRIMARY);
            add.addClassNames("gp-new-button", "gp-launch-new-inline-v045");
            add.addClickListener(e -> showLaunchDialog(null));
            toolbar.add(add);
        }
        content.add(titleRow, toolbar, filterDropdown);

        Grid<LaunchRecord> grid = launchGrid();
        grid.setId("launch-grid");
        content.add(grid);
        Button more = new Button(t("Mostrar mais"));
        more.setId("launch-more");
        more.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        more.addClassName("gp-show-more");
        more.addClickListener(e -> {
            launchLimit += AppConfig.PAGE_SIZE;
            refreshLaunchGrid();
        });
        content.add(more);
        refreshLaunchGrid();
        refreshMenuSyncStatus();
    }

    private void renderOrderProduction() {
        content.removeAll();
        H2 title = new H2(t("Estoque por OP"));
        title.addClassName("gp-section-title");

        Paragraph explanation = new Paragraph(t("Dados do ERP separados por OP e processo. OPs atuais usam Planejamento/Estoque; OPs encerradas ausentes do planejamento usam os Apontamentos do ERP."));
        explanation.addClassName("gp-muted");

        TextField order = new TextField(t("Nº da OP"));
        order.setPlaceholder(t("Digite o Nº da OP"));
        order.setValue(productionOrderSearch);
        order.setClearButtonVisible(true);
        order.getElement().setAttribute("autocomplete", "off");
        Button search = new Button(t("Buscar"), VaadinIcon.SEARCH.create());
        search.addThemeVariants(ButtonVariant.PRIMARY);
        Runnable apply = () -> {
            productionOrderSearch = Norm.order(order.getValue());
            order.setValue(productionOrderSearch);
            refreshOrderProduction();
        };
        search.addClickListener(e -> apply.run());
        order.addKeyPressListener(Key.ENTER, e -> apply.run());
        order.addValueChangeListener(e -> {
            if (e.getValue() == null || e.getValue().isBlank()) {
                productionOrderSearch = "";
                refreshOrderProduction();
            }
        });

        Div searchRow = new Div(order, search);
        searchRow.addClassName("gp-order-production-search-v110");

        Span state = new Span();
        state.setId("order-production-state");
        state.addClassName("gp-muted");

        Grid<LaunchService.OrderProcessProgress> grid = new Grid<>(LaunchService.OrderProcessProgress.class, false);
        grid.setId("order-production-grid");
        grid.addClassName("gp-order-production-grid-v110");
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(LaunchService.OrderProcessProgress::process).setHeader(t("Processo")).setAutoWidth(true);
        grid.addColumn(r -> t(r.processName())).setHeader(t("Etapa")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(r -> fullTextCell(r.product())))
                .setHeader(t("Código Produto")).setWidth("160px").setFlexGrow(0);
        grid.addColumn(new ComponentRenderer<>(r -> fullTextCell(r.description())))
                .setHeader(t("Descrição")).setWidth("300px").setFlexGrow(1);
        grid.addColumn(r -> formatInt(r.plannedPcs())).setHeader(t("Programado (OP)")).setAutoWidth(true);
        grid.addColumn(r -> formatInt(r.producedPcs())).setHeader(t("Produzido (OP)")).setAutoWidth(true);
        grid.addColumn(r -> formatInt(r.remainingPcs())).setHeader(t("Falta (OP)")).setAutoWidth(true);
        grid.addColumn(r -> productionPeriod(r.firstDate(), r.lastDate())).setHeader(t("Período")).setAutoWidth(true);
        grid.setAllRowsVisible(true);

        content.add(title, explanation, searchRow, state, grid);
        refreshOrderProduction();
    }

    @SuppressWarnings("unchecked")
    private void refreshOrderProduction() {
        Component component = byId("order-production-grid");
        if (!(component instanceof Grid<?> raw)) return;
        Grid<LaunchService.OrderProcessProgress> grid = (Grid<LaunchService.OrderProcessProgress>) raw;
        Component stateComponent = byId("order-production-state");
        Span state = stateComponent instanceof Span span ? span : null;
        if (productionOrderSearch == null || productionOrderSearch.isBlank()) {
            grid.setItems(List.of());
            if (state != null) state.setText(t("Informe uma OP para consultar os processos 770, 771, 772, 773, 775 e 776."));
            return;
        }
        List<LaunchService.OrderProcessProgress> rows = launches.orderProcessProgress(productionOrderSearch);
        grid.setItems(rows);
        if (state != null) {
            state.setText(rows.isEmpty()
                    ? t("Nenhum dado de produção encontrado no ERP para esta OP.")
                    : t("OP") + " " + productionOrderSearch + " · " + rows.size() + " " + t(rows.size() == 1 ? "processo encontrado" : "processos encontrados"));
        }
    }

    private String productionPeriod(LocalDate first, LocalDate last) {
        if (first == null && last == null) return "—";
        if (Objects.equals(first, last)) return Norm.br(first);
        return Norm.br(first) + " – " + Norm.br(last);
    }

    private Grid<LaunchRecord> launchGrid() {
        Grid<LaunchRecord> grid = new Grid<>(LaunchRecord.class, false);
        grid.addClassName("gp-launch-grid-v059");
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(r -> Norm.br(r.getDate())).setHeader(t("Data")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(r -> fullTextCell(r.getMachine())))
                .setHeader(t("Máquina")).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(new ComponentRenderer<>(this::launchProductCell)).setHeader(t("Código Produto")).setFlexGrow(2);
        grid.addColumn(new ComponentRenderer<>(this::launchOrderCell)).setHeader(t("Nº da OP")).setAutoWidth(true);
        grid.addColumn(r -> formatInt(r.getTotalProduced())).setHeader(t("Total Lançamento")).setAutoWidth(true);
        grid.addColumn(r -> format(r.getScrapTotalKg())).setHeader(t("Refugo (kg)")).setAutoWidth(true);
        grid.addColumn(r -> formatInt(r.getScrapTotalPcs())).setHeader(t("Refugo (pçs)")).setAutoWidth(true);
        grid.addColumn(r -> format(r.getScrapPct()) + "%").setHeader(t("Refugo (%)")).setAutoWidth(true);
        grid.addColumn(r -> r.isOrderProgressAvailable() ? formatInt(r.getOrderPlannedPcs()) : "—").setHeader(t("Programado (OP)")).setAutoWidth(true);
        grid.addColumn(r -> r.isOrderProgressAvailable() ? formatInt(r.getOrderLaunchedPcs()) : "—").setHeader(t("Produzido (OP)")).setAutoWidth(true);
        grid.addColumn(r -> r.isOrderProgressAvailable() ? formatInt(r.getOrderRemainingPcs()) : "—").setHeader(t("Falta (OP)")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(r -> oeeCell(r, 1))).setHeader("OEE").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::launchActions)).setHeader(t("Ações")).setAutoWidth(true);
        grid.setAllRowsVisible(true);
        return grid;
    }

    private Component launchActions(LaunchRecord record) {
        HorizontalLayout actions = new HorizontalLayout();
        actions.setPadding(false);
        actions.setSpacing(false);
        Button view = icon(VaadinIcon.EYE, t("Visualizar lançamento"));
        view.addClickListener(e -> showLaunchView(record));
        actions.add(view);
        if (canActOnLaunch(record)) {
            Button edit = icon(VaadinIcon.EDIT, t("Editar"));
            edit.addClickListener(e -> showLaunchDialog(record));
            Button delete = icon(VaadinIcon.TRASH, t("Excluir"));
            delete.addClickListener(e -> confirmDelete(record));
            actions.add(edit, delete);
        }
        return actions;
    }

    private boolean canActOnLaunch(LaunchRecord record) {
        if (user == null || !user.canModifyLaunches() || record == null) return false;
        if (user.isAdmin()) return true;
        String allowed = user.sector() == null ? "" : user.sector().trim();
        if (allowed.isBlank()) return false;
        String target = record.getSector() == null ? "" : record.getSector().trim();
        if (target.isBlank()) {
            for (Machine m : catalog.machines()) {
                if (m.name() != null && record.getMachine() != null && m.name().equalsIgnoreCase(record.getMachine())) {
                    target = m.sector() == null ? "" : m.sector().trim();
                    break;
                }
            }
        }
        return !target.isBlank() && allowed.equalsIgnoreCase(target);
    }

    private Button icon(VaadinIcon icon, String tip) {
        String glyph = icon == VaadinIcon.EYE ? "👁️" : icon == VaadinIcon.EDIT ? "✏️" : icon == VaadinIcon.TRASH ? "❌" : "";
        Button button = new Button(glyph);
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.setTooltipText(tip).withPosition(Tooltip.TooltipPosition.TOP);
        button.getElement().setAttribute("aria-label", tip);
        button.addClassName("gp-action-icon");
        if (icon == VaadinIcon.TRASH) button.addClassName("gp-action-icon-danger");
        return button;
    }

    @SuppressWarnings("unchecked")
    private void refreshLaunchGrid() {
        Component component = byId("launch-grid");
        if (!(component instanceof Grid<?> raw)) return;
        Grid<LaunchRecord> grid = (Grid<LaunchRecord>) raw;
        List<LaunchRecord> all = cachedLaunchData(launchStart, launchEnd);
        List<LaunchRecord> filtered = launches.filter(all, launchSearch, launchSectors, launchStart, launchEnd, launchMachines, launchClients);
        grid.setItems(filtered.stream().limit(launchLimit).toList());
        Component more = byId("launch-more");
        if (more != null) more.setVisible(filtered.size() > launchLimit);
    }

    private void refreshMenuSyncStatus() {
        if (menuSyncItem == null) return;
        Map<String, String> states = launches.syncStatus();
        ZonedDateTime latest = null;
        for (String ts : states.values()) {
            if (ts == null || ts.isBlank()) continue;
            try {
                ZonedDateTime parsed = ZonedDateTime.parse(ts).withZoneSameInstant(AppConfig.ZONE);
                if (latest == null || parsed.isAfter(latest)) latest = parsed;
            } catch (Exception ignored) { }
        }
        boolean online = latest != null
                && Duration.between(latest, ZonedDateTime.now(AppConfig.ZONE)).abs().toMinutes() <= 10;
        String time = latest == null ? "—" : latest.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        menuSyncItem.getElement().setText(t("Última sincronização") + " " + time);
        menuSyncItem.getElement().setAttribute("data-gp-sync-state", online ? "online" : "offline");
        menuSyncItem.getElement().setAttribute("data-gp-sync-label", t(online ? "Online" : "Offline"));
    }

    private void invalidateDataCaches() {
        dataRevision++;
        launchRangeCache.clear();
        scrapRangeCache.clear();
        launchBoundsRevision = -1;
        scrapBoundsRevision = -1;
    }

    private List<LaunchRecord> cachedLaunchData(LocalDate start, LocalDate end) {
        if (start == null) start = Norm.productiveToday();
        if (end == null) end = start;
        if (end.isBefore(start)) { LocalDate tmp = start; start = end; end = tmp; }
        String key = start + "|" + end;
        List<LaunchRecord> cached = launchRangeCache.get(key);
        if (cached != null) return cached;
        List<LaunchRecord> loaded = List.copyOf(launches.all(start, end));
        putBounded(launchRangeCache, key, loaded);
        return loaded;
    }

    private List<RefugoRecord> cachedScrapData(LocalDate start, LocalDate end) {
        if (start == null) start = Norm.productiveToday();
        if (end == null) end = start;
        if (end.isBefore(start)) { LocalDate tmp = start; start = end; end = tmp; }
        String key = start + "|" + end;
        List<RefugoRecord> cached = scrapRangeCache.get(key);
        if (cached != null) return cached;
        List<RefugoRecord> loaded = List.copyOf(scraps.load(start, end));
        putBounded(scrapRangeCache, key, loaded);
        return loaded;
    }

    private static <T> void putBounded(Map<String, List<T>> cache, String key, List<T> value) {
        cache.put(key, value);
        while (cache.size() > RANGE_CACHE_LIMIT) {
            String oldest = cache.keySet().iterator().next();
            cache.remove(oldest);
        }
    }

    private LocalDate[] cachedLaunchBounds() {
        if (launchBoundsRevision != dataRevision || launchBoundsCache == null) {
            launchBoundsCache = launches.dateBounds();
            launchBoundsRevision = dataRevision;
        }
        return launchBoundsCache.clone();
    }

    private LocalDate[] cachedScrapBounds() {
        if (scrapBoundsRevision != dataRevision || scrapBoundsCache == null) {
            scrapBoundsCache = scraps.dateBounds();
            scrapBoundsRevision = dataRevision;
        }
        return scrapBoundsCache.clone();
    }

    private void configureAdaptiveGridHeight(Grid<?> grid, int rowCount, int inlineLimit, int maxHeightPx) {
        if (rowCount <= inlineLimit) {
            grid.setAllRowsVisible(true);
            grid.getStyle().remove("height");
            grid.removeClassName("gp-virtual-grid");
            return;
        }
        // Para conjuntos grandes, mantém a virtualização do Vaadin em vez de
        // criar centenas/milhares de linhas DOM de uma vez.
        grid.setAllRowsVisible(false);
        grid.setHeight(maxHeightPx + "px");
        grid.addClassName("gp-virtual-grid");
    }

    private Component byId(String id) {
        return content.getChildren().flatMap(this::deep)
                .filter(c -> c.getId().orElse("").equals(id))
                .findFirst().orElse(null);
    }

    private java.util.stream.Stream<Component> deep(Component component) {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(component),
                component.getChildren().flatMap(this::deep));
    }

    private Popover launchFilterDropdown(Button target) {
        Popover p = new Popover();
        p.setTarget(target);
        p.setPosition(PopoverPosition.BOTTOM_END);
        p.setWidth("min(300px, calc(100vw - 24px))");
        p.setModal(false);
        p.setBackdropVisible(false);
        p.setCloseOnOutsideClick(true);
        p.setCloseOnEsc(true);
        p.setAriaLabel(t("Filtros"));
        p.addClassName("gp-filter-popover");

        LocalDate[] bounds = cachedLaunchBounds();
        DateRangePicker period = new DateRangePicker(
                t("Período"), launchStart, launchEnd,
                bounds[0], bounds[1], language, this::t, null
        );
        MultiSelectComboBox<String> sector = multiSelect(
                t("Setor"), catalog.sectors(), launchSectors, t("Todos")
        );
        forceUppercaseSectorFilter(sector);
        List<Machine> launchFilterMachines = catalog.machines();
        MultiSelectComboBox<String> machine = multiSelect(
                t("Máquina"), launchMachineOptions(launchFilterMachines, launchSectors),
                launchMachines, t("Todos")
        );
        MultiSelectComboBox<String> client = multiSelect(
                t("Cliente"), launchClientOptions(cachedLaunchData(launchStart, launchEnd)),
                launchClients, t("Todos")
        );

        Runnable apply = () -> {
            launchStart = period.getStart();
            launchEnd = period.getEnd();
            replace(launchSectors, sector.getValue());
            replace(launchMachines, machine.getValue());
            replace(launchClients, client.getValue());
            launchLimit = AppConfig.PAGE_SIZE;
            updateFilterButton(target, launchFiltersActive());
            refreshLaunchGrid();
        };
        period.setChangeListener(() -> {
            launchStart = period.getStart();
            launchEnd = period.getEnd();
            List<String> options = launchClientOptions(cachedLaunchData(launchStart, launchEnd));
            Set<String> valid = new LinkedHashSet<>(client.getValue());
            valid.removeIf(v -> !options.contains(v));
            client.setItems(options);
            if (!Objects.equals(valid, client.getValue())) client.setValue(valid);
            apply.run();
        });
        sector.addValueChangeListener(e -> {
            List<String> options = launchMachineOptions(launchFilterMachines, e.getValue());
            Set<String> valid = new LinkedHashSet<>(machine.getValue());
            valid.removeIf(v -> !options.contains(v));
            machine.setItems(options);
            if (!Objects.equals(valid, machine.getValue())) machine.setValue(valid);
            apply.run();
        });
        machine.addValueChangeListener(e -> apply.run());
        client.addValueChangeListener(e -> apply.run());

        Button clear = new Button(t("Limpar filtros"), e -> {
            LocalDate today = Norm.productiveToday();
            launchStart = today;
            launchEnd = today;
            launchSectors.clear();
            launchMachines.clear();
            launchClients.clear();
            launchSearch = "";
            launchLimit = AppConfig.PAGE_SIZE;
            updateFilterButton(target, launchFiltersActive());
            p.setOpened(false);
            renderLaunches();
        });
        clear.setWidthFull();
        clear.addClassName("gp-filter-clear");

        Div fields = new Div(period, sector, machine, client);
        fields.addClassName("gp-filter-dropdown-grid");
        Div actions = new Div();
        actions.add(clear);
        actions.addClassName("gp-filter-dropdown-actions");
        Div body = new Div(fields, actions);
        body.addClassName("gp-filter-dropdown");
        p.add(body);
        updateFilterButton(target, launchFiltersActive());
        return p;
    }


    private Popover summaryFilterDropdown(Button target, Div fields, Runnable clearAction) {
        Popover p = new Popover();
        p.setTarget(target);
        p.setPosition(PopoverPosition.BOTTOM_END);
        p.setWidth("min(320px, calc(100vw - 24px))");
        p.setModal(false);
        p.setBackdropVisible(false);
        p.setCloseOnOutsideClick(true);
        p.setCloseOnEsc(true);
        p.setAriaLabel(t("Filtros"));
        p.addClassNames("gp-filter-popover", "gp-summary-filter-popover-v047");

        Button clear = new Button(t("Limpar filtros"), e -> {
            clearAction.run();
            p.setOpened(false);
        });
        clear.setWidthFull();
        clear.addClassName("gp-filter-clear");

        Div actions = new Div(clear);
        actions.addClassName("gp-filter-dropdown-actions");
        Div body = new Div(fields, actions);
        body.addClassNames("gp-filter-dropdown", "gp-summary-filter-dropdown-v047");
        p.add(body);
        return p;
    }

    private void showLaunchDialog(LaunchRecord original) {
        boolean edit = original != null;
        LaunchRecord record = edit ? original.copy() : new LaunchRecord();
        if (!edit) record.setDate(Norm.productiveToday());
        String title = edit ? t("Editar Lançamento") : t("Novo Lançamento");
        Dialog dialog = launchDialog(title);
        Div itemDescription = addLaunchItemDescription(dialog, record);
        LaunchFormFields fields = createLaunchForm(record, false, !edit, edit);
        configureProductMetadataLookup(fields.product, record, itemDescription);
        dialog.add(fields.root);

        Button save = new Button(edit ? t("Salvar Alterações") : t("Salvar Lançamento"));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.addClickListener(e -> {
            try {
                applyLaunchForm(record, fields);
                if (record.getShiftA() + record.getShiftB() + record.getShiftC() == 0) {
                    throw new IllegalArgumentException(t("IMPOSSÍVEL SALVAR: Preencha a Produção de pelo menos um turno."));
                }
                if (edit) {
                    if (record.isErp()) launches.saveErpOverride(record, user);
                    else launches.updateManual(record, user);
                } else {
                    launches.saveManual(record, user);
                }
                dialog.close();
                launchLimit = AppConfig.PAGE_SIZE;
                if (edit && record.getDate() != null) {
                    if (launchStart == null || record.getDate().isBefore(launchStart)) launchStart = record.getDate();
                    if (launchEnd == null || record.getDate().isAfter(launchEnd)) launchEnd = record.getDate();
                }
                invalidateDataCaches();
                renderLaunches();
                notify(t(edit
                        ? "Lançamento atualizado no Banco de Dados!"
                        : "Lançamento salvo no Banco de Dados!"));
            } catch (Exception ex) {
                String message = ex.getMessage();
                notify(message == null || message.isBlank() ? t("Não foi possível salvar o lançamento.") : t(message));
            }
        });
        Button cancel = new Button(t("Cancelar"), e -> dialog.close());
        HorizontalLayout footer = new HorizontalLayout(save, cancel);
        footer.addClassName("gp-launch-dialog-actions");
        footer.setWidthFull();
        footer.setFlexGrow(1, save, cancel);
        dialog.getFooter().add(footer);
        dialog.open();
        configureLaunchKeyboard(fields);
        fields.date.focus();
    }

    private void showLaunchView(LaunchRecord record) {
        Dialog dialog = launchDialog(t("Visualizar lançamento"));
        addLaunchItemDescription(dialog, record);
        LaunchFormFields fields = createLaunchForm(record.copy(), true, false, true);
        dialog.add(fields.root);
        dialog.open();
    }

    private Dialog launchDialog(String title) {
        Dialog d = dialog(title);
        d.addClassName("gp-launch-dialog");
        d.setWidth("min(820px, calc(100vw - 32px))");
        d.setTop("37px");
        return d;
    }

    private Div addLaunchItemDescription(Dialog dialog, LaunchRecord record) {
        Div itemDescription = new Div();
        itemDescription.addClassName("gp-launch-item-description-v094");
        updateLaunchItemDescription(itemDescription, record);
        if (dialog != null) dialog.add(itemDescription);
        return itemDescription;
    }

    private void updateLaunchItemDescription(Div itemDescription, LaunchRecord record) {
        if (itemDescription == null) return;
        String value = launchProductMetadataText(record);
        itemDescription.setText(value);
        itemDescription.setVisible(!value.isBlank());
    }

    private void configureProductMetadataLookup(TextField product, LaunchRecord record, Div itemDescription) {
        if (product == null || product.isReadOnly()) return;
        product.setValueChangeMode(ValueChangeMode.LAZY);
        product.setValueChangeTimeout(300);
        product.addValueChangeListener(event -> {
            LaunchService.ProductMetadata metadata = launches.productMetadata(event.getValue());
            record.setProduct(normalizeProduct(event.getValue()));
            record.setDescriptionErp(metadata.description());
            record.setClientErp(metadata.client());
            updateLaunchItemDescription(itemDescription, record);
        });
    }


    private LaunchFormFields createLaunchForm(LaunchRecord record, boolean readOnly, boolean isNew, boolean showTime) {
        LaunchFormFields f = new LaunchFormFields();
        f.date = datePicker(t("Data da Produção"), record.getDate() == null ? Norm.productiveToday() : record.getDate());
        f.date.setReadOnly(readOnly);
        f.date.setWidthFull();
        f.date.addClassName("gp-launch-standard-field-v054");

        List<Machine> allowed = readOnly
                ? catalog.machines().stream().filter(m -> Objects.equals(m.name(), record.getMachine())).toList()
                : (user.isAdmin() ? catalog.machines() : catalog.allowedMachines(user));
        // ComboBox compartilha a mesma base visual dos TextField. O Select
        // possui um value-button próprio e continuava visualmente diferente.
        f.machine = new ComboBox<>();
        f.machine.setLabel(t("Máquina"));
        f.machine.setAllowCustomValue(false);
        f.machine.setClearButtonVisible(false);
        f.machine.addClassNames("gp-launch-standard-field-v054", "gp-launch-machine-field-v055");
        // Edição/visualização ERP não pode perder o valor original só porque a
        // máquina ainda não foi cadastrada no Java. O valor atual entra como
        // opção preservada, seguido das máquinas permitidas/cadastradas.
        LinkedHashSet<String> machineOptions = new LinkedHashSet<>();
        String originalMachine = cleanInput(record.getMachine());
        if ((record.isErp() || readOnly) && !originalMachine.isBlank()) machineOptions.add(originalMachine);
        allowed.stream().map(Machine::name).filter(Objects::nonNull).filter(v -> !v.isBlank()).forEach(machineOptions::add);
        f.machine.setItems(machineOptions);
        if (!originalMachine.isBlank() && machineOptions.contains(originalMachine)) {
            f.machine.setValue(originalMachine);
        } else if (!machineOptions.isEmpty()) {
            f.machine.setValue(machineOptions.iterator().next());
        }
        f.machine.setReadOnly(readOnly);
        f.machine.setWidthFull();

        Machine catalogMachineForRecord = findCatalogMachine(originalMachine);
        boolean capacityReadOnly = readOnly || !record.isErp() || catalogMachineForRecord != null;
        f.capacity = textField(t("Capacidade (pçs/24h)"), value(record.getCapacity24h()), capacityReadOnly);
        f.product = textField(t("Cód. Produto"), cleanInput(record.getProduct()), readOnly);
        f.order = textField(t("Nº da OP"), cleanInput(record.getOrderNumber()), readOnly);
        f.hours = textField(t("Hrs Program."), numberValue(record.getScheduledHours()), readOnly);
        f.weight = textField(t("Peso da Bis. (g)"), numberValue(record.getUnitWeightG()), readOnly);
        f.shiftA = textField(t("Turno A (pçs)"), productionInputValue(record, "A"), readOnly);
        f.scrapA = textField(t("Refugo A (kg)"), scrapInputValue(record.getScrapAKg()), readOnly);
        f.shiftB = textField(t("Turno B (pçs)"), productionInputValue(record, "B"), readOnly);
        f.scrapB = textField(t("Refugo B (kg)"), scrapInputValue(record.getScrapBKg()), readOnly);
        f.shiftC = textField(t("Turno C (pçs)"), productionInputValue(record, "C"), readOnly);
        f.scrapC = textField(t("Refugo C (kg)"), scrapInputValue(record.getScrapCKg()), readOnly);
        f.changeovers = textField(t("Qtd. Trocas"), intValue(record.getChangeovers()), readOnly);
        f.setup = textField(t("Setup (hrs)"), numberValue(record.getSetupHours()), readOnly);
        f.breakdown = textField(t("Paradas (hrs)"), numberValue(record.getBreakdownHours()), readOnly);
        f.observations = textField(t("Observações"), cleanProblem(record.getProblem()), readOnly);
        if (!readOnly) forceUppercase(f.observations);

        String productionHelp = t("Com várias OPs, cada valor separado por + pertence à OP da mesma posição. Um único valor pertence à primeira OP; OPs sem valor naquele turno recebem zero.");
        String scrapHelp = t("Digite sem ponto ou vírgula: 2500 = 2,500 kg. Aceita soma (+): 2500+1500 = 4,000 kg.");
        for (TextField field : List.of(f.shiftA, f.shiftB, f.shiftC)) {
            field.setTooltipText(productionHelp).withPosition(Tooltip.TooltipPosition.TOP);
        }
        for (TextField field : List.of(f.scrapA, f.scrapB, f.scrapC)) {
            field.setTooltipText(scrapHelp).withPosition(Tooltip.TooltipPosition.TOP);
        }

        configureInputMode(f.product, "text");
        configureInputMode(f.order, "text");
        configureInputMode(f.hours, "decimal");
        configureInputMode(f.weight, "decimal");
        configureInputMode(f.setup, "decimal");
        configureInputMode(f.breakdown, "decimal");
        configureInputMode(f.shiftA, "tel");
        configureInputMode(f.shiftB, "tel");
        configureInputMode(f.shiftC, "tel");
        configureInputMode(f.scrapA, "tel");
        configureInputMode(f.scrapB, "tel");
        configureInputMode(f.scrapC, "tel");
        configureInputMode(f.changeovers, "numeric");
        configureInputMode(f.observations, "text");

        Machine initialMachine = findCatalogMachine(f.machine.getValue());
        if (initialMachine != null) f.capacity.setValue(String.valueOf(initialMachine.capacity()));
        else if (record.getCapacity24h() > 0) f.capacity.setValue(String.valueOf(record.getCapacity24h()));
        f.machine.addValueChangeListener(e -> {
            Machine m = findCatalogMachine(e.getValue());
            if (m != null) f.capacity.setValue(String.valueOf(m.capacity()));
            else if (record.isErp() && Objects.equals(cleanInput(e.getValue()), originalMachine) && record.getCapacity24h() > 0)
                f.capacity.setValue(String.valueOf(record.getCapacity24h()));
            else f.capacity.setValue("");
        });

        Div rowDate = new Div(f.date);
        rowDate.addClassNames("gp-launch-row", "gp-launch-row-date");
        Div time = new Div();
        time.addClassName("gp-launch-time");
        if (showTime) {
            String hour = record.getLaunchTime() == null || record.getLaunchTime().isBlank() ? "—" : record.getLaunchTime();
            time.setText(t("Hora do lançamento") + ": " + hour);
        }
        Div rowMachine = new Div(f.machine, f.capacity);
        rowMachine.addClassNames("gp-launch-row", "gp-launch-row-2");
        Div rowBasic = new Div(f.product, f.order, f.hours, f.weight);
        rowBasic.addClassNames("gp-launch-row", "gp-launch-row-4");
        Div shiftA = new Div(f.shiftA, f.scrapA);
        shiftA.addClassName("gp-launch-shift-column");
        Div shiftB = new Div(f.shiftB, f.scrapB);
        shiftB.addClassName("gp-launch-shift-column");
        Div shiftC = new Div(f.shiftC, f.scrapC);
        shiftC.addClassName("gp-launch-shift-column");
        Div rowShifts = new Div(shiftA, shiftB, shiftC);
        rowShifts.addClassNames("gp-launch-row", "gp-launch-row-3");
        Div rowStops = new Div(f.changeovers, f.setup, f.breakdown);
        rowStops.addClassNames("gp-launch-row", "gp-launch-row-3");
        Div rowObs = new Div(f.observations);
        rowObs.addClassNames("gp-launch-row", "gp-launch-row-observations");

        f.root = new Div();
        f.root.addClassName("gp-launch-form-python");
        f.root.add(rowDate);
        if (showTime) f.root.add(time);
        f.root.add(rowMachine, rowBasic, rowShifts, rowStops, rowObs);

        if (!readOnly) {
            List<Component> order = List.of(
                    f.date, f.machine, f.product, f.order, f.hours, f.weight,
                    f.shiftA, f.shiftB, f.shiftC,
                    f.scrapA, f.scrapB, f.scrapC,
                    f.changeovers, f.setup, f.breakdown, f.observations
            );
            for (int i = 0; i < order.size(); i++) {
                order.get(i).getElement().setAttribute("data-gp-launch-order", String.valueOf(i));
            }
        }
        return f;
    }

    private void applyLaunchForm(LaunchRecord record, LaunchFormFields f) {
        LocalDate date = f.date.getValue();
        if (date == null) throw new IllegalArgumentException(t("Informe a Data da Produção."));
        String machineName = f.machine.getValue();
        if (machineName == null || machineName.isBlank()) throw new IllegalArgumentException(t("Selecione uma máquina."));

        record.setDate(date);
        record.setMachine(machineName);
        record.setProduct(normalizeProduct(f.product.getValue()));
        LaunchService.ProductMetadata productMetadata = launches.productMetadata(record.getProduct());
        record.setDescriptionErp(productMetadata.description());
        record.setClientErp(productMetadata.client());
        record.setOrderNumber(f.order.getValue() == null ? "" : f.order.getValue().trim());

        double scheduled = parseHours(f.hours.getValue(), 24.0);
        record.setScheduledHours(Norm.round(scheduled <= 0 ? 24.0 : scheduled, 2));

        Machine machine = findCatalogMachine(machineName);
        if (machine != null) {
            record.setCapacity24h(machine.capacity());
            record.setSector(machine.sector());
        } else if (!record.isErp()) {
            throw new IllegalArgumentException(t("Máquina inválida."));
        } else {
            // Para ERP sem máquina cadastrada, a capacidade original continua
            // preservada. Se ainda for zero, o Administrador/Conferente pode
            // informar a Capacidade 24h no próprio override; esse valor passa
            // ao snapshot e é reutilizado em todos os apontamentos da máquina.
            int typedCapacity = (int)Math.round(parseDecimal(f.capacity.getValue(), record.getCapacity24h()));
            if (typedCapacity > 0) record.setCapacity24h(typedCapacity);
        }
        // ERP editado preserva Máquina/Capacidade/Setor originais mesmo que o
        // equipamento ainda não exista no cadastro local. Isso evita apagar
        // metadados válidos ao simplesmente abrir e salvar o item.

        record.setUnitWeightG(parseDecimal(f.weight.getValue(), 0));
        record.setProductionDetail(buildProductionDetail(
                record.getOrderNumber(),
                f.shiftA.getValue(), f.shiftB.getValue(), f.shiftC.getValue()
        ));
        record.setShiftA(parseSumInt(f.shiftA.getValue()));
        record.setShiftB(parseSumInt(f.shiftB.getValue()));
        record.setShiftC(parseSumInt(f.shiftC.getValue()));
        record.setScrapAKg(parseScrapKg(f.scrapA.getValue()));
        record.setScrapBKg(parseScrapKg(f.scrapB.getValue()));
        record.setScrapCKg(parseScrapKg(f.scrapC.getValue()));
        record.setChangeovers((int) parseDecimal(f.changeovers.getValue(), 0));
        record.setSetupHours(Norm.round(parseHours(f.setup.getValue(), 0), 2));
        record.setBreakdownHours(Norm.round(parseHours(f.breakdown.getValue(), 0), 2));

        String obs = f.observations.getValue() == null ? "" : f.observations.getValue().trim();
        record.setProblem(obs.isBlank() ? "Nenhum" : obs.toUpperCase(locale()));
    }

    private Machine findCatalogMachine(String name) {
        if (name == null || name.isBlank()) return null;
        Machine direct = catalog.machineMap().get(name);
        if (direct != null) return direct;
        String wanted = machineUiKey(name);
        for (Machine m : catalog.machines()) if (machineUiKey(m.name()).equals(wanted)) return m;
        return null;
    }

    private static String machineUiKey(String value) {
        String normalized = Norm.machine(value == null ? "" : value);
        String folded = Norm.fold(normalized).replaceAll("[^a-z0-9]+", " ").trim();
        if (folded.isBlank()) return "";
        StringBuilder key = new StringBuilder();
        for (String token : folded.split("\\s+")) {
            if (token.matches("\\d+")) {
                try { key.append(Integer.parseInt(token)); } catch (Exception ignored) { key.append(token); }
            } else key.append(token);
        }
        return key.toString();
    }

    private void configureLaunchKeyboard(LaunchFormFields f) {
        if (f == null || f.root == null) return;
        f.root.getElement().executeJs(
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

                        // Enter seleciona normalmente uma opção enquanto o
                        // dropdown da Máquina estiver aberto. Fora disso,
                        // Enter/Tab mantêm a navegação sequencial do formulário.
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

    private void configureInputMode(TextField field, String mode) {
        if (field == null) return;
        field.getElement().setAttribute("data-gp-inputmode", mode == null ? "text" : mode);
    }

    private String productionInputValue(LaunchRecord record, String shift) {
        int fallback = switch (shift) {
            case "A" -> record.getShiftA();
            case "B" -> record.getShiftB();
            case "C" -> record.getShiftC();
            default -> 0;
        };
        String detail = record.getProductionDetail();
        List<String> currentOps = extractOps(record.getOrderNumber());
        if (detail == null || detail.isBlank() || currentOps.size() < 2) return intValue(fallback);

        try {
            List<String> detailOps = extractJsonStringArray(detail, "ops");
            if (!detailOps.equals(currentOps)) return intValue(fallback);

            int aggregate = extractJsonObjectInt(detail, "agregados", shift, 0);
            if (aggregate != 0) return String.valueOf(aggregate);

            List<Integer> values = extractJsonIntArray(detail, shift);
            int quantity = extractJsonObjectInt(detail, "quantidades_componentes", shift, values.size());
            quantity = Math.max(0, Math.min(quantity, values.size()));
            if (quantity == 0) return "";
            return values.subList(0, quantity).stream().map(String::valueOf).collect(Collectors.joining("+"));
        } catch (Exception ignored) {
            return intValue(fallback);
        }
    }

    private String buildProductionDetail(String orderNumber, String shiftA, String shiftB, String shiftC) {
        List<String> ops = extractOps(orderNumber);
        if (ops.size() < 2) return "";

        Map<String, List<Integer>> turns = new LinkedHashMap<>();
        Map<String, Integer> aggregate = new LinkedHashMap<>();
        Map<String, Integer> quantities = new LinkedHashMap<>();
        Map<String, String> input = Map.of("A", nz(shiftA), "B", nz(shiftB), "C", nz(shiftC));

        for (String shift : List.of("A", "B", "C")) {
            List<Integer> components = parseProductionComponents(input.get(shift), shift);
            int mapped = Math.min(components.size(), ops.size());
            List<Integer> values = new ArrayList<>();
            for (int i = 0; i < ops.size(); i++) values.add(i < mapped ? components.get(i) : 0);
            int extra = components.size() <= ops.size()
                    ? 0
                    : components.subList(ops.size(), components.size()).stream().mapToInt(Integer::intValue).sum();
            turns.put(shift, values);
            aggregate.put(shift, extra);
            quantities.put(shift, mapped);
        }

        List<Integer> totals = new ArrayList<>();
        for (int i = 0; i < ops.size(); i++) {
            totals.add(turns.get("A").get(i) + turns.get("B").get(i) + turns.get("C").get(i));
        }

        return "{"
                + "\"ops\":" + jsonStringArray(ops) + ","
                + "\"turnos\":{\"A\":" + jsonIntArray(turns.get("A"))
                + ",\"B\":" + jsonIntArray(turns.get("B"))
                + ",\"C\":" + jsonIntArray(turns.get("C")) + "},"
                + "\"totais\":" + jsonIntArray(totals) + ","
                + "\"agregados\":{\"A\":" + aggregate.get("A")
                + ",\"B\":" + aggregate.get("B")
                + ",\"C\":" + aggregate.get("C") + "},"
                + "\"quantidades_componentes\":{\"A\":" + quantities.get("A")
                + ",\"B\":" + quantities.get("B")
                + ",\"C\":" + quantities.get("C") + "}"
                + "}";
    }

    private List<Integer> parseProductionComponents(String raw, String shift) {
        if (raw == null || raw.trim().isBlank()) return new ArrayList<>();
        List<Integer> out = new ArrayList<>();
        try {
            for (String part : raw.trim().split("\\+")) {
                String term = part.trim().replace(',', '.');
                if (term.isBlank()) throw new NumberFormatException();
                out.add((int) Double.parseDouble(term));
            }
            return out;
        } catch (Exception e) {
            String message = "en-US".equals(language)
                    ? "Shift " + shift + " production contains an invalid value. Use numbers separated by + only."
                    : "A produção do Turno " + shift + " contém um valor inválido. Use somente números separados por +.";
            throw new IllegalArgumentException(message);
        }
    }

    private static List<String> extractOps(String raw) {
        if (raw == null || raw.trim().isBlank()) return new ArrayList<>();
        return java.util.Arrays.stream(raw.trim().split("\\s*[/;|]\\s*"))
                .map(String::trim).filter(v -> !v.isBlank()).toList();
    }

    private static List<String> extractJsonStringArray(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\\"" + java.util.regex.Pattern.quote(key) + "\\\"\\s*:\\s*\\[(.*?)]")
                .matcher(json);
        if (!m.find()) return List.of();
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher q = java.util.regex.Pattern
                .compile("\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"")
                .matcher(m.group(1));
        while (q.find()) out.add(jsonUnescape(q.group(1)));
        return out;
    }

    private static List<Integer> extractJsonIntArray(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\\"" + java.util.regex.Pattern.quote(key) + "\\\"\\s*:\\s*\\[([^]]*)]")
                .matcher(json);
        if (!m.find()) return List.of();
        if (m.group(1).trim().isBlank()) return List.of();
        List<Integer> out = new ArrayList<>();
        for (String part : m.group(1).split(",")) out.add((int) Double.parseDouble(part.trim()));
        return out;
    }

    private static int extractJsonObjectInt(String json, String objectName, String key, int def) {
        java.util.regex.Matcher object = java.util.regex.Pattern
                .compile("\\\"" + java.util.regex.Pattern.quote(objectName) + "\\\"\\s*:\\s*\\{([^}]*)}")
                .matcher(json);
        if (!object.find()) return def;
        java.util.regex.Matcher value = java.util.regex.Pattern
                .compile("\\\"" + java.util.regex.Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+)")
                .matcher(object.group(1));
        return value.find() ? Integer.parseInt(value.group(1)) : def;
    }

    private static String jsonStringArray(List<String> values) {
        return values.stream().map(v -> "\"" + jsonEscape(v) + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    private static String jsonIntArray(List<Integer> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    private static String jsonEscape(String value) {
        return nz(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jsonUnescape(String value) {
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '\\' -> out.append('\\');
                    case '"' -> out.append('"');
                    default -> out.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                out.append(c);
            }
        }
        if (escaped) out.append('\\');
        return out.toString();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private String normalizeProduct(String raw) {
        String value = raw == null ? "" : raw.trim();
        return value.isBlank() ? t("Não informado") : value.toUpperCase(locale());
    }

    private static String cleanInput(String value) {
        if (value == null) return "";
        String clean = value.trim();
        if (clean.equalsIgnoreCase("Nenhum") || clean.equalsIgnoreCase("nan") || clean.equalsIgnoreCase("none")) return "";
        return clean;
    }

    private static String cleanProblem(String value) {
        return cleanInput(value);
    }

    private TextField textField(String label, String value, boolean readOnly) {
        TextField field = new TextField(label);
        field.addClassName("gp-launch-standard-field-v054");
        field.setValue(value == null ? "" : value);
        field.setReadOnly(readOnly);
        field.setWidthFull();
        return field;
    }

    private void confirmDelete(LaunchRecord record) {
        Dialog d = dialog(t("Confirmar Exclusão de Lançamento"));
        d.add(new Span(t("Deseja mover este lançamento para a lixeira? Ele poderá ser restaurado por 30 dias.")));
        Button delete = new Button(t("Sim, Excluir Registro"));
        delete.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.PRIMARY);
        delete.addClickListener(e -> {
            try {
                if (record.isErp()) launches.hideErp(record, user);
                else launches.deleteManual(record.getId(), user);
                d.close();
                invalidateDataCaches();
                renderLaunches();
                notify(t("Lançamento movido para a lixeira."));
            } catch (Exception ex) {
                notify(t(ex.getMessage()));
            }
        });
        d.getFooter().add(new Button(t("Cancelar"), e -> d.close()), delete);
        d.open();
    }

    private void showLaunchTrash() {
        Dialog d = dialog(t("Lixeira de Lançamentos"));
        d.setWidth("min(980px, calc(100vw - 32px))");
        Span retention = new Span(t("Os lançamentos excluídos permanecem na lixeira por 30 dias e depois são excluídos definitivamente."));
        retention.addClassName("gp-muted");

        Grid<LaunchService.TrashItem> grid = new Grid<>(LaunchService.TrashItem.class, false);
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(item -> Norm.br(item.record().getDate())).setHeader(t("Data")).setAutoWidth(true);
        grid.addColumn(item -> item.record().getMachine()).setHeader(t("Máquina")).setFlexGrow(2);
        grid.addColumn(item -> item.record().getProduct()).setHeader(t("Código Produto")).setFlexGrow(2);
        grid.addColumn(item -> item.record().getOrderNumber()).setHeader(t("Nº da OP")).setAutoWidth(true);
        grid.addColumn(item -> formatTrashDate(item.deletedAt())).setHeader(t("Excluído em")).setAutoWidth(true);
        grid.addColumn(item -> formatTrashDate(item.expiresAt())).setHeader(t("Exclusão definitiva")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(item -> {
            Button restore = new Button(t("Restaurar"), VaadinIcon.ROTATE_LEFT.create());
            restore.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            restore.addClickListener(e -> {
                try {
                    launches.restoreTrash(item.id(), user);
                    invalidateDataCaches();
                    grid.setItems(launches.trash(user));
                    refreshLaunchGrid();
                    notify(t("Lançamento restaurado com sucesso!"));
                } catch (Exception ex) {
                    String message = ex.getMessage();
                    notify(message == null || message.isBlank() ? t("Não foi possível restaurar o lançamento.") : t(message));
                }
            });
            return restore;
        })).setHeader(t("Ações")).setAutoWidth(true);
        grid.setHeight("420px");
        grid.setItems(launches.trash(user));

        Div body = new Div(retention, grid);
        body.setWidthFull();
        d.add(body);
        d.getFooter().add(new Button(t("Fechar"), e -> d.close()));
        d.open();
    }

    private String formatTrashDate(String value) {
        if (value == null || value.isBlank()) return "—";
        try {
            return ZonedDateTime.parse(value).withZoneSameInstant(AppConfig.ZONE)
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception ignored) {
            return value;
        }
    }

    private void renderDay() {
        content.removeAll();
        Div page = new Div();
        page.addClassNames("gp-summary-page", "gp-summary-day-page", "gp-original-summary");

        H2 title = new H2(t("Resumo Diário da Produção"));
        title.addClassName("gp-section-title");
        Button filter = searchFilterButton();
        filter.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        filter.addClassNames("gp-filter-button", "gp-summary-filter-trigger-v047");
        filter.setAriaLabel(t("Filtros"));
        filter.setTooltipText(t("Filtros")).withPosition(Tooltip.TooltipPosition.TOP);
        Div titleRow = new Div(title, filter);
        titleRow.addClassNames("gp-title-row", "gp-summary-title-row-v047");

        LocalDate[] bounds = cachedLaunchBounds();
        LocalDate today = Norm.productiveToday();
        LocalDate defaultDate = today.isBefore(bounds[0]) ? bounds[0] : (today.isAfter(bounds[1]) ? bounds[1] : today);
        if (summaryDayDate == null || summaryDayDate.isBefore(bounds[0]) || summaryDayDate.isAfter(bounds[1])) summaryDayDate = defaultDate;

        DateRangePicker date = new DateRangePicker(
                t("Escolha a Data"), summaryDayDate, summaryDayDate,
                bounds[0], bounds[1], language, this::t, null, true
        );
        MultiSelectComboBox<String> sector = multiSelect(
                t("Filtrar por Setor"), List.of(), summaryDaySectors, t("Todos")
        );
        forceUppercaseSectorFilter(sector);
        MultiSelectComboBox<String> machine = multiSelect(
                t("Filtrar por Máquina"), List.of(), summaryDayMachines, t("Todas")
        );
        MultiSelectComboBox<String> shift = multiSelect(
                t("Filtrar por Turno"), List.of("A", "B", "C"), summaryDayShifts, t("Todos")
        );
        Div filterFields = new Div(date, sector, machine, shift);
        filterFields.addClassNames("gp-filter-dropdown-grid", "gp-summary-filter-fields-v047");

        Div result = new Div();
        result.addClassName("gp-summary-result");
        boolean[] adjusting={false};
        Runnable[] refreshRef=new Runnable[1];
        refreshRef[0]=()->{
            if(adjusting[0])return;
            LocalDate d=date.getValue(); if(d==null)return;
            summaryDayDate=d;
            List<LaunchRecord> source=cachedLaunchData(d,d);
            List<LaunchRecord> sourceForShift = summaryRowsForShifts(source, summaryDayShifts);
            List<String> sectors=sourceForShift.stream().map(LaunchRecord::getSector).filter(Objects::nonNull).filter(v->!v.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
            adjusting[0]=true;
            try{
                Set<String> validSectors = new LinkedHashSet<>(summaryDaySectors);
                validSectors.removeIf(v -> !sectors.contains(v));
                sector.setItems(sectors);
                sector.setValue(validSectors);
                replace(summaryDaySectors, validSectors);

                List<LaunchRecord> forMachines=sourceForShift;
                if(!summaryDaySectors.isEmpty())forMachines=forMachines.stream()
                        .filter(r->summaryDaySectors.contains(r.getSector())).toList();
                List<String> machines=forMachines.stream().map(LaunchRecord::getMachine).filter(Objects::nonNull).filter(v->!v.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
                Set<String> validMachines = new LinkedHashSet<>(summaryDayMachines);
                validMachines.removeIf(v -> !machines.contains(v));
                machine.setItems(machines);
                machine.setValue(validMachines);
                replace(summaryDayMachines, validMachines);
            }finally{adjusting[0]=false;}

            updateFilterButton(filter, !Objects.equals(summaryDayDate, defaultDate) || !summaryDaySectors.isEmpty() || !summaryDayMachines.isEmpty() || !summaryDayShifts.isEmpty());

            List<LaunchRecord> rows=sourceForShift;
            if(!summaryDaySectors.isEmpty())rows=rows.stream().filter(r->summaryDaySectors.contains(r.getSector())).toList();
            if(!summaryDayMachines.isEmpty())rows=rows.stream().filter(r->summaryDayMachines.contains(r.getMachine())).toList();
            List<LaunchRecord> summary=summarizeDaily(rows);
            result.removeAll();
            if(summary.isEmpty()){
                result.add(emptyState(t("Nenhum lançamento encontrado para os filtros selecionados.")));
                return;
            }

            Div metrics=new Div(
                    kpi(t("🎯 OEE Geral"), format1(avg(summary,"oee"))+"%"),
                    kpi(t("⏱️ Disponibilidade"), format1(avg(summary,"availability"))+"%"),
                    kpi(t("⚡ Desempenho"), format1(avg(summary,"performance"))+"%"),
                    kpi(t("✨ Qualidade"), format1(avg(summary,"quality"))+"%"),
                    kpi(t("📦 Peças Boas Produzidas"), formatInt(summary.stream().mapToInt(LaunchRecord::getTotalProduced).sum())+" "+t("pçs"))
            );
            metrics.addClassNames("gp-original-metrics", "gp-original-metrics-5");

            H3 tableTitle=new H3(t("Tabela Consolidada por Máquina")); tableTitle.addClassName("gp-original-subtitle");
            Grid<LaunchRecord> grid=dailySummaryGrid(); grid.setItems(summary);
            result.add(metrics,tableTitle,grid);
        };
        date.setChangeListener(() -> {
            if (!adjusting[0]) {
                summaryDayDate = date.getValue();
                refreshRef[0].run();
            }
        });
        sector.addValueChangeListener(e->{if(!adjusting[0]){replace(summaryDaySectors,e.getValue());refreshRef[0].run();}});
        machine.addValueChangeListener(e->{if(!adjusting[0]){replace(summaryDayMachines,e.getValue());refreshRef[0].run();}});
        shift.addValueChangeListener(e->{if(!adjusting[0]){replace(summaryDayShifts,e.getValue());refreshRef[0].run();}});
        activeDayRefresh = refreshRef[0];

        Popover filterDropdown = summaryFilterDropdown(filter, filterFields, () -> {
            adjusting[0]=true;
            try {
                summaryDayDate=defaultDate;
                summaryDaySectors.clear();
                summaryDayMachines.clear();
                summaryDayShifts.clear();
                date.setValue(defaultDate);
                sector.clear();
                machine.clear();
                shift.clear();
            } finally { adjusting[0]=false; }
            refreshRef[0].run();
        });

        page.add(titleRow,filterDropdown,result);
        content.add(page);
        refreshRef[0].run();
    }

    private Div summaryCard(String title, Component... components) {
        Div card = new Div();
        card.addClassName("gp-summary-card");
        if (title != null && !title.isBlank()) {
            H3 heading = new H3(title);
            heading.addClassName("gp-summary-card-title");
            card.add(heading);
        }
        if (components != null) {
            for (Component component : components) if (component != null) card.add(component);
        }
        return card;
    }

    private Grid<LaunchRecord> summaryGrid() {
        Grid<LaunchRecord> grid = new Grid<>(LaunchRecord.class, false);
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(LaunchRecord::getMachine).setHeader(t("Máquina"));
        grid.addColumn(LaunchRecord::getSector).setHeader(t("Setor"));
        grid.addColumn(r -> formatInt(r.getCapacity24h())).setHeader(t("Capacidade 24h"));
        grid.addColumn(r -> formatInt(r.getTotalProduced())).setHeader(t("Total Produzido (pçs)"));
        grid.addColumn(r -> format(r.getScrapTotalKg())).setHeader(t("Refugo Total (kg)"));
        grid.addColumn(r -> formatInt(r.getScrapTotalPcs())).setHeader(t("Refugo Total (pçs)"));
        grid.addColumn(r -> formatInt(r.getChangeovers())).setHeader(t("Qtd. Trocas"));
        grid.addColumn(new ComponentRenderer<>(r -> oeeCell(r, 1))).setHeader("OEE (%)");
        grid.addColumn(r -> format1(r.getAvailabilityPct()) + "%").setHeader(t("Disponibilidade (%)"));
        grid.addColumn(r -> format1(r.getPerformancePct()) + "%").setHeader(t("Desempenho (%)"));
        grid.addColumn(r -> format1(r.getQualityPct()) + "%").setHeader(t("Qualidade (%)"));
        grid.setAllRowsVisible(true);
        grid.addClassName("gp-summary-grid");
        return grid;
    }

    private Grid<LaunchRecord> dailySummaryGrid() {
        Grid<LaunchRecord> grid = new Grid<>(LaunchRecord.class, false);
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(r -> Norm.br(r.getDate())).setHeader(t("Data")).setAutoWidth(true);
        grid.addColumn(LaunchRecord::getMachine).setHeader(t("Máquina")).setFlexGrow(2);
        grid.addColumn(new ComponentRenderer<>(r -> summaryCompactCell(r.getProduct()))).setHeader(t("Código Produto")).setFlexGrow(2);
        grid.addColumn(new ComponentRenderer<>(r -> summaryCompactCell(r.getOrderNumber()))).setHeader(t("Nº da OP")).setAutoWidth(true);
        grid.addColumn(r -> formatInt(r.getTotalProduced())).setHeader(t("Total Produzido")).setAutoWidth(true);
        grid.addColumn(r -> format(r.getScrapTotalKg())).setHeader(t("Refugo (kg)")).setAutoWidth(true);
        grid.addColumn(r -> formatInt(r.getScrapTotalPcs())).setHeader(t("Refugo (pçs)")).setAutoWidth(true);
        grid.addColumn(r -> format1(r.getScrapPct()) + "%").setHeader(t("Refugo (%)")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(r -> oeeCell(r, 1))).setHeader("OEE").setAutoWidth(true);
        grid.addColumn(r -> formatInt(r.getLaunchCount())).setHeader(t("Lançamentos")).setAutoWidth(true);
        grid.setAllRowsVisible(true);
        grid.addClassNames("gp-summary-grid", "gp-summary-day-grid-v083");
        return grid;
    }

    private Component summaryCompactCell(String consolidated) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (consolidated != null) {
            for (String item : consolidated.split("\\s*/\\s*")) {
                String clean = item == null ? "" : item.trim();
                if (!clean.isBlank()) unique.add(clean);
            }
        }
        if (unique.isEmpty()) return new Span("—");
        List<String> items = new ArrayList<>(unique);
        Span value = new Span(items.get(0) + (items.size() > 1 ? "..." : ""));
        value.addClassName("gp-summary-compact-value-v082");
        if (items.size() > 1) {
            String all = String.join("\n", items);
            value.getElement().setAttribute("tabindex", "0");
            value.getElement().setAttribute("aria-label", all);
            value.getElement().setAttribute("data-gp-tooltip", "true");
            Tooltip.forComponent(value)
                    .withText(all)
                    .withPosition(Tooltip.TooltipPosition.TOP)
                    .withHoverDelay(150);
        }
        return value;
    }

    private Component fullTextCell(String text) {
        String full = Norm.text(text);
        if (full.isBlank()) return new Span("—");
        Span value = new Span(full);
        value.addClassName("gp-full-text-cell-v110");
        value.getElement().setAttribute("tabindex", "0");
        value.getElement().setAttribute("aria-label", full);
        value.addAttachListener(e -> value.getElement().executeJs("""
            (() => {
                if (this.__gpTruncatedTitleV111) return;
                this.__gpTruncatedTitleV111 = true;
                this.addEventListener('pointerenter', () => {
                    const clipped = this.scrollWidth > this.clientWidth + 1 ||
                                    this.scrollHeight > this.clientHeight + 1;
                    if (clipped) this.setAttribute('title', this.innerText || this.textContent || '');
                    else this.removeAttribute('title');
                });
            })();
        """));
        return value;
    }

    private Component launchOrderCell(LaunchRecord record) {
        List<String> ops = extractOps(record == null ? "" : record.getOrderNumber());
        if (ops.isEmpty()) return new Span("—");
        if (ops.size() == 1) return new Span(ops.get(0));

        List<Integer> totals = List.of();
        String detail = record.getProductionDetail();
        if (detail != null && !detail.isBlank()) {
            try {
                List<String> detailOps = extractJsonStringArray(detail, "ops");
                if (detailOps.equals(ops)) totals = extractJsonIntArray(detail, "totais");
            } catch (Exception ignored) { }
        }

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < ops.size(); i++) {
            String quantity = i < totals.size()
                    ? formatInt(Math.max(0, totals.get(i))) + " " + t("pçs")
                    : "—";
            lines.add(ops.get(i) + " · " + quantity);
        }

        Span value = new Span(ops.get(0) + "...");
        value.addClassName("gp-launch-order-compact-v083");
        String all = String.join("\n", lines);
        value.getElement().setAttribute("tabindex", "0");
        value.getElement().setAttribute("aria-label", all);
        value.getElement().setAttribute("data-gp-tooltip", "true");
        Tooltip.forComponent(value)
                .withText(all)
                .withPosition(Tooltip.TooltipPosition.TOP)
                .withHoverDelay(150);
        return value;
    }

    private String launchProductMetadataText(LaunchRecord record) {
        if (record == null) return "";
        List<String> parts = new ArrayList<>();
        String code = Norm.text(record.getProduct());
        String client = Norm.text(record.getClientErp());
        String description = Norm.text(record.getDescriptionErp());
        if (!code.isBlank()) parts.add(code);
        if (!client.isBlank()) parts.add(client);
        if (!description.isBlank()) parts.add(description);
        return String.join(" · ", parts);
    }

    private Component launchProductCell(LaunchRecord record) {
        String full = launchProductMetadataText(record);
        if (full.isBlank()) return new Span("—");

        String code = Norm.text(record.getProduct());
        String compact = code.isBlank() ? full : code + (full.equals(code) ? "" : "...");
        Span value = new Span(compact);
        value.addClassName("gp-launch-product-description-v094");
        value.getElement().setAttribute("tabindex", "0");
        value.getElement().setAttribute("aria-label", full);
        value.getElement().setAttribute("data-gp-tooltip", "true");
        Tooltip.forComponent(value)
                .withText(full)
                .withPosition(Tooltip.TooltipPosition.TOP)
                .withHoverDelay(150);
        return value;
    }

    private boolean hasDataInShift(LaunchRecord record, String shift) {
        if (record == null || shift == null || shift.isBlank()) return true;
        return switch (shift.trim().toUpperCase(Locale.ROOT)) {
            case "A" -> record.getShiftA() > 0 || record.getScrapAKg() > 0;
            case "B" -> record.getShiftB() > 0 || record.getScrapBKg() > 0;
            case "C" -> record.getShiftC() > 0 || record.getScrapCKg() > 0;
            default -> true;
        };
    }

    private List<LaunchRecord> summaryRowsForShifts(List<LaunchRecord> source, Set<String> shifts) {
        if (source == null || source.isEmpty()) return List.of();
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        if (shifts != null) {
            for (String shift : List.of("A", "B", "C")) {
                if (shifts.stream().anyMatch(value -> shift.equalsIgnoreCase(value))) selected.add(shift);
            }
        }
        if (selected.isEmpty()) return source;
        return source.stream()
                .filter(record -> selected.stream().anyMatch(shift -> hasDataInShift(record, shift)))
                .map(record -> summaryRecordForShifts(record, selected))
                .toList();
    }

    private LaunchRecord summaryRecordForShifts(LaunchRecord original, Set<String> shifts) {
        LinkedHashSet<String> selected = shifts == null ? new LinkedHashSet<>() : new LinkedHashSet<>(shifts);
        if (selected.isEmpty()) return original.copy();

        LaunchRecord record = original.copy();
        int shiftA = selected.contains("A") ? Math.max(0, original.getShiftA()) : 0;
        int shiftB = selected.contains("B") ? Math.max(0, original.getShiftB()) : 0;
        int shiftC = selected.contains("C") ? Math.max(0, original.getShiftC()) : 0;
        double scrapA = selected.contains("A") ? Math.max(0, original.getScrapAKg()) : 0;
        double scrapB = selected.contains("B") ? Math.max(0, original.getScrapBKg()) : 0;
        double scrapC = selected.contains("C") ? Math.max(0, original.getScrapCKg()) : 0;

        record.setShiftA(shiftA);
        record.setShiftB(shiftB);
        record.setShiftC(shiftC);
        record.setTotalProduced(shiftA + shiftB + shiftC);
        record.setScrapAKg(scrapA);
        record.setScrapBKg(scrapB);
        record.setScrapCKg(scrapC);
        record.setScrapTotalKg(Norm.round(scrapA + scrapB + scrapC, 3));

        int scrapPcs = 0;
        if (record.getUnitWeightG() > 0) {
            scrapPcs = (int) Math.round(record.getScrapTotalKg() * 1000.0 / record.getUnitWeightG());
        } else if (original.getScrapTotalKg() > 0 && original.getScrapTotalPcs() > 0) {
            scrapPcs = (int) Math.round(original.getScrapTotalPcs()
                    * record.getScrapTotalKg() / original.getScrapTotalKg());
        }
        record.setScrapTotalPcs(Math.max(0, scrapPcs));
        int processed = record.getTotalProduced() + record.getScrapTotalPcs();
        record.setScrapPct(processed > 0
                ? Norm.round(record.getScrapTotalPcs() * 100.0 / processed, 2)
                : 0.0);
        return record;
    }

    private List<LaunchRecord> summaryRowsForShift(List<LaunchRecord> source, String shift) {
        if (shift == null || shift.isBlank()) return source == null ? List.of() : source;
        return summaryRowsForShifts(source, Set.of(shift));
    }

    private LaunchRecord summaryRecordForShift(LaunchRecord original, String shift) {
        if (shift == null || shift.isBlank()) return original.copy();
        return summaryRecordForShifts(original, Set.of(shift.trim().toUpperCase(Locale.ROOT)));
    }

    private List<LaunchRecord> summarizeDaily(List<LaunchRecord> rows) {
        Map<String, List<LaunchRecord>> groups = rows.stream().collect(Collectors.groupingBy(
                r -> r.getMachine() + "¦" + r.getSector(), LinkedHashMap::new, Collectors.toList()));
        List<LaunchRecord> out = new ArrayList<>();
        for (List<LaunchRecord> g : groups.values()) {
            if (g.isEmpty()) continue;
            g = g.stream().sorted(Comparator.comparingLong(LaunchRecord::getId)).toList();
            LaunchRecord z = new LaunchRecord();
            z.setId(g.stream().mapToLong(LaunchRecord::getId).max().orElse(0));
            z.setDate(g.get(0).getDate());
            z.setMachine(g.get(0).getMachine());
            z.setSector(g.get(0).getSector());
            z.setProduct(combineUnique(g.stream().map(LaunchRecord::getProduct).toList(), " / "));
            z.setOrderNumber(combineOrders(g.stream().map(LaunchRecord::getOrderNumber).toList()));
            z.setScheduledHours(g.stream().mapToDouble(LaunchRecord::getScheduledHours).sum());
            z.setCapacity24h(g.stream().mapToInt(LaunchRecord::getCapacity24h).max().orElse(0));
            z.setShiftA(g.stream().mapToInt(LaunchRecord::getShiftA).sum());
            z.setShiftB(g.stream().mapToInt(LaunchRecord::getShiftB).sum());
            z.setShiftC(g.stream().mapToInt(LaunchRecord::getShiftC).sum());
            z.setTotalProduced(g.stream().mapToInt(LaunchRecord::getTotalProduced).sum());
            z.setScrapAKg(g.stream().mapToDouble(LaunchRecord::getScrapAKg).sum());
            z.setScrapBKg(g.stream().mapToDouble(LaunchRecord::getScrapBKg).sum());
            z.setScrapCKg(g.stream().mapToDouble(LaunchRecord::getScrapCKg).sum());
            z.setScrapTotalKg(g.stream().mapToDouble(LaunchRecord::getScrapTotalKg).sum());
            z.setScrapTotalPcs(g.stream().mapToInt(LaunchRecord::getScrapTotalPcs).sum());
            int processed = Math.max(0,z.getTotalProduced()) + Math.max(0,z.getScrapTotalPcs());
            z.setScrapPct(processed>0 ? Norm.round(z.getScrapTotalPcs()*100.0/processed,2) : 0.0);
            z.setChangeovers(g.stream().mapToInt(LaunchRecord::getChangeovers).sum());
            z.setSetupHours(g.stream().mapToDouble(LaunchRecord::getSetupHours).sum());
            z.setBreakdownHours(g.stream().mapToDouble(LaunchRecord::getBreakdownHours).sum());
            z.setProblem(combineObservations(g));
            // OEE já foi recalculado pela mesma máquina/dia; todos os registros do grupo carregam o mesmo indicador.
            z.setOeePct(avg(g, "oee"));
            z.setAvailabilityPct(avg(g, "availability"));
            z.setPerformancePct(avg(g, "performance"));
            z.setQualityPct(avg(g, "quality"));
            z.setLaunchCount(g.size());
            out.add(z);
        }
        out.sort(Comparator.comparing(LaunchRecord::getMachine, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private String combineUnique(List<String> values, String separator) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for(String v: values){ String x=v==null?"":v.trim(); if(!x.isBlank()&&!x.equalsIgnoreCase("nan")&&!x.equalsIgnoreCase("none")&&!x.equals("-")) unique.add(x); }
        return String.join(separator, unique);
    }

    private String combineOrders(List<String> values) {
        LinkedHashSet<String> ops = new LinkedHashSet<>();
        for(String value: values) for(String op: extractOps(value)) if(!op.isBlank()) ops.add(op);
        return String.join("/", ops);
    }

    private double avg(List<LaunchRecord> rows, String metric) {
        return Norm.round(rows.stream().mapToDouble(r -> switch (metric) {
            case "availability" -> r.getAvailabilityPct();
            case "performance" -> r.getPerformancePct();
            case "quality" -> r.getQualityPct();
            default -> r.getOeePct();
        }).average().orElse(0), 2);
    }

    private void renderMonth() {
        content.removeAll();
        Div page=new Div();
        page.addClassNames("gp-summary-page","gp-summary-month-page","gp-original-summary");
        H2 title=new H2(t("Resumo Mensal de Eficiência")); title.addClassName("gp-section-title");
        Button filter = searchFilterButton();
        filter.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        filter.addClassNames("gp-filter-button", "gp-summary-filter-trigger-v047");
        filter.setAriaLabel(t("Filtros"));
        filter.setTooltipText(t("Filtros")).withPosition(Tooltip.TooltipPosition.TOP);
        Div titleRow = new Div(title, filter);
        titleRow.addClassNames("gp-title-row", "gp-summary-title-row-v047");

        LocalDate[] bounds=cachedLaunchBounds();
        YearMonth minMonth=YearMonth.from(bounds[0]), maxMonth=YearMonth.from(bounds[1]);
        List<YearMonth> months=new ArrayList<>();
        for(YearMonth cursor=maxMonth;!cursor.isBefore(minMonth);cursor=cursor.minusMonths(1))months.add(cursor);
        if(summaryMonth==null||summaryMonth.isBefore(minMonth)||summaryMonth.isAfter(maxMonth))summaryMonth=maxMonth;

        Select<YearMonth> month=new Select<>();
        month.setLabel(t("Selecione o Mês de Análise")); month.setItems(months);
        month.setItemLabelGenerator(m->m.format(DateTimeFormatter.ofPattern("MMMM/yyyy",locale()))); month.setValue(summaryMonth);
        Select<String> sector=select(t("Filtrar por Setor"),List.of(),summaryMonthSector,t("Todos"));
        sector.addClassName("gp-uppercase-sector-filter-v061");
        Div filterFields=new Div(month,sector);
        filterFields.addClassNames("gp-filter-dropdown-grid", "gp-summary-filter-fields-v047");
        Div result=new Div(); result.addClassName("gp-summary-result");

        // A estrutura pesada da aba é criada uma vez. Filtros apenas trocam os
        // dados; "Mostrar mais" atualiza somente a grade de apontamentos.
        H3 rankingTitle=new H3(t("Ranking de OEE no Mês"));rankingTitle.addClassName("gp-original-subtitle");
        Div rankingHolder=new Div();rankingHolder.addClassName("gp-month-ranking-holder-v054");
        H3 tableTitle=new H3(t("Tabela Consolidada por Equipamento"));tableTitle.addClassName("gp-original-subtitle");
        Grid<LaunchRecord> summaryGrid=summaryGrid();
        H3 allTitle=new H3(t("Todos os Apontamentos do Mês"));allTitle.addClassName("gp-original-subtitle");
        Grid<LaunchRecord> entries=launchGrid();
        Div moreHolder=new Div();moreHolder.addClassName("gp-show-more-holder");
        result.add(rankingTitle,rankingHolder,tableTitle,summaryGrid,allTitle,entries,moreHolder);

        boolean[] adjusting={false};
        @SuppressWarnings("unchecked")
        List<LaunchRecord>[] currentMonthRows=new List[]{List.of()};
        Runnable[] refreshEntriesRef=new Runnable[1];
        refreshEntriesRef[0]=()->{
            List<LaunchRecord> rows=currentMonthRows[0];
            int limit=Math.min(Math.max(AppConfig.PAGE_SIZE,monthLimit),rows.size());
            List<LaunchRecord> visible=rows.stream().limit(limit).toList();
            entries.setItems(visible);
            configureAdaptiveGridHeight(entries,visible.size(),40,720);
            moreHolder.removeAll();
            if(limit<rows.size()){
                Button more=new Button(t("Mostrar mais"));
                more.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
                more.addClassName("gp-show-more");
                more.addClickListener(e->{monthLimit+=AppConfig.PAGE_SIZE;refreshEntriesRef[0].run();});
                moreHolder.add(more);
            }
        };
        Runnable[] refreshRef=new Runnable[1];
        refreshRef[0]=()->{
            if(adjusting[0])return;
            YearMonth ym=month.getValue(); if(ym==null)return;
            summaryMonth=ym;
            List<LaunchRecord> source=cachedLaunchData(ym.atDay(1),ym.atEndOfMonth());
            List<String> sectors=source.stream().map(LaunchRecord::getSector).filter(Objects::nonNull).filter(v->!v.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
            adjusting[0]=true;
            try{sector.setItems(sectors);if(summaryMonthSector!=null&&sectors.contains(summaryMonthSector))sector.setValue(summaryMonthSector);else{summaryMonthSector=null;sector.clear();}}finally{adjusting[0]=false;}
            updateFilterButton(filter, !Objects.equals(summaryMonth, maxMonth) || summaryMonthSector != null);
            List<LaunchRecord> filtered=source;
            if(summaryMonthSector!=null)filtered=filtered.stream().filter(r->summaryMonthSector.equals(r.getSector())).toList();
            filtered=launches.newestFirst(filtered);
            List<LaunchRecord> summary=summarizeMonthly(filtered);
            if(filtered.isEmpty()){
                currentMonthRows[0]=List.of();
                result.removeAll();
                result.add(emptyState(t("Nenhum lançamento encontrado para o mês com os filtros selecionados.")));
                return;
            }
            if(result.getChildren().noneMatch(component->component==rankingTitle)){
                result.removeAll();
                result.add(rankingTitle,rankingHolder,tableTitle,summaryGrid,allTitle,entries,moreHolder);
            }
            Map<String,Double> bars=new LinkedHashMap<>();
            summary.stream().sorted(Comparator.comparingDouble(LaunchRecord::getOeePct).reversed()).forEach(r->bars.put(r.getMachine(),r.getOeePct()));
            rankingHolder.removeAll();
            rankingHolder.add(new OeeRankingChart(bars,locale()));
            summaryGrid.setItems(summary);
            configureAdaptiveGridHeight(summaryGrid,summary.size(),30,620);
            currentMonthRows[0]=filtered;
            refreshEntriesRef[0].run();
        };
        month.addValueChangeListener(e->{if(!adjusting[0]){summaryMonth=e.getValue();summaryMonthSector=null;monthLimit=AppConfig.PAGE_SIZE;refreshRef[0].run();}});
        sector.addValueChangeListener(e->{if(!adjusting[0]){summaryMonthSector=e.getValue();monthLimit=AppConfig.PAGE_SIZE;refreshRef[0].run();}});
        activeMonthRefresh = refreshRef[0];

        Popover filterDropdown = summaryFilterDropdown(filter, filterFields, () -> {
            adjusting[0]=true;
            try {
                summaryMonth=maxMonth;
                summaryMonthSector=null;
                monthLimit=AppConfig.PAGE_SIZE;
                month.setValue(maxMonth);
                sector.clear();
            } finally { adjusting[0]=false; }
            refreshRef[0].run();
        });

        page.add(titleRow,filterDropdown,result);
        content.add(page);
        refreshRef[0].run();
    }

    private long capacityTargetByMachineDay(List<LaunchRecord> rows) {
        Map<String, Integer> caps = new LinkedHashMap<>();
        for (LaunchRecord r : rows) {
            if (r.getDate() == null || r.getMachine() == null) continue;
            String key = r.getDate() + "¦" + r.getMachine();
            caps.merge(key, Math.max(0, r.getCapacity24h()), Math::max);
        }
        return caps.values().stream().mapToLong(Integer::longValue).sum();
    }

    private void refreshMonthView(Select<YearMonth> month, Select<String> sector, Grid<LaunchRecord> summaryGrid,
                                  Grid<LaunchRecord> entriesGrid, Div chartBox, Div moreHolder) {
        YearMonth ym = month.getValue();
        if (ym == null) return;
        List<LaunchRecord> filtered = cachedLaunchData(ym.atDay(1), ym.atEndOfMonth());
        if (sector.getValue() != null) filtered = filtered.stream().filter(r -> sector.getValue().equals(r.getSector())).toList();
        filtered = launches.newestFirst(filtered);
        List<LaunchRecord> summary = summarizeMonthly(filtered);
        summaryGrid.setItems(summary);
        chartBox.removeAll();
        Map<String, Double> bars = new LinkedHashMap<>();
        summary.stream().sorted(Comparator.comparingDouble(LaunchRecord::getOeePct).reversed()).forEach(r -> bars.put(r.getMachine(), r.getOeePct()));
        chartBox.add(new OeeRankingChart(bars, locale()));
        int limit = Math.min(Math.max(AppConfig.PAGE_SIZE, monthLimit), filtered.size());
        entriesGrid.setItems(filtered.stream().limit(limit).toList());
        moreHolder.removeAll();
        if (limit < filtered.size()) {
            Button more = new Button(t("Mostrar mais"));
            more.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            more.addClassName("gp-show-more");
            more.addClickListener(e -> { monthLimit += AppConfig.PAGE_SIZE; refreshMonthView(month, sector, summaryGrid, entriesGrid, chartBox, moreHolder); });
            moreHolder.add(more);
        }
    }

    private List<LaunchRecord> summarizeMonthly(List<LaunchRecord> rows) {
        Map<String, List<LaunchRecord>> groups = rows.stream().collect(Collectors.groupingBy(
                r -> r.getMachine() + "¦" + r.getSector(), LinkedHashMap::new, Collectors.toList()));
        List<LaunchRecord> out = new ArrayList<>();
        for (List<LaunchRecord> g : groups.values()) {
            if (g.isEmpty()) continue;
            LaunchRecord z = new LaunchRecord();
            z.setMachine(g.get(0).getMachine());
            z.setSector(g.get(0).getSector());
            z.setCapacity24h(g.stream().mapToInt(LaunchRecord::getCapacity24h).max().orElse(0));
            z.setTotalProduced(g.stream().mapToInt(LaunchRecord::getTotalProduced).sum());
            z.setScrapTotalKg(g.stream().mapToDouble(LaunchRecord::getScrapTotalKg).sum());
            z.setScrapTotalPcs(g.stream().mapToInt(LaunchRecord::getScrapTotalPcs).sum());
            z.setChangeovers(g.stream().mapToInt(LaunchRecord::getChangeovers).sum());
            z.setProblem(combineObservations(g));

            Map<LocalDate, LaunchRecord> daily = new LinkedHashMap<>();
            for (LaunchRecord r : g) daily.putIfAbsent(r.getDate(), r);
            List<LaunchRecord> indicators = new ArrayList<>(daily.values());
            z.setOeePct(avg(indicators, "oee"));
            z.setAvailabilityPct(avg(indicators, "availability"));
            z.setPerformancePct(avg(indicators, "performance"));
            z.setQualityPct(avg(indicators, "quality"));
            out.add(z);
        }
        out.sort(Comparator.comparingDouble(LaunchRecord::getOeePct).reversed());
        return out;
    }

    private void renderScrap() {
        content.removeAll();
        H2 title = new H2(t("Análise de Refugo"));
        title.addClassName("gp-section-title");
        Div titleRow = new Div(title);
        titleRow.addClassNames("gp-title-row", "gp-title-row-static");

        TextField search = new TextField(t("Pesquisar refugo"));
        search.setPlaceholder(t("Ordem, produto ou descrição"));
        search.setClearButtonVisible(true);
        search.setValue(scrapSearch);
        Button filter = searchFilterButton();
        filter.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        filter.addClassName("gp-filter-button");
        filter.setAriaLabel(t("Filtros"));
        filter.setTooltipText(t("Filtros")).withPosition(Tooltip.TooltipPosition.TOP);
        Div toolbar = new Div(search, filter);
        toolbar.addClassNames("gp-toolbar", "gp-tab-controls", "gp-search-filter-toolbar-v044", "gp-refugo-search-toolbar-v071", "gp-refugo-search-toolbar-v072", "gp-refugo-search-toolbar-v073", "gp-refugo-search-toolbar-v074");

        Div kpis = new Div();
        kpis.setId("scrap-kpis");
        kpis.addClassNames("gp-kpis", "gp-refugo-kpis-v056");

        Map<Tab, String> dimensions = new LinkedHashMap<>();
        List<Tab> tabsList = new ArrayList<>();
        Tab yearlyComparisonTab = new Tab(t("Anual"));
        dimensions.put(yearlyComparisonTab, "Comparativo Anual");
        Tab monthlyComparisonTab = new Tab(t("Mensal"));
        dimensions.put(monthlyComparisonTab, "Comparativo Mensal");
        monthlyComparisonTab.setVisible(scrapHasMonthlyComparison());
        yearlyComparisonTab.setVisible(scrapHasYearlyComparison());
        tabsList.add(yearlyComparisonTab);
        tabsList.add(monthlyComparisonTab);
        tabsList.add(dimensionTab(dimensions, "Setor"));
        tabsList.add(dimensionTab(dimensions, "Máquina"));
        tabsList.add(dimensionTab(dimensions, "Turno"));
        tabsList.add(dimensionTab(dimensions, "Descrição"));
        tabsList.add(dimensionTab(dimensions, "Motivo"));
        Tabs dims = new Tabs(tabsList.toArray(Tab[]::new));
        dims.addClassName("gp-inner-tabs");
        dims.addAttachListener(e -> dims.getElement().executeJs("""
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
        for (var e : dimensions.entrySet()) {
            if (Objects.equals(e.getValue(), scrapActiveDimension)) {
                dims.setSelectedTab(e.getKey());
                break;
            }
        }
        if (dimensions.get(dims.getSelectedTab()) == null) scrapActiveDimension = "Setor";

        Div chart = new Div();
        chart.setId("scrap-chart");
        chart.addClassName("gp-refugo-analysis");
        Div details = new Div();
        details.setId("scrap-details");
        details.addClassName("gp-refugo-details");

        Div recent = new Div();
        recent.setId("scrap-recent");
        recent.addClassName("gp-refugo-recent");

        Runnable refreshSelected = () -> {
            monthlyComparisonTab.setVisible(scrapHasMonthlyComparison());
            yearlyComparisonTab.setVisible(scrapHasYearlyComparison());
            String selected = dimensions.getOrDefault(dims.getSelectedTab(), "Setor");
            if (("Comparativo Mensal".equals(selected) && !monthlyComparisonTab.isVisible())
                    || ("Comparativo Anual".equals(selected) && !yearlyComparisonTab.isVisible())) {
                Tab sectorTab = dimensions.entrySet().stream()
                        .filter(entry -> "Setor".equals(entry.getValue()))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);
                if (sectorTab != null) dims.setSelectedTab(sectorTab);
                selected = "Setor";
            }
            scrapActiveDimension = selected;
            refreshScrap(selected);
        };
        Popover filterDropdown = scrapFilterDropdown(filter, refreshSelected);
        Div page = new Div(titleRow, toolbar, filterDropdown, kpis, dims, chart, details, recent);
        page.addClassNames("gp-refugo-page", "gp-refugo-page-v065", "gp-refugo-page-v066", "gp-refugo-page-v067", "gp-refugo-page-v068", "gp-refugo-page-v069", "gp-refugo-page-v070");
        content.add(page);

        search.addValueChangeListener(e -> {
            scrapSearch = e.getValue();
            resetScrapInteraction();
            refreshSelected.run();
        });
        dims.addSelectedChangeListener(e -> {
            scrapActiveDimension = dimensions.getOrDefault(e.getSelectedTab(), "Setor");
            scrapShowLaunches = false;
            refreshSelected.run();
            dims.getElement().executeJs("""
                const restore=()=>{
                  const y=Number(window.__gpScrapScrollV065);
                  if(Number.isFinite(y)) window.scrollTo(window.scrollX,y);
                };
                restore();
                requestAnimationFrame(()=>{restore();requestAnimationFrame(restore);});
                setTimeout(restore,60);
            """);
        });
        refreshSelected.run();
    }

    private Tab dimensionTab(Map<Tab, String> dimensions, String canonical) {
        Tab tab = new Tab(t(canonical));
        dimensions.put(tab, canonical);
        return tab;
    }

    private boolean scrapHasMonthlyComparison() {
        return currentScrapRows().stream()
                .map(r -> YearMonth.from(r.productiveDate()))
                .distinct()
                .limit(2)
                .count() >= 2;
    }

    private boolean scrapHasYearlyComparison() {
        return currentScrapRows().stream()
                .map(r -> r.productiveDate().getYear())
                .distinct()
                .limit(2)
                .count() >= 2;
    }

    private List<RefugoRecord> currentScrapRows() {
        List<RefugoRecord> base = cachedScrapData(scrapStart, scrapEnd);
        List<RefugoRecord> filtered = scraps.filter(base, scrapSearch, scrapSectors, scrapOrders, scrapMachines, scrapProducts, scrapDescriptions, scrapClients, scrapShifts, scrapOperators, scrapMotives);
        if (!scrapExcludedIds.isEmpty()) filtered = filtered.stream().filter(r -> !scrapExcludedIds.contains(r.analysisId())).toList();
        return filtered;
    }

    private void resetScrapInteraction() {
        scrapPages.clear();
        scrapSelectedDimension = "";
        scrapSelectedKey = "";
        scrapShowLaunches = false;
    }

    private void refreshScrap(String dimension) {
        List<RefugoRecord> rows = currentScrapRows();
        Div chart = (Div) byId("scrap-chart");
        if (chart != null) {
            chart.removeAll();
            if (rows.isEmpty()) {
                chart.add(emptyState(t("Nenhum dado encontrado para os filtros selecionados.")));
            } else if ("Comparativo Mensal".equals(dimension)) {
                renderScrapComparison(chart, rows, true);
            } else if ("Comparativo Anual".equals(dimension)) {
                renderScrapComparison(chart, rows, false);
            } else {
                renderScrapDimension(chart, rows, dimension);
            }
        }

        refreshScrapSelectionPanels(rows, dimension);

        Div recent = (Div) byId("scrap-recent");
        if (recent != null) {
            recent.removeAll();
            LocalDate productiveToday = Norm.productiveToday();
            if (Objects.equals(scrapStart, productiveToday) && Objects.equals(scrapEnd, productiveToday)) {
                renderRecentScrapLaunches(recent, rows);
            }
        }
    }

    private void renderScrapKpis(Div kpis, List<RefugoRecord> rows, String dimension) {
        kpis.removeAll();
        double total = scraps.totalKg(rows);
        String selectedPct = "—";
        if (Objects.equals(scrapSelectedDimension, dimension) && !scrapSelectedKey.isBlank() && total > 0) {
            double selected = rows.stream().filter(r -> scrapMatches(r, dimension, scrapSelectedKey)).mapToDouble(RefugoRecord::scrapKg).sum();
            if (selected > 0) selectedPct = format1(selected * 100.0 / total) + "%";
        }
        double totalPct = total > 0 ? 100.0 : 0.0;
        Div totalKpi = kpiWithCaption(
                t("Total Refugo"),
                format(total) + " kg",
                t("{percentual}% do total").replace("{percentual}", format1(totalPct))
        );

        LocalDate productiveToday = Norm.productiveToday();
        String periodValue;
        String periodCaption = null;
        if (Objects.equals(scrapStart, productiveToday) && Objects.equals(scrapEnd, productiveToday)) {
            periodValue = t("Hoje");
            periodCaption = Norm.br(productiveToday);
        } else if (Objects.equals(scrapStart, scrapEnd)) {
            periodValue = Norm.br(scrapStart);
        } else {
            periodValue = Norm.br(scrapStart) + " – " + Norm.br(scrapEnd);
        }

        kpis.add(
                totalKpi,
                kpi(t("Item Selecionado (%)"), selectedPct),
                kpi(t("Ordens Afetadas"), formatInt(scraps.orders(rows))),
                kpi(t("Total de Lançamentos"), formatInt(rows.size())),
                periodCaption == null
                        ? kpi(t("Período"), periodValue)
                        : kpiWithCaption(t("Período"), periodValue, periodCaption)
        );
    }

    private void renderScrapDimension(Div host, List<RefugoRecord> rows, String dimension) {
        Map<String, Double> aggregate = aggregateScrap(rows, dimension);
        if (aggregate.isEmpty()) {
            host.add(emptyState(t("Nenhum dado encontrado para os filtros selecionados.")));
            return;
        }
        List<Map.Entry<String, Double>> all = new ArrayList<>(aggregate.entrySet());
        int pageSize = 15;
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) pageSize));
        int page = Math.max(1, Math.min(scrapPages.getOrDefault(dimension, 1), totalPages));
        scrapPages.put(dimension, page);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, all.size());
        Map<String, Double> pageValues = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (int i = start; i < end; i++) {
            var e = all.get(i);
            pageValues.put(e.getKey(), e.getValue());
            labels.put(e.getKey(), scrapDisplayLabel(e.getKey(), dimension));
        }

        Div chartLine = new Div();
        chartLine.addClassName("gp-refugo-chart-line");
        String selected = Objects.equals(scrapSelectedDimension, dimension) ? scrapSelectedKey : null;
        InteractiveBarChart barChart = new InteractiveBarChart(
                t("Análise por " + dimension), pageValues, labels, selected, locale(), key -> {
                    toggleScrapSelectionAndRefreshKpis(dimension, key);
                }, key -> {
                    selectScrapForContextMenu(dimension, key);
                });
        if ("Setor".equals(dimension)) {
            barChart.addClassNames("gp-refugo-sector-chart-v064", "gp-refugo-sector-chart-v068", "gp-refugo-sector-chart-v069");
        }
        alignScrapChartTitleV070(barChart);
        attachScrapContextMenu(barChart, rows, dimension);
        chartLine.add(barChart);
        host.add(chartLine);
        host.add(scrapPagination(dimension, page, totalPages));
        if ("Setor".equals(dimension)) renderTopReasonsBySector(host, rows);
    }

    private Map<String, Double> aggregateScrap(List<RefugoRecord> rows, String dimension) {
        Map<String, Double> raw = new LinkedHashMap<>();
        for (RefugoRecord r : rows) raw.merge(scrapKey(r, dimension), r.scrapKg(), Double::sum);
        return raw.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Norm.round(e.getValue(), 3), (a, b) -> a, LinkedHashMap::new));
    }

    private String scrapKey(RefugoRecord r, String dimension) {
        return switch (dimension) {
            case "Máquina" -> nonBlank(r.machine());
            case "Turno" -> nonBlank(r.shift());
            case "Descrição" -> nonBlank(r.description());
            case "Motivo" -> Norm.scrapMotiveUid(r.product(), r.motive());
            case "Comparativo Mensal" -> YearMonth.from(r.productiveDate()).toString();
            case "Comparativo Anual" -> String.valueOf(r.productiveDate().getYear());
            default -> uppercaseSector(nonBlank(r.sector()));
        };
    }

    private void alignScrapChartTitleV070(InteractiveBarChart chart) {
        chart.addClassName("gp-refugo-chart-title-aligned-v070");
        chart.addAttachListener(e -> chart.getElement().executeJs("""
            const apply=()=>{
              const title=this.querySelector('.gp-refugo-chart-title');
              if(title){
                title.style.setProperty('left','48px','important');
                title.style.setProperty('top','0px','important');
              }
            };
            apply();
            requestAnimationFrame(apply);
        """));
    }

    private String scrapDisplayLabel(String key, String dimension) {
        if (key == null) return t("NÃO INFORMADO");
        if ("Motivo".equals(dimension)) {
            String[] parts = key.split("¦", -1);
            return parts.length >= 3 ? t(parts[2]) : t(key);
        }
        if ("Comparativo Mensal".equals(dimension)) {
            try { return YearMonth.parse(key).format(DateTimeFormatter.ofPattern("MMM/yyyy", locale())); } catch (Exception ignored) { }
        }
        return t(key);
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? "NÃO INFORMADO" : value;
    }

    private boolean scrapMatches(RefugoRecord r, String dimension, String key) {
        return Objects.equals(scrapKey(r, dimension), key);
    }

    private void toggleScrapSelection(String dimension, String key) {
        if (Objects.equals(scrapSelectedDimension, dimension) && Objects.equals(scrapSelectedKey, key)) {
            scrapSelectedDimension = "";
            scrapSelectedKey = "";
            scrapShowLaunches = false;
        } else {
            scrapSelectedDimension = dimension;
            scrapSelectedKey = key == null ? "" : key;
            scrapShowLaunches = false;
        }
    }

    private void selectScrapForContextMenu(String dimension, String key) {
        scrapSelectedDimension = dimension;
        scrapSelectedKey = key == null ? "" : key;
        scrapShowLaunches = false;
        refreshScrapSelectionPanels(currentScrapRows(), dimension);
    }

    private void toggleScrapSelectionAndRefreshKpis(String dimension, String key) {
        toggleScrapSelection(dimension, key);
        refreshScrapSelectionPanels(currentScrapRows(), dimension);
    }

    private void refreshScrapSelectionPanels(List<RefugoRecord> rows, String dimension) {
        Div kpis = (Div) byId("scrap-kpis");
        if (kpis != null) renderScrapKpis(kpis, rows, dimension);

        Div details = (Div) byId("scrap-details");
        if (details == null) return;
        details.removeAll();

        boolean selected = Objects.equals(scrapSelectedDimension, dimension)
                && !scrapSelectedKey.isBlank();
        if (selected && "Descrição".equals(dimension)) {
            renderDescriptionDetails(details, rows, scrapSelectedKey);
        }
        if (selected && scrapShowLaunches) {
            renderScrapLaunches(details, rows, dimension, scrapSelectedKey);
        }
    }

    private void attachScrapContextMenu(Component target, List<RefugoRecord> rows, String dimension) {
        ContextMenu menu = new ContextMenu();
        menu.setTarget(target);
        menu.setOpenOnClick(false);
        menu.getElement().setProperty("selector", ".gp-refugo-bar-column");

        if (user != null && user.isAdmin()) {
            MenuItem transfer = menu.addItem(t("Enviar para outro setor"));
            List<String> sectors = catalog.sectors().stream()
                    .filter(Objects::nonNull)
                    .map(MainView::uppercaseSector)
                    .filter(v -> !v.isBlank())
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            for (String sector : sectors) {
                transfer.getSubMenu().addItem(sector, e -> transferSelectedScrapToSector(dimension, sector));
            }
            transfer.setEnabled(!sectors.isEmpty());
        }

        MenuItem exclude = menu.addItem(t("Excluir item"), e -> {
            if (!Objects.equals(scrapSelectedDimension, dimension) || scrapSelectedKey.isBlank()) {
                notify(t("Selecione um item no gráfico"));
                return;
            }
            rows.stream().filter(r -> scrapMatches(r, dimension, scrapSelectedKey)).map(RefugoRecord::analysisId).forEach(scrapExcludedIds::add);
            scrapSelectedDimension = "";
            scrapSelectedKey = "";
            scrapShowLaunches = false;
            refreshScrap(dimension);
        });
        MenuItem view = menu.addItem(t("Ver lançamentos"), e -> {
            if (!Objects.equals(scrapSelectedDimension, dimension) || scrapSelectedKey.isBlank()) {
                notify(t("Selecione um item no gráfico"));
                return;
            }
            scrapShowLaunches = true;
            refreshScrap(dimension);
        });
        if (!scrapExcludedIds.isEmpty()) {
            menu.addItem(t("Restaurar excluídos") + " (" + scrapExcludedIds.size() + ")", e -> {
                scrapExcludedIds.clear();
                resetScrapInteraction();
                refreshScrap(dimension);
            });
        }
    }

    private void transferSelectedScrapToSector(String dimension, String destinationSector) {
        if (user == null || !user.isAdmin()) {
            notify(t("Somente um Administrador pode enviar lançamentos para outro setor."));
            return;
        }
        if (!Objects.equals(scrapSelectedDimension, dimension) || scrapSelectedKey.isBlank()) {
            notify(t("Selecione um item no gráfico"));
            return;
        }

        List<RefugoRecord> selectedRows = currentScrapRows().stream()
                .filter(r -> scrapMatches(r, dimension, scrapSelectedKey))
                .toList();
        if (selectedRows.isEmpty()) {
            notify(t("Selecione um item no gráfico"));
            return;
        }

        try {
            int changed = scraps.reassignSector(selectedRows, destinationSector, user);
            invalidateDataCaches();
            resetScrapInteraction();
            refreshScrap(dimension);
            notify(formatInt(changed) + " " + t(changed == 1 ? "lançamento enviado." : "lançamentos enviados."));
        } catch (RuntimeException ex) {
            notify(ex.getMessage() == null ? t("Não foi possível alterar o setor.") : ex.getMessage());
        }
    }

    private static String uppercaseSector(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private Component scrapPagination(String dimension, int page, int totalPages) {
        Div holder = new Div();
        holder.addClassName("gp-refugo-pagination");
        if (totalPages <= 1) return holder;
        Button prev = new Button(t("← Anterior"));
        prev.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        prev.setEnabled(page > 1);
        prev.addClickListener(e -> { scrapPages.put(dimension, page - 1); scrapSelectedDimension = ""; scrapSelectedKey = ""; scrapShowLaunches = false; refreshScrap(dimension); });
        Span label = new Span(t("Página") + " " + page + " " + t("de") + " " + totalPages);
        Button next = new Button(t("Próximo →"));
        next.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        next.setEnabled(page < totalPages);
        next.addClickListener(e -> { scrapPages.put(dimension, page + 1); scrapSelectedDimension = ""; scrapSelectedKey = ""; scrapShowLaunches = false; refreshScrap(dimension); });
        holder.add(prev, label, next);
        return holder;
    }

    private void renderScrapComparison(Div host, List<RefugoRecord> rows, boolean monthly) {
        String dimension = monthly ? "Comparativo Mensal" : "Comparativo Anual";
        Map<String, Double> totals = new LinkedHashMap<>();
        for (RefugoRecord r : rows) totals.merge(scrapKey(r, dimension), r.scrapKg(), Double::sum);
        totals = totals.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Norm.round(e.getValue(), 3), (a, b) -> a, LinkedHashMap::new));
        if (totals.size() < 2) {
            host.add(emptyState(t("O comparativo requer pelo menos 2 períodos nos dados filtrados.")));
            return;
        }
        Map<String, String> labels = new LinkedHashMap<>();
        totals.keySet().forEach(k -> labels.put(k, scrapDisplayLabel(k, dimension)));
        Div line = new Div();
        line.addClassName("gp-refugo-chart-line");
        String selected = monthly && Objects.equals(scrapSelectedDimension, dimension) ? scrapSelectedKey : null;
        InteractiveBarChart chart = new InteractiveBarChart(
                t(monthly ? "Análise por mês" : "Análise por ano"),
                totals, labels, selected, locale(), monthly ? key -> {
                    toggleScrapSelectionAndRefreshKpis(dimension, key);
                } : null, monthly ? key -> {
                    selectScrapForContextMenu(dimension, key);
                } : null);
        alignScrapChartTitleV070(chart);
        if (monthly) attachScrapContextMenu(chart, rows, dimension);
        line.add(chart);
        host.add(line);

        if (monthly) {
            List<Map.Entry<String, Double>> ordered = new ArrayList<>(totals.entrySet());
            var last = ordered.get(ordered.size() - 1);
            var previous = ordered.get(ordered.size() - 2);
            var min = ordered.stream().min(Map.Entry.comparingByValue()).orElse(last);
            var max = ordered.stream().max(Map.Entry.comparingByValue()).orElse(last);
            double variation = previous.getValue() == 0 ? 0 : (last.getValue() - previous.getValue()) / previous.getValue() * 100.0;
            Div metrics = new Div(
                    kpi(t("Último mês"), format(last.getValue()) + " kg · " + signed1(variation) + "%"),
                    kpi(t("Menor refugo"), labels.get(min.getKey()) + " · " + format(min.getValue()) + " kg"),
                    kpi(t("Maior refugo"), labels.get(max.getKey()) + " · " + format(max.getValue()) + " kg")
            );
            metrics.addClassNames("gp-kpis", "gp-comparison-kpis");
            host.add(metrics);
        }
        renderTopReasonsByPeriod(host, rows, monthly);
    }

    private void renderTopReasonsBySector(Div host, List<RefugoRecord> rows) {
        String selectedSector = null;
        if (Objects.equals(scrapSelectedDimension, "Setor") && !scrapSelectedKey.isBlank()) selectedSector = scrapSelectedKey;
        else if (scrapSectors.size() == 1) selectedSector = scrapSectors.iterator().next();
        final String sector = selectedSector;
        List<RefugoRecord> scope = sector == null ? rows : rows.stream().filter(r -> sector.equalsIgnoreCase(r.sector())).toList();
        Map<String, Double> ranking = aggregateScrap(scope, "Motivo");
        H3 title = new H3(t("Top 5 Motivos"));
        title.addClassName("gp-subsection-title");
        Span caption = new Span(sector == null
                ? t("Ranking geral • valor em Kg e participação no total do recorte atual")
                : t("Ranking do setor") + " " + sector + " • " + t("valor em Kg e participação no total do setor"));
        caption.addClassName("gp-caption");
        host.add(title, caption, rankingTable(ranking, scope.stream().mapToDouble(RefugoRecord::scrapKg).sum(), sector));
    }

    private Component rankingTable(Map<String, Double> ranking, double total, String sector) {
        Div table = new Div();
        table.addClassName("gp-ranking-table");
        int rank = 1;
        for (var e : ranking.entrySet()) {
            if (rank > 5) break;
            String[] parts = e.getKey().split("¦", -1);
            String code = parts.length > 0 ? parts[0] : "";
            String sec = parts.length > 1 ? parts[1] : "";
            String motive = parts.length > 2 ? parts[2] : e.getKey();
            double pct = total > 0 ? e.getValue() / total * 100.0 : 0;
            Div row = new Div();
            row.addClassName("gp-ranking-row");
            row.add(new Span(rank + "º"), new Span(t(motive).toUpperCase(locale()) + " · " + (sector == null ? sec + " · " : "") + code + " · " + format1(e.getValue()) + " kg (" + format1(pct) + "%)"));
            table.add(row);
            rank++;
        }
        while (rank <= 5) {
            Div row = new Div(new Span(rank + "º"), new Span("—"));
            row.addClassName("gp-ranking-row");
            table.add(row);
            rank++;
        }
        return table;
    }

    private void renderTopReasonsByPeriod(Div host, List<RefugoRecord> rows, boolean monthly) {
        String dimension = monthly ? "Comparativo Mensal" : "Comparativo Anual";
        List<String> periods = rows.stream().map(r -> scrapKey(r, dimension)).distinct().sorted().toList();
        H3 title = new H3(t("Top 5 motivos por " + (monthly ? "mês" : "ano")));
        title.addClassName("gp-subsection-title");
        Span caption = new Span(t("Ranking independente em cada período • valor em Kg e participação no total do período"));
        caption.addClassName("gp-caption");
        Div table = new Div();
        table.addClassName("gp-period-ranking");
        table.getStyle().set("--gp-period-count", String.valueOf(periods.size()));
        Div header = new Div(new Span(t("Ranking")));
        header.addClassName("gp-period-ranking-row");
        for (String period : periods) header.add(new Span(scrapDisplayLabel(period, dimension)));
        table.add(header);
        Map<String, Map<String, Double>> byPeriod = new LinkedHashMap<>();
        Map<String, Double> totals = new LinkedHashMap<>();
        for (String period : periods) {
            List<RefugoRecord> scope = rows.stream().filter(r -> Objects.equals(scrapKey(r, dimension), period)).toList();
            byPeriod.put(period, aggregateScrap(scope, "Motivo"));
            totals.put(period, scope.stream().mapToDouble(RefugoRecord::scrapKg).sum());
        }
        for (int rank = 1; rank <= 5; rank++) {
            Div row = new Div(new Span(rank + "º"));
            row.addClassName("gp-period-ranking-row");
            for (String period : periods) {
                List<Map.Entry<String, Double>> r = new ArrayList<>(byPeriod.get(period).entrySet());
                if (r.size() < rank) { row.add(new Span("—")); continue; }
                var e = r.get(rank - 1);
                String[] parts = e.getKey().split("¦", -1);
                String code = parts.length > 0 ? parts[0] : "";
                String sec = parts.length > 1 ? parts[1] : "";
                String motive = parts.length > 2 ? parts[2] : e.getKey();
                double pct = totals.get(period) > 0 ? e.getValue() / totals.get(period) * 100.0 : 0;
                row.add(new Span(t(motive).toUpperCase(locale()) + " · " + sec + " · " + code + " · " + format1(e.getValue()) + " kg (" + format1(pct) + "%)"));
            }
            table.add(row);
        }
        host.add(title, caption, table);
    }

    private void renderRecentScrapLaunches(Div host, List<RefugoRecord> rows) {
        List<RefugoRecord> recentRows = rows.stream()
                .sorted(Comparator.comparingLong(RefugoRecord::erpId).reversed())
                .limit(20)
                .toList();

        Grid<RefugoRecord> grid = new Grid<>(RefugoRecord.class, false);
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(r -> Norm.br(r.productiveDate())).setHeader(t("Data")).setAutoWidth(true);
        grid.addColumn(MainView::scrapLoadTime).setHeader(t("Hora")).setAutoWidth(true);
        grid.addColumn(RefugoRecord::orderNumber).setHeader(t("OP")).setAutoWidth(true);
        grid.addColumn(RefugoRecord::machine).setHeader(t("Máquina")).setAutoWidth(true);
        grid.addColumn(RefugoRecord::product).setHeader(t("Produto")).setAutoWidth(true);
        grid.addColumn(RefugoRecord::shift).setHeader(t("Turno")).setAutoWidth(true);
        grid.addColumn(r -> t(nonBlank(r.motive()))).setHeader(t("Motivo")).setAutoWidth(true);
        grid.addColumn(RefugoRecord::operator).setHeader(t("Lançado por")).setAutoWidth(true);
        grid.addColumn(r -> format(r.scrapKg())).setHeader(t("Refugo (Kg)")).setAutoWidth(true);
        grid.setItems(recentRows);
        grid.setAllRowsVisible(true);
        grid.addClassName("gp-refugo-recent-grid");

        Component body = recentRows.isEmpty()
                ? new Span(t("Nenhum lançamento encontrado."))
                : grid;
        if (body instanceof Span span) span.addClassName("gp-caption");

        Details expander = new Details(t("Lançamentos recentes"), body);
        expander.setOpened(false);
        expander.addClassName("gp-refugo-recent-expander");
        host.add(expander);
    }

    private void renderScrapLaunches(Div host, List<RefugoRecord> rows, String dimension, String key) {
        List<RefugoRecord> selected = rows.stream().filter(r -> scrapMatches(r, dimension, key)).toList();
        if (selected.isEmpty()) return;
        H3 title = new H3(t("Lançamentos") + " · " + scrapDisplayLabel(key, dimension));
        title.addClassName("gp-subsection-title");
        Grid<RefugoRecord> grid = new Grid<>(RefugoRecord.class, false);
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(r -> Norm.br(r.productiveDate())).setHeader(t("Data"));
        grid.addColumn(MainView::scrapLoadTime).setHeader(t("Hora"));
        grid.addColumn(RefugoRecord::orderNumber).setHeader(t("Nº da OP"));
        grid.addColumn(RefugoRecord::sector).setHeader(t("Setor"));
        grid.addColumn(RefugoRecord::machine).setHeader(t("Máquina"));
        grid.addColumn(RefugoRecord::product).setHeader(t("Produto"));
        grid.addColumn(RefugoRecord::description).setHeader(t("Descrição"));
        grid.addColumn(RefugoRecord::shift).setHeader(t("Turno"));
        grid.addColumn(RefugoRecord::motive).setHeader(t("Motivo"));
        grid.addColumn(RefugoRecord::operator).setHeader(t("Lançado por"));
        grid.addColumn(r -> format(r.scrapKg())).setHeader(t("Refugo (Kg)"));
        grid.setItems(selected);
        configureAdaptiveGridHeight(grid, selected.size(), 18, 560);
        host.add(title, grid);
    }

    private void renderDescriptionDetails(Div host, List<RefugoRecord> rows, String key) {
        List<RefugoRecord> selected = rows.stream()
                .filter(r -> Objects.equals(scrapKey(r, "Descrição"), key))
                .toList();
        if (selected.isEmpty()) return;

        H3 title = new H3(t("Detalhes do item"));
        title.addClassName("gp-subsection-title");
        Span description = new Span(nonBlank(selected.get(0).description()));
        description.addClassName("gp-detail-main");

        List<String> productList = selected.stream().map(RefugoRecord::product)
                .filter(v -> v != null && !v.isBlank()).distinct().toList();
        List<String> clientList = selected.stream().map(RefugoRecord::client)
                .filter(v -> v != null && !v.isBlank()).distinct().toList();
        Div captions = new Div();
        captions.addClassName("gp-detail-captions");
        if (!productList.isEmpty()) captions.add(new Span(t("Produto(s)") + ": " + String.join(", ", productList)));
        if (!clientList.isEmpty()) captions.add(new Span(t("Cliente(s)") + ": " + String.join(", ", clientList)));
        host.add(title, description, captions);

        // App original: Data depende do período selecionado; Produto só aparece quando
        // a descrição selecionada realmente reúne mais de um produto.
        boolean multiDay = scrapStart != null && scrapEnd != null && !scrapStart.equals(scrapEnd);
        boolean multiProducts = productList.size() > 1;

        List<String> sectors = selected.stream().map(RefugoRecord::sector)
                .filter(Objects::nonNull).distinct().sorted().toList();
        for (String sector : sectors) {
            List<RefugoRecord> sectorRows = selected.stream()
                    .filter(r -> Objects.equals(r.sector(), sector)).toList();
            double sectorKg = sectorRows.stream().mapToDouble(RefugoRecord::scrapKg).sum();
            Span sectorTitle = new Span(t("SETOR") + ": " + sector + " (" + format(sectorKg) + " " + t("Kg") + ")");
            sectorTitle.addClassName("gp-refugo-sector-detail-title");
            host.add(sectorTitle);

            Map<String, List<RefugoRecord>> grouped = new LinkedHashMap<>();
            for (RefugoRecord r : sectorRows) {
                StringBuilder groupKey = new StringBuilder();
                if (multiDay) groupKey.append(r.productiveDate()).append('¦');
                groupKey.append(r.orderNumber());
                if (multiProducts) groupKey.append('¦').append(r.product());
                grouped.computeIfAbsent(groupKey.toString(), k -> new ArrayList<>()).add(r);
            }

            List<ScrapDetailRow> detailRows = new ArrayList<>();
            for (List<RefugoRecord> group : grouped.values()) {
                RefugoRecord first = group.get(0);
                double kg = group.stream().mapToDouble(RefugoRecord::scrapKg).sum();
                int units = (int) Math.round(group.stream().mapToDouble(RefugoRecord::itemCount).sum());
                double planned = group.stream().mapToDouble(RefugoRecord::plannedQty).max().orElse(0.0);
                Double loss = planned > 0 ? units / planned * 100.0 : null;
                detailRows.add(new ScrapDetailRow(
                        multiDay ? Norm.br(first.productiveDate()) : "",
                        first.orderNumber(),
                        multiProducts ? first.product() : "",
                        planned, kg, units, loss
                ));
            }

            if (multiDay) {
                detailRows.sort(
                        Comparator.comparing((ScrapDetailRow r) -> Norm.isoDate(r.date()),
                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                .reversed()
                                .thenComparing(ScrapDetailRow::order, Comparator.nullsLast(String::compareTo))
                );
            }

            Grid<ScrapDetailRow> grid = new Grid<>(ScrapDetailRow.class, false);
            grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
            if (multiDay) grid.addColumn(ScrapDetailRow::date).setHeader(t("Data"));
            grid.addColumn(ScrapDetailRow::order).setHeader(t("Ordem"));
            if (multiProducts) grid.addColumn(ScrapDetailRow::product).setHeader(t("Produto"));
            grid.addColumn(r -> formatInt((int) Math.round(r.planned()))).setHeader(t("Planejado (un)"));
            grid.addColumn(r -> format(r.scrapKg())).setHeader(t("Refugo (Kg)"));
            grid.addColumn(r -> formatInt(r.items())).setHeader(t("Refugo (un)"));
            grid.addColumn(r -> r.lossPct() == null ? "-" : format(r.lossPct()) + "%").setHeader(t("Perda (%)"));
            grid.setItems(detailRows);
            configureAdaptiveGridHeight(grid, detailRows.size(), 14, 500);
            host.add(grid);
        }

        // Resumo original: planejado contado uma vez por OP, usando o maior valor da OP.
        double totalKg = selected.stream().mapToDouble(RefugoRecord::scrapKg).sum();
        int totalItems = (int) Math.round(selected.stream().mapToDouble(RefugoRecord::itemCount).sum());
        Map<String, Double> plannedByOrder = new LinkedHashMap<>();
        for (RefugoRecord r : selected) plannedByOrder.merge(r.orderNumber(), r.plannedQty(), Math::max);
        double totalPlanned = plannedByOrder.values().stream().mapToDouble(Double::doubleValue).sum();
        Double loss = totalPlanned > 0 ? totalItems / totalPlanned * 100.0 : null;

        H3 summaryTitle = new H3(t("Resumo total do item"));
        summaryTitle.addClassName("gp-subsection-title");
        Div summary = new Div(
                kpi(t("Refugo total"), format(totalKg) + " " + t("Kg")),
                kpi(t("Unidades refugadas"), formatInt(totalItems)),
                kpi(t("Planejado das OPs"), formatInt((int) Math.round(totalPlanned))),
                kpi(t("Perda total"), loss == null ? "-" : format(loss) + "%")
        );
        summary.addClassNames("gp-kpis", "gp-detail-kpis");
        host.add(summaryTitle, summary);

        List<Double> weights = selected.stream().map(RefugoRecord::unitWeightG)
                .filter(v -> v != null && v > 0).distinct().toList();
        String weightText;
        if (weights.size() == 1) weightText = DisplayFormat.decimal(weights.get(0), 3, locale()) + " g";
        else if (weights.size() > 1) weightText = t("múltiplos pesos no agrupamento");
        else weightText = "-";
        Span weight = new Span(t("Peso unitário") + ": " + weightText);
        weight.addClassName("gp-caption");
        host.add(weight);
    }

    private static String scrapLoadTime(RefugoRecord record) {
        String time = Norm.syncTime(record == null ? null : record.firstDetectedAt());
        return time == null || time.isBlank() ? "—" : time;
    }

    private String signed1(double value) {
        return (value > 0 ? "+" : "") + format1(value);
    }

    private Popover scrapFilterDropdown(Button target, Runnable refresh) {
        Popover p = new Popover();
        p.setTarget(target);
        p.setPosition(PopoverPosition.BOTTOM_END);
        p.setWidth("min(320px, calc(100vw - 24px))");
        p.setModal(false);
        p.setBackdropVisible(false);
        p.setCloseOnOutsideClick(true);
        p.setCloseOnEsc(true);
        p.setAriaLabel(t("Filtros"));
        p.addClassNames("gp-filter-popover", "gp-filter-popover-refugo");

        LocalDate[] bounds = cachedScrapBounds();
        DateRangePicker period = new DateRangePicker(
                t("Período"), scrapStart, scrapEnd,
                bounds[0], bounds[1], language, this::t, null
        );
        @SuppressWarnings("unchecked")
        List<RefugoRecord>[] optionBase = new List[]{cachedScrapData(scrapStart, scrapEnd)};
        List<RefugoRecord> base = optionBase[0];
        MultiSelectComboBox<String> sector = multiSelect(t("Setor"),
                base.stream().map(RefugoRecord::sector).map(MainView::uppercaseSector).filter(v -> !v.isBlank()).distinct().sorted().toList(),
                scrapSectors, t("Todos"));
        forceUppercaseSectorFilter(sector);
        MultiSelectComboBox<String> order = multiSelect(t("Ordem"),
                base.stream().map(RefugoRecord::orderNumber).filter(Objects::nonNull).filter(v -> !v.isBlank()).distinct().sorted().toList(),
                scrapOrders, t("Todos"));
        MultiSelectComboBox<String> machine = multiSelect(t("Máquina"),
                base.stream().map(RefugoRecord::machine).filter(Objects::nonNull).filter(v -> !v.isBlank()).distinct().sorted().toList(),
                scrapMachines, t("Todos"));
        MultiSelectComboBox<String> shift = multiSelect(t("Turno"), List.of("A", "B", "C"), scrapShifts, t("Todos"));
        MultiSelectComboBox<String> product = multiSelect(t("Produto"),
                base.stream().map(RefugoRecord::product).filter(Objects::nonNull).filter(v -> !v.isBlank()).distinct().sorted().toList(),
                scrapProducts, t("Todos"));
        MultiSelectComboBox<String> description = multiSelect(t("Descrição"),
                base.stream().map(RefugoRecord::description).filter(Objects::nonNull).filter(v -> !v.isBlank()).distinct().sorted().toList(),
                scrapDescriptions, t("Todos"));
        MultiSelectComboBox<String> client = multiSelect(t("Cliente"),
                base.stream().map(RefugoRecord::client).filter(Objects::nonNull).filter(v -> !v.isBlank()).distinct().sorted().toList(),
                scrapClients, t("Todos"));
        MultiSelectComboBox<String> operator = multiSelect(t("Operador"),
                base.stream().map(RefugoRecord::operator).filter(Objects::nonNull).filter(v -> !v.isBlank()).distinct().sorted().toList(),
                scrapOperators, t("Todos"));
        List<String> motiveUids = base.stream()
                .map(r -> Norm.scrapMotiveUid(r.product(), r.motive()))
                .filter(Objects::nonNull).filter(v -> !v.isBlank()).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        MultiSelectComboBox<String> motive = multiSelect(t("Motivo"), motiveUids, scrapMotives, t("Todos"));
        motive.setItemLabelGenerator(uid -> {
            String[] parts = uid == null ? new String[0] : uid.split("¦", -1);
            if (parts.length >= 3) return t(parts[2]) + " · " + t(parts[1]) + " · " + parts[0];
            return t(uid == null ? "" : uid);
        });

        // Filtros contextuais: o período atualiza o universo; Setor limita as
        // Máquinas; Setor + Máquina limitam todas as demais listas.
        boolean[] adjustingOptions = {false};
        Runnable[] updateOptionsRef = new Runnable[1];
        updateOptionsRef[0] = () -> {
            LocalDate start = period.getStart();
            LocalDate end = period.getEnd();
            if (start != null && end != null) optionBase[0] = cachedScrapData(start, end);
            List<RefugoRecord> available = optionBase[0];

            updateMultiSelectOptions(sector, scrapFacetValues(available, r -> uppercaseSector(r.sector())));

            Set<String> selectedSectors = new LinkedHashSet<>(sector.getValue());
            List<RefugoRecord> sectorRows = selectedSectors.isEmpty() ? available : available.stream()
                    .filter(r -> selectedSectors.stream().anyMatch(s -> s.equalsIgnoreCase(r.sector()))).toList();
            updateMultiSelectOptions(machine, scrapFacetValues(sectorRows, RefugoRecord::machine));

            Set<String> selectedMachines = new LinkedHashSet<>(machine.getValue());
            List<RefugoRecord> contextual = selectedMachines.isEmpty() ? sectorRows : sectorRows.stream()
                    .filter(r -> selectedMachines.contains(r.machine())).toList();

            updateMultiSelectOptions(order, scrapFacetValues(contextual, RefugoRecord::orderNumber));
            updateMultiSelectOptions(product, scrapFacetValues(contextual, RefugoRecord::product));
            updateMultiSelectOptions(description, scrapFacetValues(contextual, RefugoRecord::description));
            updateMultiSelectOptions(client, scrapFacetValues(contextual, RefugoRecord::client));
            updateMultiSelectOptions(operator, scrapFacetValues(contextual, RefugoRecord::operator));
            updateMultiSelectOptions(shift, List.of("A", "B", "C").stream()
                    .filter(value -> contextual.stream().anyMatch(r -> value.equalsIgnoreCase(r.shift())))
                    .toList());
            updateMultiSelectOptions(motive, contextual.stream()
                    .map(r -> Norm.scrapMotiveUid(r.product(), r.motive()))
                    .filter(Objects::nonNull).filter(v -> !v.isBlank()).distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList());
        };

        adjustingOptions[0] = true;
        try { updateOptionsRef[0].run(); }
        finally { adjustingOptions[0] = false; }

        Runnable apply = () -> {
            scrapStart = period.getStart();
            scrapEnd = period.getEnd();
            replace(scrapSectors, sector.getValue());
            replace(scrapOrders, order.getValue());
            replace(scrapMachines, machine.getValue());
            replace(scrapProducts, product.getValue());
            replace(scrapDescriptions, description.getValue());
            replace(scrapClients, client.getValue());
            replace(scrapShifts, shift.getValue());
            replace(scrapOperators, operator.getValue());
            replace(scrapMotives, motive.getValue());
            resetScrapInteraction();
            updateFilterButton(target, scrapFiltersActive());
            refresh.run();
        };
        Runnable cascadeAndApply = () -> {
            if (adjustingOptions[0]) return;
            adjustingOptions[0] = true;
            try { updateOptionsRef[0].run(); }
            finally { adjustingOptions[0] = false; }
            apply.run();
        };
        period.setChangeListener(cascadeAndApply);
        sector.addValueChangeListener(e -> cascadeAndApply.run());
        machine.addValueChangeListener(e -> cascadeAndApply.run());
        order.addValueChangeListener(e -> { if (!adjustingOptions[0]) apply.run(); });
        product.addValueChangeListener(e -> { if (!adjustingOptions[0]) apply.run(); });
        description.addValueChangeListener(e -> { if (!adjustingOptions[0]) apply.run(); });
        client.addValueChangeListener(e -> { if (!adjustingOptions[0]) apply.run(); });
        shift.addValueChangeListener(e -> { if (!adjustingOptions[0]) apply.run(); });
        operator.addValueChangeListener(e -> { if (!adjustingOptions[0]) apply.run(); });
        motive.addValueChangeListener(e -> { if (!adjustingOptions[0]) apply.run(); });

        Button clear = new Button(t("Limpar filtros"), e -> {
            try {
                if (user != null && user.isAdmin()) scraps.clearSectorReassignments(user);
            } catch (RuntimeException ex) {
                notify(ex.getMessage() == null ? t("Não foi possível restaurar os setores originais do Refugo.") : ex.getMessage());
                return;
            }
            LocalDate today = Norm.productiveToday();
            scrapStart = today;
            scrapEnd = today;
            scrapSectors.clear();
            scrapOrders.clear();
            scrapMachines.clear();
            scrapProducts.clear();
            scrapDescriptions.clear();
            scrapClients.clear();
            scrapShifts.clear();
            scrapOperators.clear();
            scrapMotives.clear();
            scrapSearch = "";
            scrapExcludedIds.clear();
            resetScrapInteraction();
            invalidateDataCaches();
            updateFilterButton(target, scrapFiltersActive());
            p.setOpened(false);
            renderScrap();
        });
        clear.setWidthFull();
        clear.addClassName("gp-filter-clear");

        Div fields = new Div(period, sector, machine, order, product, description, client, shift, operator, motive);
        fields.addClassName("gp-filter-dropdown-grid");
        Div actions = new Div(clear);
        actions.addClassName("gp-filter-dropdown-actions");
        Div body = new Div(fields, actions);
        body.addClassNames("gp-filter-dropdown", "gp-filter-dropdown-refugo");
        p.add(body);
        updateFilterButton(target, scrapFiltersActive());
        return p;
    }

    private List<String> scrapFacetValues(List<RefugoRecord> rows,
                                          java.util.function.Function<RefugoRecord, String> getter) {
        if (rows == null || rows.isEmpty()) return List.of();
        return rows.stream().map(getter).filter(Objects::nonNull).filter(v -> !v.isBlank())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private void updateMultiSelectOptions(MultiSelectComboBox<String> field, List<String> options) {
        List<String> safe = options == null ? List.of() : options;
        LinkedHashSet<String> valid = new LinkedHashSet<>(field.getValue());
        valid.removeIf(value -> !safe.contains(value));
        field.setItems(safe);
        if (!Objects.equals(valid, field.getValue())) field.setValue(valid);
    }

    private void forceUppercase(TextField field) {
        if (field == null) return;
        field.addClassName("gp-uppercase-input");

        // Fallback no servidor: qualquer valor que chegue do cliente também é normalizado.
        field.addValueChangeListener(e -> {
            String current = e.getValue() == null ? "" : e.getValue();
            String upper = current.toUpperCase(Locale.ROOT);
            if (!upper.equals(current)) field.setValue(upper);
        });

        // Conversão no próprio input, no mesmo evento de digitação. Assim o usuário
        // já vê maiúsculas mesmo com Caps Lock desligado, sem esperar blur/round-trip.
        field.addAttachListener(e -> field.getElement().executeJs(
                """
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

    private void installContextMenuHoverOnly(Component trigger) {
        if (trigger == null) return;
        trigger.addAttachListener(e -> trigger.getElement().executeJs(
                """
                (() => {
                    const trigger = this;
                    if (trigger.__gpContextMenuHoverOnlyInstalled) return;
                    trigger.__gpContextMenuHoverOnlyInstalled = true;

                    const neutral = (item) => {
                        item.style.setProperty('background', 'transparent', 'important');
                        item.style.setProperty('background-color', 'transparent', 'important');
                        item.style.setProperty('box-shadow', 'none', 'important');
                        item.style.setProperty('outline', '0', 'important');
                        item.removeAttribute('focused');
                        item.removeAttribute('focus-ring');
                        item.removeAttribute('highlighted');
                        item.removeAttribute('selected');
                    };

                    const prepareItem = (item) => {
                        neutral(item);
                        if (item.__gpHoverOnlyInstalled) return;
                        item.__gpHoverOnlyInstalled = true;
                        item.addEventListener('mouseenter', () => {
                            if (item.hasAttribute('disabled')) return;
                            item.style.setProperty('background', 'var(--gp-hover)', 'important');
                            item.style.setProperty('background-color', 'var(--gp-hover)', 'important');
                        });
                        item.addEventListener('mouseleave', () => neutral(item));
                        item.addEventListener('focus', () => {
                            if (!item.matches(':hover')) neutral(item);
                        });
                    };

                    const apply = () => {
                        document.querySelectorAll('vaadin-context-menu-overlay vaadin-context-menu-item')
                            .forEach(prepareItem);
                    };

                    const schedule = () => {
                        requestAnimationFrame(apply);
                        setTimeout(apply, 0);
                        setTimeout(apply, 40);
                        setTimeout(apply, 120);
                    };

                    trigger.addEventListener('click', schedule, true);

                    if (!window.__gpContextMenuHoverObserver) {
                        window.__gpContextMenuHoverObserver = new MutationObserver(apply);
                        window.__gpContextMenuHoverObserver.observe(document.body, {childList:true, subtree:true});
                    }
                })();
                """));
    }

    private void enforceLoginInputContrast(Component field) {
        if (field == null) return;
        field.addAttachListener(e -> field.getElement().executeJs(
                """
                (() => {
                    const host = this;
                    const root = document.documentElement;
                    const apply = () => {
                        const dark = root.getAttribute('data-gp-theme') === 'dark' ||
                                     (root.getAttribute('theme') || '').split(/\\s+/).includes('dark');
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

    private Component oeeCell(LaunchRecord row, int decimals) {
        Span wrap = new Span();
        wrap.addClassName("gp-oee-cell");
        Span value = new Span((decimals <= 1 ? format1(row.getOeePct()) : format(row.getOeePct())) + "%");
        value.addClassName("gp-oee-value");
        if (row.getOeePct() < 85.0) value.addClassName("gp-oee-low");
        wrap.add(value);
        String obs = meaningfulObservation(row.getProblem());
        if (!obs.isBlank()) {
            Icon info = VaadinIcon.INFO_CIRCLE.create();
            info.addClassName("gp-oee-observation-icon-v063");
            info.getElement().setAttribute("tabindex", "0");
            info.getElement().setAttribute("aria-label", t("Observação") + ": " + obs);
            Tooltip.forComponent(info)
                    .withText(obs)
                    .withPosition(Tooltip.TooltipPosition.TOP)
                    .withHoverDelay(150);
            wrap.add(info);
        }
        return wrap;
    }

    private String meaningfulObservation(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.isBlank() || value.equalsIgnoreCase("Nenhum") || value.equalsIgnoreCase("None")
                || value.equalsIgnoreCase("Nan") || value.equals("-")) return "";
        return value;
    }

    private String combineObservations(List<LaunchRecord> rows) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (LaunchRecord row : rows) {
            String value = meaningfulObservation(row.getProblem());
            if (!value.isBlank()) unique.add(value);
        }
        return String.join(" / ", unique);
    }

    private void showReports() {
        Dialog d=dialog(t("Relatórios"));
        Span caption=new Span(t("Selecione um relatório."));
        caption.addClassName("gp-caption");
        d.add(caption);
        d.open();
    }

    private void showRegistry() {
        Dialog d = dialog(t("Cadastro"));
        d.setWidth("min(920px,94vw)");
        d.addClassName("gp-admin-dialog");
        Tab sectorsTab = new Tab(t("Setores"));
        Tab machinesTab = new Tab(t("Máquinas"));
        Tabs tabs = new Tabs(sectorsTab, machinesTab);
        tabs.addClassName("gp-admin-tabs");
        Div body = new Div();
        body.addClassName("gp-admin-body");
        tabs.addSelectedChangeListener(e -> {
            if (e.getSelectedTab() == sectorsTab) renderSectors(body);
            else renderMachines(body);
        });
        d.add(tabs, body);
        d.getFooter().add(new Button(t("Fechar"), e -> d.close()));
        renderSectors(body);
        d.open();
    }

    private void renderSectors(Div body) {
        body.removeAll();
        TextField name = new TextField(t("Nome do Setor"));
        name.setWidthFull();
        forceUppercase(name);
        Button add = new Button(t("Cadastrar Setor"), VaadinIcon.PLUS.create());
        add.addThemeVariants(ButtonVariant.PRIMARY);
        add.addClickListener(e -> {
            try {
                catalog.saveSector(null, name.getValue());
                invalidateDataCaches();
                renderSectors(body);
            } catch (Exception ex) { notify(t(ex.getMessage())); }
        });
        Div form = new Div(name, add);
        form.addClassNames("gp-admin-toolbar", "gp-admin-toolbar-sector");

        Grid<Sector> grid = new Grid<>(Sector.class, false);
        grid.addClassName("gp-admin-grid");
        grid.addColumn(Sector::name).setHeader(t("Setor")).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(new ComponentRenderer<>(sector -> {
            Button edit = iconButton(VaadinIcon.EDIT, t("Editar"));
            edit.addClickListener(e -> showSectorEdit(sector, body));
            Button delete = iconButton(VaadinIcon.TRASH, t("Excluir"));
            delete.addThemeVariants(ButtonVariant.ERROR);
            delete.addClickListener(e -> confirmDeleteSector(sector, body));
            HorizontalLayout actions = new HorizontalLayout(edit, delete);
            actions.setPadding(false); actions.setSpacing(true);
            return actions;
        })).setHeader(t("Ações")).setAutoWidth(true).setFlexGrow(0);
        grid.setItems(catalog.sectorEntries());
        grid.setAllRowsVisible(true);
        body.add(form, grid);
    }

    private void showSectorEdit(Sector sector, Div body) {
        Dialog d = dialog(t("Editar Setor"));
        d.setWidth("min(430px,92vw)");
        d.addClassName("gp-admin-form-dialog");
        TextField name = new TextField(t("Nome do Setor"));
        name.setValue(sector.name()); name.setWidthFull();
        forceUppercase(name);
        Div form = new Div(name); form.addClassName("gp-admin-form");
        Button save = new Button(t("Salvar"), e -> {
            try { catalog.saveSector(sector.id(), name.getValue()); invalidateDataCaches(); d.close(); renderSectors(body); }
            catch (Exception ex) { notify(t(ex.getMessage())); }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        d.add(form);
        d.getFooter().add(new Button(t("Cancelar"), e -> d.close()), save);
        d.open();
    }

    private void confirmDeleteSector(Sector sector, Div body) {
        Dialog d = dialog(t("Excluir Setor"));
        d.setWidth("min(430px,92vw)");
        d.add(new Paragraph(t("Confirma a exclusão de") + " " + sector.name() + "?"));
        Button delete = new Button(t("Excluir"), e -> {
            try { catalog.deleteSector(sector.id()); d.close(); renderSectors(body); }
            catch (Exception ex) { notify(t(ex.getMessage())); }
        });
        delete.addThemeVariants(ButtonVariant.ERROR);
        d.getFooter().add(new Button(t("Cancelar"), e -> d.close()), delete);
        d.open();
    }

    private void renderMachines(Div body) {
        body.removeAll();
        TextField name = new TextField(t("Nome da Máquina"));
        forceUppercase(name);
        IntegerField cap = new IntegerField(t("Capacidade 24h"));
        ComboBox<String> sector = new ComboBox<>();
        sector.setLabel(t("Atribuir ao Setor"));
        sector.setItems(catalog.sectors());
        sector.setAllowCustomValue(false);
        sector.setClearButtonVisible(false);
        sector.addClassNames("gp-admin-standard-field-v057", "gp-machine-sector-field-v060");
        Button add = new Button(t("Salvar Máquina"), VaadinIcon.PLUS.create());
        add.addThemeVariants(ButtonVariant.PRIMARY);
        add.addClickListener(e -> {
            try {
                catalog.saveMachine(null, name.getValue(), cap.getValue() == null ? 0 : cap.getValue(), sector.getValue());
                invalidateDataCaches();
                renderMachines(body);
            } catch (Exception ex) { notify(t(ex.getMessage())); }
        });
        Div form = new Div(name, cap, sector, add);
        form.addClassNames("gp-admin-toolbar", "gp-admin-toolbar-machine");

        Grid<Machine> grid = new Grid<>(Machine.class, false);
        grid.addClassName("gp-admin-grid");
        grid.addColumn(Machine::name).setHeader(t("Máquina")).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(m -> formatInt(m.capacity())).setHeader(t("Capacidade 24h")).setAutoWidth(true);
        grid.addColumn(Machine::sector).setHeader(t("Setor")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(machine -> {
            Button edit = iconButton(VaadinIcon.EDIT, t("Editar"));
            edit.addClickListener(e -> showMachineEdit(machine, body));
            Button delete = iconButton(VaadinIcon.TRASH, t("Excluir"));
            delete.addThemeVariants(ButtonVariant.ERROR);
            delete.addClickListener(e -> confirmDeleteMachine(machine, body));
            HorizontalLayout actions = new HorizontalLayout(edit, delete);
            actions.setPadding(false); actions.setSpacing(true);
            return actions;
        })).setHeader(t("Ações")).setAutoWidth(true).setFlexGrow(0);
        grid.setItems(catalog.machines());
        grid.setAllRowsVisible(true);
        body.add(form, grid);
    }

    private void showMachineEdit(Machine machine, Div body) {
        Dialog d = dialog(t("Editar Máquina"));
        d.setWidth("min(520px,92vw)");
        d.addClassNames("gp-admin-form-dialog", "gp-machine-edit-dialog-v060");
        TextField name = new TextField(t("Nome da Máquina")); name.setValue(machine.name());
        forceUppercase(name);
        IntegerField cap = new IntegerField(t("Capacidade 24h")); cap.setValue(machine.capacity());
        ComboBox<String> sector = new ComboBox<>();
        sector.setLabel(t("Atribuir ao Setor"));
        sector.setItems(catalog.sectors());
        sector.setAllowCustomValue(false);
        sector.setClearButtonVisible(false);
        sector.addClassNames("gp-admin-standard-field-v057", "gp-machine-sector-field-v060");
        if (machine.sector() != null && catalog.sectors().contains(machine.sector())) sector.setValue(machine.sector());
        name.setWidthFull();
        cap.setWidthFull();
        sector.setWidthFull();
        Div form = new Div(name, cap, sector);
        form.addClassNames("gp-admin-form", "gp-machine-edit-form-v060");
        Button save = new Button(t("Salvar"), e -> {
            try { catalog.saveMachine(machine.id(), name.getValue(), cap.getValue() == null ? 0 : cap.getValue(), sector.getValue()); invalidateDataCaches(); d.close(); renderMachines(body); }
            catch (Exception ex) { notify(t(ex.getMessage())); }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        d.add(form);
        d.getFooter().add(new Button(t("Cancelar"), e -> d.close()), save);
        d.open();
    }

    private void confirmDeleteMachine(Machine machine, Div body) {
        Dialog d = dialog(t("Excluir Máquina"));
        d.setWidth("min(430px,92vw)");
        d.add(new Paragraph(t("Confirma a exclusão de") + " " + machine.name() + "?"));
        Button delete = new Button(t("Excluir"), e -> {
            try { catalog.deleteMachine(machine.id()); d.close(); renderMachines(body); }
            catch (Exception ex) { notify(t(ex.getMessage())); }
        });
        delete.addThemeVariants(ButtonVariant.ERROR);
        d.getFooter().add(new Button(t("Cancelar"), e -> d.close()), delete);
        d.open();
    }

    private void showUsers() {
        Dialog d = dialog(t("Usuários"));
        d.setWidth("min(880px,94vw)");
        d.addClassName("gp-admin-dialog");
        Grid<User> grid = new Grid<>(User.class, false);
        grid.addClassName("gp-admin-grid");
        grid.addColumn(User::username).setHeader(t("Usuário")).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(u -> profileLabel(u.profile())).setHeader(t("Perfil")).setAutoWidth(true);
        grid.addColumn(u -> u.sector() == null ? "—" : u.sector()).setHeader(t("Setor")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(u -> {
            Button edit = iconButton(VaadinIcon.EDIT, t("Editar"));
            edit.addClickListener(e -> showUserEdit(u, d));
            Button delete = iconButton(VaadinIcon.TRASH, t("Excluir"));
            delete.addThemeVariants(ButtonVariant.ERROR);
            delete.setEnabled(user == null || user.id() != u.id());
            delete.addClickListener(e -> confirmDeleteUser(u, d));
            HorizontalLayout actions = new HorizontalLayout(edit, delete);
            actions.setPadding(false); actions.setSpacing(true);
            return actions;
        })).setHeader(t("Ações")).setAutoWidth(true).setFlexGrow(0);
        grid.setItems(auth.users()); grid.setAllRowsVisible(true);
        Button add = new Button(t("Novo Usuário"), VaadinIcon.PLUS.create());
        add.addThemeVariants(ButtonVariant.PRIMARY);
        add.addClickListener(e -> showUserEdit(null, d));
        Div toolbar = new Div(add); toolbar.addClassName("gp-admin-list-toolbar");
        d.add(toolbar, grid);
        d.getFooter().add(new Button(t("Fechar"), e -> d.close()));
        d.open();
    }

    private void showUserEdit(User existing, Dialog parent) {
        Dialog d = dialog(existing == null ? t("Novo Usuário") : t("Editar Usuário"));
        d.setWidth("min(600px,92vw)");
        d.addClassName("gp-admin-form-dialog");
        TextField username = new TextField(t("Usuário"));
        forceUppercase(username);
        ComboBox<String> profile = new ComboBox<>();
        profile.setLabel(t("Perfil"));
        profile.setAllowCustomValue(false);
        profile.setClearButtonVisible(false);
        profile.addClassNames("gp-admin-standard-field-v057", "gp-admin-profile-field-v057");
        List<String> profileValues = List.of("Padrão", "Acompanhamento", "Conferente", "Administrador");
        profile.setItems(profileValues); profile.setItemLabelGenerator(this::t);
        ComboBox<String> sector = new ComboBox<>();
        sector.setLabel(t("Setor"));
        sector.setItems(catalog.sectors());
        sector.setAllowCustomValue(false);
        sector.setClearButtonVisible(false);
        sector.addClassNames("gp-admin-standard-field-v057", "gp-admin-sector-field-v057");
        PasswordField password = new PasswordField(existing == null ? t("Senha") : t("Nova senha (opcional)"));
        PasswordField confirmPassword = new PasswordField(existing == null ? t("Confirmar Senha") : t("Confirmar Nova Senha"));
        username.addClassName("gp-admin-standard-field-v057");
        password.addClassName("gp-admin-standard-field-v057");
        confirmPassword.addClassName("gp-admin-standard-field-v057");
        username.setWidthFull(); profile.setWidthFull(); sector.setWidthFull(); password.setWidthFull(); confirmPassword.setWidthFull();
        if (existing != null) {
            username.setValue(existing.username()); profile.setValue(profileCanonical(existing.profile()));
            if (existing.sector() != null && catalog.sectors().contains(existing.sector())) sector.setValue(existing.sector());
        } else profile.setValue("Padrão");
        profile.addValueChangeListener(e -> sector.setEnabled("Padrão".equals(e.getValue())));
        sector.setEnabled("Padrão".equals(profile.getValue()));
        Div form = new Div(username, profile, sector, password, confirmPassword); form.addClassNames("gp-admin-form", "gp-admin-user-form");
        Button save = new Button(t("Salvar"), e -> {
            try {
                if(!Objects.equals(password.getValue(), confirmPassword.getValue())) throw new IllegalArgumentException(existing==null ? "A confirmação da senha não confere." : "A confirmação da nova senha não confere.");
                auth.saveUser(existing == null ? null : existing.id(), username.getValue(), profile.getValue(), sector.getValue(), password.getValue());
                d.close(); parent.close(); showUsers();
            } catch (Exception ex) { notify(t(ex.getMessage())); }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        d.add(form);
        d.getFooter().add(new Button(t("Cancelar"), e -> d.close()), save);
        d.open();
    }

    private void confirmDeleteUser(User user, Dialog parent) {
        Dialog d = dialog(t("Excluir Usuário"));
        d.setWidth("min(430px,92vw)");
        d.add(new Paragraph(t("Confirma a exclusão de") + " " + user.username() + "?"));
        Button delete = new Button(t("Excluir"), e -> {
            try { auth.deleteUser(user.id()); d.close(); parent.close(); showUsers(); }
            catch (Exception ex) { notify(t(ex.getMessage())); }
        });
        delete.addThemeVariants(ButtonVariant.ERROR);
        d.getFooter().add(new Button(t("Cancelar"), e -> d.close()), delete);
        d.open();
    }

    private Button iconButton(VaadinIcon icon, String aria) {
        String glyph = icon == VaadinIcon.EYE ? "👁️" : icon == VaadinIcon.EDIT ? "✏️" : icon == VaadinIcon.TRASH ? "❌" : "";
        Button b = new Button(glyph);
        b.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        b.getElement().setAttribute("aria-label", aria);
        b.setTooltipText(aria).withPosition(Tooltip.TooltipPosition.TOP);
        b.addClassName("gp-action-icon");
        if (icon == VaadinIcon.TRASH) b.addClassName("gp-action-icon-danger");
        return b;
    }

    private void showOwnPassword() {
        Dialog d = dialog(t("Alterar Senha"));
        PasswordField password = new PasswordField(t("Nova Senha"));
        PasswordField confirm = new PasswordField(t("Confirmar Nova Senha"));
        Button save = new Button(t("Alterar Senha"), e -> {
            if (password.getValue().isBlank()) { notify(t("Informe a nova senha.")); return; }
            if (!Objects.equals(password.getValue(), confirm.getValue())) { notify(t("A confirmação da nova senha não confere.")); return; }
            auth.changePassword(user.id(), password.getValue());
            d.close(); notify(t("Senha alterada com sucesso."));
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        d.add(password, confirm);
        d.getFooter().add(new Button(t("Cancelar"), e -> d.close()), save);
        d.open();
    }

    private Dialog dialog(String title) {
        Dialog d = new Dialog();
        d.addThemeName("gp-modal-v053");
        d.addClassName("gp-modal-v053");
        d.setHeaderTitle(title);
        d.setModal(true);
        d.setCloseOnEsc(true);
        d.setCloseOnOutsideClick(false);
        d.addOpenedChangeListener(event -> d.getElement().executeJs("""
            requestAnimationFrame(()=>{
              const open=Boolean(document.querySelector('vaadin-dialog-overlay[opened]'));
              document.documentElement.classList.toggle('gp-modal-open-v061',open);
              document.body.classList.toggle('gp-modal-open-v061',open);
            });
        """));

        Button close = new Button(VaadinIcon.CLOSE.create(), e -> d.close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        close.addClassName("gp-dialog-close");
        close.setTooltipText(t("Fechar")).withPosition(Tooltip.TooltipPosition.TOP);
        close.getElement().setAttribute("aria-label", t("Fechar"));
        d.getHeader().add(close);
        return d;
    }

    private List<String> launchMachineOptions(List<Machine> machines, java.util.Collection<String> sectors) {
        Set<String> selectedSectors = sectors == null ? Set.of() : new LinkedHashSet<>(sectors);
        return machines.stream()
                .filter(m -> selectedSectors.isEmpty()
                        || (m.sector() != null && selectedSectors.stream().anyMatch(s -> s.equalsIgnoreCase(m.sector()))))
                .map(Machine::name)
                .filter(Objects::nonNull)
                .filter(v -> !v.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }


    private List<String> launchClientOptions(Collection<LaunchRecord> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        return rows.stream()
                .map(LaunchRecord::getClientErp)
                .map(Norm::text)
                .filter(v -> !v.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private MultiSelectComboBox<String> multiSelect(String label, List<String> items, Set<String> selected, String placeholder) {
        MultiSelectComboBox<String> box = new MultiSelectComboBox<>(label);
        box.setItems(items);
        box.setPlaceholder(placeholder);
        box.getElement().setProperty("clearButtonVisible", true);
        // Usa a API própria do Vaadin, não apenas uma propriedade genérica no DOM.
        // Com keepFilter=false o texto pesquisado deve desaparecer ao selecionar.
        box.setKeepFilter(false);
        box.getElement().setProperty("keepFilter", false);
        box.setSelectedItemsOnTop(true);
        if (selected != null && !selected.isEmpty()) {
            box.select(selected.stream().filter(items::contains).toList());
        }
        box.setWidthFull();
        box.addClassName("gp-filter-multiselect");
        box.addValueChangeListener(e -> clearMultiSelectSearchText(box));
        box.addAttachListener(e -> clearMultiSelectSearchText(box));
        return box;
    }

    private void forceUppercaseSectorFilter(MultiSelectComboBox<?> box) {
        box.addClassName("gp-uppercase-sector-filter-v061");
        box.addAttachListener(e -> box.getElement().executeJs("""
            const host=this;
            const apply=()=>{
              const input=host.inputElement || host.shadowRoot?.querySelector('input');
              if(!input)return;
              const update=()=>{
                if(String(input.value||'').length>0){
                  input.style.setProperty('text-transform','uppercase','important');
                }else{
                  input.style.removeProperty('text-transform');
                }
              };
              if(!input.__gpUppercaseSectorContentV063){
                input.__gpUppercaseSectorContentV063=true;
                input.addEventListener('input',update,true);
              }
              update();
            };
            apply();
            requestAnimationFrame(apply);
            if(!host.__gpUppercaseSectorV061){
              host.__gpUppercaseSectorV061=true;
              host.addEventListener('opened-changed',apply);
            }
        """));
    }

    private void clearMultiSelectSearchText(MultiSelectComboBox<?> box) {
        // Reafirma a configuração pela API Java e elimina qualquer texto residual
        // criado pelo repaint do web component depois da seleção do chip.
        box.setKeepFilter(false);
        box.getElement().setProperty("keepFilter", false);
        box.getElement().setProperty("filter", "");
        box.getElement().executeJs("""
            const host=this;
            const clearTree=(root)=>{
              if(!root) return;
              if(root.nodeType===1){
                const tag=(root.tagName||'').toLowerCase();
                if(tag.includes('combo-box')){
                  try{ root.keepFilter=false; }catch(e){}
                  try{ root.filter=''; }catch(e){}
                  try{ if('_filter' in root) root._filter=''; }catch(e){}
                }
                if(tag==='input'){
                  try{ root.value=''; }catch(e){}
                  try{ root.removeAttribute('value'); }catch(e){}
                }
              }
              if(root.shadowRoot) clearTree(root.shadowRoot);
              const children=root.children || root.childNodes || [];
              Array.from(children).forEach(clearTree);
            };
            const clearResidual=()=>{
              host.keepFilter=false;
              try{ host.filter=''; }catch(e){}
              try{ if('_filter' in host) host._filter=''; }catch(e){}
              clearTree(host);
            };
            const schedule=()=>{
              clearResidual();
              requestAnimationFrame(clearResidual);
              setTimeout(clearResidual,0);
              setTimeout(clearResidual,40);
            };
            const selected=()=>{
              schedule();
              requestAnimationFrame(()=>{ try{ host.opened=false; }catch(e){} });
            };
            if(!host.__gpMultiSelectSingleRenderV052){
              host.__gpMultiSelectSingleRenderV052=true;
              host.keepFilter=false;
              host.addEventListener('selected-items-changed',selected);
              host.addEventListener('opened-changed',event=>{
                if(event.detail && event.detail.value) schedule();
              });
            }
            schedule();
        """);
    }

    private boolean launchFiltersActive() {
        LocalDate[] bounds = cachedLaunchBounds();
        boolean dateActive = !Objects.equals(launchStart, bounds[0]) || !Objects.equals(launchEnd, bounds[1]);
        return dateActive || !launchSectors.isEmpty() || !launchMachines.isEmpty() || !launchClients.isEmpty();
    }

    private boolean scrapFiltersActive() {
        LocalDate[] bounds = cachedScrapBounds();
        boolean dateActive = !Objects.equals(scrapStart, bounds[0]) || !Objects.equals(scrapEnd, bounds[1]);
        return dateActive
                || !scrapSectors.isEmpty()
                || !scrapOrders.isEmpty()
                || !scrapMachines.isEmpty()
                || !scrapProducts.isEmpty()
                || !scrapDescriptions.isEmpty()
                || !scrapClients.isEmpty()
                || !scrapShifts.isEmpty()
                || !scrapOperators.isEmpty()
                || !scrapMotives.isEmpty();
    }

    private Button searchFilterButton() {
        Button button = new Button();
        Span glyph = new Span();
        glyph.addClassName("gp-search-filter-funnel-v044");
        glyph.getElement().setAttribute("aria-hidden", "true");
        glyph.getElement().setProperty("innerHTML",
                "<svg viewBox=\"0 0 24 24\" width=\"18\" height=\"18\" aria-hidden=\"true\" focusable=\"false\">" +
                "<path d=\"M3.5 5.25h17l-6.7 7.55v5.15l-3.6 1.8V12.8L3.5 5.25Z\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linejoin=\"round\"/>" +
                "</svg>");
        button.setIcon(glyph);
        button.addClassName("gp-search-filter-button-v044");
        return button;
    }

    private Button filterButton() {
        Button button = new Button();
        var glyph = VaadinIcon.FILTER.create();
        glyph.addClassName("gp-filter-funnel-icon");
        glyph.getElement().setAttribute("aria-hidden", "true");
        button.setIcon(glyph);
        return button;
    }

    private void updateFilterButton(Button button, boolean active) {
        if (button == null) return;
        if (active) button.addClassName("gp-filter-active");
        else button.removeClassName("gp-filter-active");
    }

    private DateRangePicker datePicker(String label, LocalDate value) {
        LocalDate selected = value == null ? Norm.productiveToday() : value;
        LocalDate min = selected.minusYears(20);
        LocalDate max = selected.plusYears(20);
        DateRangePicker d = new DateRangePicker(
                label, selected, selected, min, max,
                language, this::t, null, true
        );
        d.addClassNames("gp-date-picker", "gp-unified-date-picker-v081");
        return d;
    }

    private Select<String> select(String label, List<String> items, String value, String emptyCaption) {
        Select<String> s = new Select<>();
        s.setLabel(label);
        s.setItems(items);
        s.setEmptySelectionAllowed(true);
        s.setEmptySelectionCaption(emptyCaption);
        if (value != null && items.contains(value)) s.setValue(value);
        return s;
    }

    private ComboBox<String> summaryFilterCombo(String label, List<String> items, String value, String placeholder) {
        ComboBox<String> field = new ComboBox<>(label);
        field.setItems(items);
        field.setAllowCustomValue(false);
        field.setClearButtonVisible(true);
        field.setPlaceholder(placeholder);
        field.setWidthFull();
        field.addClassName("gp-summary-native-clear-v087");
        if (value != null && items.contains(value)) field.setValue(value);
        return field;
    }

    private Div kpi(String label, String value) {
        Div d = new Div(new Span(label), new H3(value));
        d.addClassName("gp-kpi");
        return d;
    }

    private Div kpiWithCaption(String label, String value, String caption) {
        Div d = kpi(label, value);
        if (caption != null && !caption.isBlank()) {
            Span note = new Span(caption);
            note.addClassName("gp-kpi-caption");
            d.add(note);
        }
        return d;
    }

    private Div emptyState(String message) {
        Div d = new Div(new Span(message));
        d.addClassName("gp-empty-state");
        return d;
    }

    private String format1(double value) {
        return DisplayFormat.decimal(value, 1, locale());
    }

    private String profileCanonical(String profile) {
        return switch (profile) {
            case "administrador" -> "Administrador";
            case "acompanhamento" -> "Acompanhamento";
            case "conferente" -> "Conferente";
            default -> "Padrão";
        };
    }

    private String profileLabel(String profile) {
        return t(profileCanonical(profile));
    }

    private Locale locale() {
        return "en-US".equals(language) ? Locale.US : new Locale("pt", "BR");
    }

    private String format(double value) {
        return DisplayFormat.decimal(value, 2, locale());
    }

    private String formatInt(long value) {
        return DisplayFormat.integer(value, locale());
    }

    private void notify(String message) {
        Notification.show(message == null ? t("Operação não concluída.") : message, 3500, Notification.Position.TOP_CENTER);
    }

    private static String one(Set<String> set) {
        return set.size() == 1 ? set.iterator().next() : null;
    }

    private static void set(Set<String> set, String value) {
        set.clear();
        if (value != null && !value.isBlank()) set.add(value);
    }

    private static void replace(Set<String> target, java.util.Collection<String> values) {
        target.clear();
        if (values != null) {
            values.stream().filter(Objects::nonNull).filter(v -> !v.isBlank()).forEach(target::add);
        }
    }

    private String numberValue(double value) {
        if (Math.abs(value) < 1e-12) return "";
        if (Math.rint(value) == value) return String.valueOf((long) value);
        String s = String.valueOf(value);
        return "pt-BR".equals(language) ? s.replace('.', ',') : s;
    }

    private String value(int value) {
        return value == 0 ? "" : String.valueOf(value);
    }

    private String intValue(int value) {
        return value == 0 ? "" : String.valueOf(value);
    }

    private String scrapInputValue(double kg) {
        return numberValue(kg);
    }

    private static double parseDecimal(String raw, double def) {
        if (raw == null || raw.isBlank()) return def;
        try { return Double.parseDouble(raw.trim().replace(',', '.')); }
        catch (Exception e) { return def; }
    }

    private static int parseSumInt(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        double total = 0;
        try {
            for (String part : raw.split("\\+")) {
                if (!part.isBlank()) total += Double.parseDouble(part.trim().replace(',', '.'));
            }
            return (int) total;
        } catch (Exception e) {
            return 0;
        }
    }

    private static double parseScrapKg(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        double total = 0;
        try {
            for (String part : raw.split("\\+")) {
                String p = part.trim().replace(" ", "");
                if (p.isBlank()) continue;
                if (p.contains(",") || p.contains(".")) total += Double.parseDouble(p.replace(',', '.'));
                else total += Double.parseDouble(p) / 1000.0;
            }
            return total;
        } catch (Exception e) {
            return 0;
        }
    }

    private static double parseHours(String raw, double def) {
        if (raw == null || raw.isBlank()) return def;
        String s = raw.trim().replace(',', '.');
        try {
            if (s.contains(":")) {
                String[] parts = s.split(":");
                double h = Double.parseDouble(parts[0]);
                double m = parts.length > 1 ? Double.parseDouble(parts[1]) : 0;
                return h + m / 60.0;
            }
            if (s.contains(".")) {
                String[] parts = s.split("\\.");
                if (parts.length == 2) {
                    double h = Double.parseDouble(parts[0]);
                    String minuteText = parts[1].length() == 1 ? parts[1] + "0" : parts[1].substring(0, Math.min(2, parts[1].length()));
                    double m = Double.parseDouble(minuteText);
                    if (m < 60) return h + m / 60.0;
                }
            }
            return Double.parseDouble(s);
        } catch (Exception e) {
            return def;
        }
    }

    private static record ScrapDetailRow(String date, String order, String product, double planned, double scrapKg, int items, Double lossPct) {}

    private static class LaunchFormFields {
        Div root;
        DateRangePicker date;
        ComboBox<String> machine;
        TextField capacity;
        TextField product;
        TextField order;
        TextField hours;
        TextField weight;
        TextField shiftA;
        TextField scrapA;
        TextField shiftB;
        TextField scrapB;
        TextField shiftC;
        TextField scrapC;
        TextField changeovers;
        TextField setup;
        TextField breakdown;
        TextField observations;
    }
}
