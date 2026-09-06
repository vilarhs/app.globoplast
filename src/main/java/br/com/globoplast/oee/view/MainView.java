package br.com.globoplast.oee.view;

import static br.com.globoplast.oee.view.ViewComponents.actionIcon;
import static br.com.globoplast.oee.view.ViewComponents.actionIcons;

import br.com.globoplast.oee.config.AppConfig;
import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.model.RefugoRecord;
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
import com.vaadin.flow.component.contextmenu.HasMenuItems;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
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
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.shared.Tooltip;
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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Route("")
@PageTitle("GLOBOPLAST APP")
public class MainView extends VerticalLayout {
    private static final String TAB_AUTH_STORAGE_KEY = "globoplast_tab_auth_v078";
    private static final String SCRAP_REPORT_KEY = "relatorios_refugo";
    private enum ProductionSource { AUTOMATIC, MANUAL }

    private final AuthService auth;
    private final CatalogService catalog;
    private final LaunchService launches;
    private final RefugoService scraps;
    private final SyncService sync;

    private User user;
    private String language = "pt-BR";
    /** Impede que a leitura assíncrona inicial sobrescreva um login já iniciado. */
    private boolean loginInteractionStarted;
    private final Div shell = new Div();
    private final Div content = new Div();
    private MenuBar mainTabs;
    private final Map<MenuItem, String> tabKeys = new LinkedHashMap<>();
    private String renderedTabKey = "";
    private ProductionSource productionSource = ProductionSource.AUTOMATIC;

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
    private ScrapReportPage scrapReportPage;
    private ScrapAnalysisPage scrapAnalysisPage;
    private LocalDate summaryDayDate = LocalDate.now(AppConfig.ZONE).minusDays(1);
    private final Set<String> summaryDaySectors = new LinkedHashSet<>();
    private final Set<String> summaryDayMachines = new LinkedHashSet<>();
    private final Set<String> summaryDayShifts = new LinkedHashSet<>();
    private YearMonth summaryMonth = YearMonth.now(AppConfig.ZONE);
    private final Set<String> summaryMonthSectors = new LinkedHashSet<>();
    private final Set<String> summaryMonthMachines = new LinkedHashSet<>();
    private final Set<String> summaryMonthShifts = new LinkedHashSet<>();
    private String lastSyncSignature = "";

    // Cache por sessão para várias faixas: trocar de aba e voltar não relê
    // nem recalcula o mesmo período do SQLite. O limite evita crescimento livre.
    private static final int RANGE_CACHE_LIMIT = 8;
    private long dataRevision = 0;
    private final Map<String, List<LaunchRecord>> launchRangeCache = new LinkedHashMap<>();
    private final Map<String, List<RefugoRecord>> scrapRangeCache = new LinkedHashMap<>();
    private final Map<ProductionSource, Long> launchBoundsRevisions = new EnumMap<>(ProductionSource.class);
    private final Map<ProductionSource, LocalDate[]> launchBoundsCaches = new EnumMap<>(ProductionSource.class);
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
                    // Em uma aba nova, o usuário pode enviar o formulário antes
                    // de a leitura do sessionStorage terminar. Não reconstruir a
                    // tela de login por cima dessa tentativa.
                    if (loginInteractionStarted || user != null) return;
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
        // Mantém os valores sincronizados antes do clique ou do Enter,
        // inclusive em navegadores que não disparam blur de forma igual.
        username.setValueChangeMode(ValueChangeMode.EAGER);
        password.setValueChangeMode(ValueChangeMode.EAGER);
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
            loginInteractionStarted = true;
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
        restoreLaunchFilterState();
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
        if(selected==null||selected.isBlank()) selected=user.canSeeSummaries()?"manual_dia":"manual_lancamentos";
        selectTab(selected);
        configureReloadScrollRestoration();
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

    private void configureReloadScrollRestoration() {
        UI.getCurrent().getPage().executeJs("""
                (() => {
                  const key = 'gp-scroll-v250:' + location.pathname;
                  if (!window.__gpScrollTrackingV250) {
                    window.__gpScrollTrackingV250 = true;
                    const save = () => sessionStorage.setItem(key, String(window.scrollY));
                    window.addEventListener('scroll', save, {passive: true});
                    window.addEventListener('beforeunload', save, {capture: true});
                  }
                  if (window.__gpReloadScrollHandledV250) return;
                  window.__gpReloadScrollHandledV250 = true;
                  const navigation = performance.getEntriesByType('navigation')[0];
                  const isReload = navigation && navigation.type === 'reload';
                  const saved = Number(sessionStorage.getItem(key));
                  if (!isReload || !Number.isFinite(saved) || saved <= 0) {
                    window.scrollTo(0, 0);
                    return;
                  }
                  let attempts = 0;
                  const restore = () => {
                    window.scrollTo(0, saved);
                    if (Math.abs(window.scrollY - saved) > 2 && attempts++ < 60)
                      requestAnimationFrame(restore);
                  };
                  requestAnimationFrame(restore);
                })();
                """);
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
        mainTabs = new MenuBar();
        mainTabs.addClassName("gp-main-tabs");
        mainTabs.setWidthFull();
        mainTabs.setOpenOnHover(false);

        if (user.isAdmin()) {
            MenuItem onlineProductionTab = mainTabs.addItem("🏭 " + t("Produção (ON)"));
            onlineProductionTab.addClassName("gp-main-navigation-root");
            tabKeys.put(onlineProductionTab, "lancamentos");
            addTabMenuItem(onlineProductionTab.getSubMenu(), t("Lançamentos"), () -> openProductionSubPage("lancamentos"));
            if (user.canSeeSummaries()) {
                addTabMenuItem(onlineProductionTab.getSubMenu(), t("Resumo do Dia"), () -> openProductionSubPage("dia"));
                addTabMenuItem(onlineProductionTab.getSubMenu(), t("Resumo do Mês"), () -> openProductionSubPage("mes"));
            }
            addTabMenuItem(onlineProductionTab.getSubMenu(), t("Refugo"), () -> openProductionSubPage("refugo"));
            if (user.canModifyLaunches()) {
                addTabMenuItem(onlineProductionTab.getSubMenu(), t("Lixeira"), () -> {
                    selectTab("lancamentos");
                    showLaunchTrash("ERP");
                });
            }
        }

        MenuItem manualProductionTab = mainTabs.addItem("🏭 " + t("Produção"));
        manualProductionTab.addClassName("gp-main-navigation-root");
        tabKeys.put(manualProductionTab, "manual_lancamentos");
        addTabMenuItem(manualProductionTab.getSubMenu(), t("Lançamentos"), () -> selectTab("manual_lancamentos"));
        if (user.canSeeSummaries()) {
            addTabMenuItem(manualProductionTab.getSubMenu(), t("Resumo do Dia"), () -> selectTab("manual_dia"));
            addTabMenuItem(manualProductionTab.getSubMenu(), t("Resumo do Mês"), () -> selectTab("manual_mes"));
        }
        addTabMenuItem(manualProductionTab.getSubMenu(), t("Refugo"), () -> openProductionSubPage("refugo"));
        if (user.canModifyLaunches()) {
            addTabMenuItem(manualProductionTab.getSubMenu(), t("Lixeira"), () -> {
                selectTab("manual_lancamentos");
                showLaunchTrash("MANUAL");
            });
        }

        MenuItem stockTab = mainTabs.addItem("📦 " + t("Estoque"));
        stockTab.addClassName("gp-main-navigation-root");
        tabKeys.put(stockTab, "estoque");
        addTabMenuItem(stockTab.getSubMenu(), t("Buscar"), () -> selectTab("estoque"));

        MenuItem reportsTab = mainTabs.addItem("📊 " + t("Relatórios"));
        reportsTab.addClassName("gp-main-navigation-root");
        tabKeys.put(reportsTab, SCRAP_REPORT_KEY);
        addTabMenuItem(reportsTab.getSubMenu(), t("Refugo"), () -> openProductionSubPage(SCRAP_REPORT_KEY));

        addSystemMenu(mainTabs);

        Div nav = new Div(mainTabs);
        nav.addClassName("gp-navigation");
        return nav;
    }

    private void addTabMenuItem(HasMenuItems menu, String label, Runnable action) {
        menu.addItem(label, event -> action.run());
    }

    private void openProductionSubPage(String key) {
        selectTab(key);
    }

    private void selectTab(String key) {
        String normalizedKey = "producao".equals(key) ? "estoque" : key;
        String selectedKey = Set.of("lancamentos", "dia", "mes", "refugo").contains(normalizedKey)
                ? "lancamentos"
                : Set.of("manual_lancamentos", "manual_dia", "manual_mes").contains(normalizedKey)
                ? "manual_lancamentos"
                : normalizedKey;
        for (var e : tabKeys.entrySet()) {
            if (e.getValue().equals(selectedKey)) {
                updateNavigationIndicator(e.getKey());
                renderedTabKey = "";
                activateTab(normalizedKey);
                return;
            }
        }
        MenuItem first = tabKeys.keySet().iterator().next();
        updateNavigationIndicator(first);
        String fallbackKey = switch (normalizedKey) {
            case "lancamentos" -> "manual_lancamentos";
            case "dia" -> "manual_dia";
            case "mes" -> "manual_mes";
            default -> normalizedKey;
        };
        activateTab(fallbackKey);
    }

    private void updateNavigationIndicator(MenuItem activeTab) {
        tabKeys.keySet().forEach(tab -> {
            if (tab == activeTab) tab.addClassName("gp-navigation-active");
            else tab.removeClassName("gp-navigation-active");
        });
    }

    private void activateTab(String key) {
        if (key == null || key.isBlank() || Objects.equals(renderedTabKey, key)) return;
        renderedTabKey = key;
        VaadinSession.getCurrent().setAttribute("gp_tab", key);
        UI.getCurrent().getPage().executeJs(
                "history.replaceState(null,'',location.pathname)");
        render(key);
    }


    private void addSystemMenu(MenuBar menuBar) {
        Span triggerLabel = new Span("•••");
        triggerLabel.addClassName("gp-system-menu-label");
        MenuItem systemMenu = menuBar.addItem(triggerLabel);
        systemMenu.addClassName("gp-system-menu-root");
        systemMenu.getElement().setAttribute("aria-label", t("Menu"));
        HasMenuItems dropdown = systemMenu.getSubMenu();

        MenuItem me = dropdown.addItem(user.username(), event -> { });
        me.addClassName("gp-menu-caption");
        me.setEnabled(false);
        if (user.isAdmin()) {
            dropdown.addItem(t("Cadastro"), event -> showRegistry());
            dropdown.addItem(t("Usuários"), event -> showUsers());
        } else {
            dropdown.addItem(t("Alterar Senha"), event -> showOwnPassword());
        }

        MenuItem languageMenu = dropdown.addItem("pt-BR / en-US", event -> { });
        languageMenu.getSubMenu().addItem("pt-BR", event -> changeLanguage("pt-BR"));
        languageMenu.getSubMenu().addItem("en-US", event -> changeLanguage("en-US"));

        MenuItem themeMenu = dropdown.addItem(t("Tema"), event -> { });
        themeMenu.getSubMenu().addItem(t("Sistema"), event -> setThemeMode("system"));
        themeMenu.getSubMenu().addItem(t("Claro"), event -> setThemeMode("light"));
        themeMenu.getSubMenu().addItem(t("Escuro"), event -> setThemeMode("dark"));

        dropdown.addItem(t("Sair"), event -> {
            clearTabAuthentication();
            auth.logout();
            user = null;
            buildLogin();
        });

        menuSyncItem = dropdown.addItem("", event -> { });
        menuSyncItem.setEnabled(false);
        menuSyncItem.addClassName("gp-menu-sync-info-v045");
        refreshMenuSyncStatus();
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
        switch (key) {
            case "dia" -> { productionSource = ProductionSource.AUTOMATIC; renderDay(); }
            case "mes" -> { productionSource = ProductionSource.AUTOMATIC; renderMonth(); }
            case "manual_dia" -> { productionSource = ProductionSource.MANUAL; renderDay(); }
            case "manual_mes" -> { productionSource = ProductionSource.MANUAL; renderMonth(); }
            case "refugo" -> renderScrap();
            case SCRAP_REPORT_KEY -> renderScrapReport();
            case "estoque", "producao" -> renderOrderProduction();
            case "manual_lancamentos" -> { productionSource = ProductionSource.MANUAL; renderLaunches(); }
            default -> { productionSource = ProductionSource.AUTOMATIC; renderLaunches(); }
        }
    }

    private void renderLaunches() {
        content.removeAll();
        Grid<LaunchRecord> grid = launchGrid();
        LaunchesPage page = new LaunchesPage(productionTitle(t("Lançamentos")), launchSearch,
                user.canModifyLaunches() && productionSource == ProductionSource.MANUAL,
                this::t, this::launchFilterDropdown,
                value -> { launchSearch = value; launchLimit = AppConfig.PAGE_SIZE; refreshLaunchGrid(); },
                () -> showLaunchDialog(null),
                () -> { launchLimit += AppConfig.PAGE_SIZE; refreshLaunchGrid(); }, grid);
        content.add(page.components());
        refreshLaunchGrid();
        refreshMenuSyncStatus();
    }

    private String productionTitle(String title) {
        return title;
    }

    private void renderOrderProduction() {
        content.removeAll();
        OrderStockPage page = new OrderStockPage(launches, productionOrderSearch,
                value -> productionOrderSearch = value, this::t, this::formatInt, this::fullTextCell);
        content.add(page.components());
    }

    private Grid<LaunchRecord> launchGrid() {
        return LaunchesPage.grid(this::t, this::formatInt, this::format, this::fullTextCell,
                this::launchProductCell, this::launchOrderCell,
                record -> oeeCell(record, 1), this::launchActions);
    }

    private Component launchActions(LaunchRecord record) {
        Button view = actionIcon(VaadinIcon.EYE, t("Visualizar lançamento"));
        view.addClickListener(e -> showLaunchView(record));
        List<Button> buttons = new ArrayList<>();
        buttons.add(view);
        if (canActOnLaunch(record)) {
            Button edit = actionIcon(VaadinIcon.EDIT, t("Editar"));
            edit.addClickListener(e -> showLaunchDialog(record));
            Button delete = actionIcon(VaadinIcon.TRASH, t("Excluir"));
            delete.addClickListener(e -> confirmDelete(record));
            buttons.add(edit);
            buttons.add(delete);
        }
        return actionIcons(buttons.toArray(Button[]::new));
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

    @SuppressWarnings("unchecked")
    private void refreshLaunchGrid() {
        Component component = byId("launch-grid");
        if (!(component instanceof Grid<?> raw)) return;
        Grid<LaunchRecord> grid = (Grid<LaunchRecord>) raw;
        rememberLaunchFilterState();
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
        launchBoundsRevisions.clear();
        launchBoundsCaches.clear();
        scrapBoundsRevision = -1;
    }

    private void rememberLaunchFilterState() {
        VaadinSession.getCurrent().setAttribute("gp_launch_filter_state", new LaunchFilterState(
                launchStart, launchEnd, launchSearch,
                new LinkedHashSet<>(launchSectors), new LinkedHashSet<>(launchMachines), new LinkedHashSet<>(launchClients), launchLimit));
    }

    private void restoreLaunchFilterState() {
        Object saved = VaadinSession.getCurrent().getAttribute("gp_launch_filter_state");
        if (!(saved instanceof LaunchFilterState state)) return;
        if (state.start() != null) launchStart = state.start();
        if (state.end() != null) launchEnd = state.end();
        launchSearch = state.search() == null ? "" : state.search();
        replace(launchSectors, state.sectors());
        replace(launchMachines, state.machines());
        replace(launchClients, state.clients());
        launchLimit = Math.max(AppConfig.PAGE_SIZE, state.limit());
    }

    private List<LaunchRecord> cachedLaunchData(LocalDate start, LocalDate end) {
        if (start == null) start = Norm.productiveToday();
        if (end == null) end = start;
        if (end.isBefore(start)) { LocalDate tmp = start; start = end; end = tmp; }
        String key = productionSource + "|" + start + "|" + end;
        List<LaunchRecord> cached = launchRangeCache.get(key);
        if (cached != null) return cached;
        List<LaunchRecord> loaded = List.copyOf(productionSource == ProductionSource.MANUAL
                ? launches.manualOnly(start, end)
                : launches.automaticOnly(start, end));
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
        LocalDate[] cached = launchBoundsCaches.get(productionSource);
        if (!Objects.equals(launchBoundsRevisions.get(productionSource), dataRevision) || cached == null) {
            cached = productionSource == ProductionSource.MANUAL
                    ? launches.manualDateBounds()
                    : launches.automaticDateBounds();
            launchBoundsCaches.put(productionSource, cached);
            launchBoundsRevisions.put(productionSource, dataRevision);
        }
        return cached.clone();
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
        if (!edit) {
            record.setDate(LocalDate.now(AppConfig.ZONE).minusDays(1));
        }
        String title = edit ? t("Editar Lançamento") : t("Novo Lançamento");
        Dialog dialog = launchDialog(title);
        Div itemDescription = addLaunchItemDescription(dialog, record);
        LaunchFormFields fields = createLaunchForm(record, false, !edit, edit);
        configureProductMetadataLookup(fields.product, fields.weight, record, itemDescription);
        boolean[] resolvingOrder = {false};
        Runnable reloadScrap = !record.isErp() && !edit
                ? configureManualScrapLookup(fields, resolvingOrder)
                : () -> { };
        Runnable resolveOrder = () -> {
            if (resolvingOrder[0]) return;
            resolvingOrder[0] = true;
            try {
                String order = fields.order.getValue();
                if (order == null || order.isBlank()) {
                    fields.machine.clear();
                    fields.product.clear();
                    fields.weight.clear();
                    return;
                }
                Machine selected = findCatalogMachine(fields.machine.getValue());
                String sector = user != null && !user.isAdmin() ? user.sector() : selected == null ? "" : selected.sector();
                LaunchService.OrderLaunchDefaults defaults = launches.orderLaunchDefaults(order, sector, fields.date.getValue());
                Machine linked = findCatalogMachine(defaults.machine());
                boolean allowed = linked != null && (user.isAdmin() || catalog.allowedMachines(user).stream()
                        .anyMatch(machine -> machine.name().equals(linked.name())));
                if (allowed && !linked.name().equals(fields.machine.getValue())) fields.machine.setValue(linked.name());
                else if (!allowed) fields.machine.clear();
                if (defaults.product().isBlank()) {
                    fields.product.clear();
                    fields.weight.clear();
                } else if (!defaults.product().equals(normalizeProduct(fields.product.getValue()))) {
                    fields.product.setValue(defaults.product());
                }
            } finally {
                resolvingOrder[0] = false;
                reloadScrap.run();
            }
        };
        fields.order.addValueChangeListener(event -> resolveOrder.run());
        dialog.add(fields.root);

        Button save = new Button(edit ? t("Salvar Alterações") : t("Salvar Lançamento"));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.addClickListener(e -> {
            try {
                applyLaunchForm(record, fields);
                if (edit) {
                    if (record.isErp()) launches.saveErpOverride(record, user);
                    else launches.updateManual(record, user);
                } else {
                    launches.saveManual(record, user);
                }
                dialog.close();
                invalidateDataCaches();
                if (!record.isErp() && !"manual_lancamentos".equals(renderedTabKey)) {
                    selectTab("manual_lancamentos");
                } else {
                    renderLaunches();
                }
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
    }

    private void configureProductMetadataLookup(TextField product, TextField weight, LaunchRecord record, Div itemDescription) {
        if (product == null || product.isReadOnly()) return;
        product.setValueChangeMode(ValueChangeMode.LAZY);
        product.setValueChangeTimeout(300);
        product.addValueChangeListener(event -> {
            LaunchService.ProductMetadata metadata = launches.productMetadata(event.getValue());
            record.setProduct(normalizeProduct(event.getValue()));
            record.setDescriptionErp(metadata.description());
            record.setClientErp(metadata.client());
            if (!record.isErp()) {
                double unitWeightG = launches.productUnitWeightG(record.getProduct());
                weight.setValue(unitWeightG > 0 ? numberValue(unitWeightG) : "");
            }
            updateLaunchItemDescription(itemDescription, record);
        });
    }

    private Runnable configureManualScrapLookup(LaunchFormFields fields, boolean[] resolvingOrder) {
        Runnable load = () -> {
            if (resolvingOrder[0]) return;
            Machine machine = findCatalogMachine(fields.machine.getValue());
            String product = normalizeProduct(fields.product.getValue());
            String sector = machine != null ? machine.sector()
                    : user != null && !user.isAdmin() ? user.sector()
                    : product.isBlank() ? "" : Norm.scrapSector(product);
            LaunchService.ScrapByShift scrap = launches.remainingManualScrapByShift(fields.date.getValue(), fields.order.getValue(), sector, fields.machine.getValue(), fields.product.getValue());
            fields.scrapA.setValue(scrapInputValue(scrap.shiftA()));
            fields.scrapB.setValue(scrapInputValue(scrap.shiftB()));
            fields.scrapC.setValue(scrapInputValue(scrap.shiftC()));
        };
        fields.order.setValueChangeMode(ValueChangeMode.LAZY);
        fields.order.setValueChangeTimeout(300);
        fields.product.setValueChangeMode(ValueChangeMode.LAZY);
        fields.product.setValueChangeTimeout(300);
        fields.product.addValueChangeListener(event -> load.run());
        fields.date.setChangeListener(load);
        fields.machine.addValueChangeListener(event -> load.run());
        return load;
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
        } else if (!isNew && !machineOptions.isEmpty()) {
            f.machine.setValue(machineOptions.iterator().next());
        }
        f.machine.setReadOnly(readOnly);
        f.machine.setWidthFull();

        Machine catalogMachineForRecord = findCatalogMachine(originalMachine);
        boolean capacityReadOnly = readOnly || !record.isErp() || catalogMachineForRecord != null;
        f.capacity = textField(t("Capacidade (pçs/24h)"), value(record.getCapacity24h()), capacityReadOnly);
        f.product = textField(t("Cód. Produto"), cleanInput(record.getProduct()), readOnly);
        f.order = textField(t("Nº da OP"), cleanInput(record.getOrderNumber()), readOnly);
        if (!readOnly) f.order.setAllowedCharPattern("[0-9]");
        f.hours = textField(t("Hrs Program."), "24", true);
        f.weight = textField(t("Peso da Bis. (g)"), numberValue(record.getUnitWeightG()), readOnly);
        f.shiftA = textField(t("Turno A (pçs)"), intValue(record.getShiftA()), readOnly);
        f.scrapA = textField(t("Refugo A (kg)"), scrapInputValue(record.getScrapAKg()), readOnly);
        f.shiftB = textField(t("Turno B (pçs)"), intValue(record.getShiftB()), readOnly);
        f.scrapB = textField(t("Refugo B (kg)"), scrapInputValue(record.getScrapBKg()), readOnly);
        f.shiftC = textField(t("Turno C (pçs)"), intValue(record.getShiftC()), readOnly);
        f.scrapC = textField(t("Refugo C (kg)"), scrapInputValue(record.getScrapCKg()), readOnly);
        f.changeovers = textField(t("Qtd. Trocas"), intValue(record.getChangeovers()), readOnly);
        f.setup = textField(t("Setup (hrs)"), numberValue(record.getSetupHours()), readOnly);
        f.breakdown = textField(t("Paradas (hrs)"), numberValue(record.getBreakdownHours()), readOnly);
        f.observations = textField(t("Observações"), cleanProblem(record.getProblem()), readOnly);
        if (!readOnly) forceUppercase(f.observations);

        configureInputMode(f.product, "text");
        configureInputMode(f.order, "numeric");
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
            String edited = record.getEditedAt();
            time.setText(t("Hora do lançamento") + ": " + hour
                    + (edited == null || edited.isBlank() ? "" : " · " + t("Editado") + ": " + formatTrashDate(edited)));
        }
        Div rowBasic = new Div(f.order, f.product, f.weight);
        rowBasic.addClassNames("gp-launch-row", "gp-launch-row-3");
        Div rowMachine = new Div(f.machine, f.capacity, f.hours);
        rowMachine.addClassNames("gp-launch-row", "gp-launch-row-3");
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
        f.root.add(rowBasic, rowMachine, rowShifts, rowStops, rowObs);

        if (!readOnly) {
            List<Component> order = List.of(
                    f.date, f.order, f.product, f.weight, f.machine,
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
        String orderNumber = f.order.getValue() == null ? "" : f.order.getValue().trim();
        if (!orderNumber.isBlank() && !orderNumber.matches("\\d+"))
            throw new IllegalArgumentException(t("Informe uma única OP usando apenas números."));
        record.setOrderNumber(orderNumber);

        record.setScheduledHours(24.0);

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
        record.setProductionDetail("");
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

    private void showLaunchTrash(String type) {
        new LaunchTrashDialog(launches, user, type, this::t, this::dialog,
                this::launchProductCell, this::launchOrderCell, this::formatTrashDate,
                () -> {
                    invalidateDataCaches();
                    refreshLaunchGrid();
                }, this::notify).open();
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

        H2 title = new H2(productionTitle(t("Resumo Diário da Produção")));
        title.addClassName("gp-section-title");
        Button filter = searchFilterButton();
        filter.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        filter.addClassNames("gp-filter-button", "gp-summary-filter-trigger-v047");
        filter.setAriaLabel(t("Filtros"));
        filter.setTooltipText(t("Filtros")).withPosition(Tooltip.TooltipPosition.TOP);
        Div titleRow = new Div(title, filter);
        titleRow.addClassNames("gp-title-row", "gp-summary-title-row-v047");

        LocalDate[] bounds = cachedLaunchBounds();
        LocalDate yesterday = LocalDate.now(AppConfig.ZONE).minusDays(1);
        LocalDate defaultDate = yesterday.isBefore(bounds[0]) ? bounds[0] : (yesterday.isAfter(bounds[1]) ? bounds[1] : yesterday);
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

        boolean[] adjusting={false};
        ProductionSummaryPage summaryPage = new ProductionSummaryPage(
                ProductionSummaryPage.Period.DAY, this::t, this::formatInt, this::format,
                this::format1, this::locale, record -> oeeCell(record, 1), this::launchGrid,
                AppConfig.PAGE_SIZE, ignored -> { });

        Runnable[] refreshRef=new Runnable[1];
        refreshRef[0]=()->{
            if(adjusting[0])return;
            LocalDate d=date.getValue(); if(d==null)return;
            summaryDayDate=d;
            List<LaunchRecord> source=cachedLaunchData(d,d);
            List<LaunchRecord> sourceForShift = ProductionSummary.rowsForShifts(source, summaryDayShifts);
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
            List<LaunchRecord> summary=ProductionSummary.daily(rows);
            if(summary.isEmpty()){
                summaryPage.showEmpty(t("Nenhum lançamento encontrado para os filtros selecionados."));
                return;
            }
            summaryPage.show(summary, launches.newestFirst(rows));
        };
        date.setChangeListener(() -> {
            if (!adjusting[0]) {
                summaryDayDate = date.getValue();
                summaryPage.resetLimits();
                refreshRef[0].run();
            }
        });
        sector.addValueChangeListener(e->{if(!adjusting[0]){replace(summaryDaySectors,e.getValue());summaryPage.resetLimits();refreshRef[0].run();}});
        machine.addValueChangeListener(e->{if(!adjusting[0]){replace(summaryDayMachines,e.getValue());summaryPage.resetLimits();refreshRef[0].run();}});
        shift.addValueChangeListener(e->{if(!adjusting[0]){replace(summaryDayShifts,e.getValue());summaryPage.resetLimits();refreshRef[0].run();}});

        Popover filterDropdown = summaryFilterDropdown(filter, filterFields, () -> {
            adjusting[0]=true;
            try {
                summaryDayDate=defaultDate;
                summaryDaySectors.clear();
                summaryDayMachines.clear();
                summaryDayShifts.clear();
                summaryPage.resetLimits();
                date.setValue(defaultDate);
                sector.clear();
                machine.clear();
                shift.clear();
            } finally { adjusting[0]=false; }
            refreshRef[0].run();
        });

        page.add(titleRow,filterDropdown,summaryPage.result());
        content.add(page, summaryPage.entriesMore());
        refreshRef[0].run();
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

    private void renderMonth() {
        content.removeAll();
        Div page=new Div();
        page.addClassNames("gp-summary-page","gp-summary-month-page","gp-original-summary");
        H2 title=new H2(productionTitle(t("Resumo Mensal de Eficiência"))); title.addClassName("gp-section-title");
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
        MultiSelectComboBox<String> sector=multiSelect(t("Filtrar por Setor"),List.of(),summaryMonthSectors,t("Todos"));
        forceUppercaseSectorFilter(sector);
        MultiSelectComboBox<String> machine=multiSelect(t("Filtrar por Máquina"),List.of(),summaryMonthMachines,t("Todas"));
        MultiSelectComboBox<String> shift=multiSelect(t("Filtrar por Turno"),List.of("A","B","C"),summaryMonthShifts,t("Todos"));
        Div filterFields=new Div(month,sector,machine,shift);
        filterFields.addClassNames("gp-filter-dropdown-grid", "gp-summary-filter-fields-v047");
        boolean[] adjusting={false};
        ProductionSummaryPage summaryPage = new ProductionSummaryPage(
                ProductionSummaryPage.Period.MONTH, this::t, this::formatInt, this::format,
                this::format1, this::locale, record -> oeeCell(record, 1), this::launchGrid,
                monthLimit, value -> monthLimit = value);
        Runnable[] refreshRef=new Runnable[1];
        refreshRef[0]=()->{
            if(adjusting[0])return;
            YearMonth ym=month.getValue(); if(ym==null)return;
            summaryMonth=ym;
            List<LaunchRecord> source=cachedLaunchData(ym.atDay(1),ym.atEndOfMonth());
            List<LaunchRecord> sourceForShift=ProductionSummary.rowsForShifts(source,summaryMonthShifts);
            List<String> sectors=sourceForShift.stream().map(LaunchRecord::getSector).filter(Objects::nonNull).filter(v->!v.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
            adjusting[0]=true;
            try{
                Set<String> validSectors=new LinkedHashSet<>(summaryMonthSectors);
                validSectors.retainAll(sectors);
                sector.setItems(sectors);
                sector.setValue(validSectors);
                replace(summaryMonthSectors,validSectors);
                List<String> machines=sourceForShift.stream()
                        .filter(r->summaryMonthSectors.isEmpty()||summaryMonthSectors.contains(r.getSector()))
                        .map(LaunchRecord::getMachine).filter(Objects::nonNull).filter(v->!v.isBlank())
                        .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
                Set<String> validMachines=new LinkedHashSet<>(summaryMonthMachines);
                validMachines.retainAll(machines);
                machine.setItems(machines);
                machine.setValue(validMachines);
                replace(summaryMonthMachines,validMachines);
            }finally{adjusting[0]=false;}
            updateFilterButton(filter, !Objects.equals(summaryMonth, maxMonth) || !summaryMonthSectors.isEmpty() || !summaryMonthMachines.isEmpty() || !summaryMonthShifts.isEmpty());
            List<LaunchRecord> filtered=sourceForShift;
            if(!summaryMonthSectors.isEmpty())filtered=filtered.stream().filter(r->summaryMonthSectors.contains(r.getSector())).toList();
            if(!summaryMonthMachines.isEmpty())filtered=filtered.stream().filter(r->summaryMonthMachines.contains(r.getMachine())).toList();
            filtered=launches.newestFirst(filtered);
            List<LaunchRecord> summary=ProductionSummary.monthly(filtered);
            if(filtered.isEmpty()){
                summaryPage.showEmpty(t("Nenhum lançamento encontrado para o mês com os filtros selecionados."));
                return;
            }
            summaryPage.show(summary, filtered);
        };
        month.addValueChangeListener(e->{if(!adjusting[0]){summaryMonth=e.getValue();summaryPage.resetLimits();refreshRef[0].run();}});
        sector.addValueChangeListener(e->{if(!adjusting[0]){replace(summaryMonthSectors,e.getValue());summaryPage.resetLimits();refreshRef[0].run();}});
        machine.addValueChangeListener(e->{if(!adjusting[0]){replace(summaryMonthMachines,e.getValue());summaryPage.resetLimits();refreshRef[0].run();}});
        shift.addValueChangeListener(e->{if(!adjusting[0]){replace(summaryMonthShifts,e.getValue());summaryPage.resetLimits();refreshRef[0].run();}});

        Popover filterDropdown = summaryFilterDropdown(filter, filterFields, () -> {
            adjusting[0]=true;
            try {
                summaryMonth=maxMonth;
                summaryMonthSectors.clear();
                summaryMonthMachines.clear();
                summaryMonthShifts.clear();
                summaryPage.resetLimits();
                month.setValue(maxMonth);
                sector.clear();
                machine.clear();
                shift.clear();
            } finally { adjusting[0]=false; }
            refreshRef[0].run();
        });

        page.add(titleRow,filterDropdown,summaryPage.result());
        content.add(page,summaryPage.entriesMore());
        refreshRef[0].run();
    }

    private void renderScrap() {
        content.removeAll();
        ScrapAnalysisPage page = new ScrapAnalysisPage(this::t, scraps, this::formatInt, this::format,
                this::format1, this::locale, scrapSearch, scrapActiveDimension,
                searchFilterButton(), this::scrapHasMonthlyComparison, this::scrapHasYearlyComparison,
                value -> { scrapSearch = value; resetScrapInteraction(); },
                () -> scrapShowLaunches = false,
                dimension -> { scrapActiveDimension = dimension; refreshScrap(dimension); });
        Popover filterDropdown = scrapFilterDropdown(page.filterButton(), page::refreshSelected, this::renderScrap);
        page.setFilterDropdown(filterDropdown);
        scrapAnalysisPage = page;
        content.add(page);
        page.refreshSelected();
    }

    private void renderScrapReport() {
        content.removeAll();
        scrapReportPage = new ScrapReportPage(this::t, this::locale, scraps, scrapSearch, searchFilterButton(),
                filter -> scrapFilterDropdown(filter, this::refreshScrapReport, this::renderScrapReport),
                value -> {
                    scrapSearch = value;
                    refreshScrapReport();
                });
        content.add(scrapReportPage);
        refreshScrapReport();
    }

    private void refreshScrapReport() {
        if (scrapReportPage == null) return;
        List<RefugoRecord> rows = currentScrapReportRows();
        LocalDate comparisonStart = YearMonth.from(scrapEnd).minusMonths(6).atDay(1);
        List<RefugoRecord> comparisonRows = scraps.filter(
                cachedScrapData(comparisonStart, scrapEnd),
                scrapSearch, scrapSectors, scrapOrders, scrapMachines, scrapProducts,
                scrapDescriptions, scrapClients, scrapShifts, scrapOperators, scrapMotives);
        scrapReportPage.refresh(scrapStart, scrapEnd, rows, comparisonRows);
    }

    private List<RefugoRecord> currentScrapReportRows() {
        List<RefugoRecord> base = cachedScrapData(scrapStart, scrapEnd);
        return scraps.filter(base, scrapSearch, scrapSectors, scrapOrders, scrapMachines, scrapProducts,
                scrapDescriptions, scrapClients, scrapShifts, scrapOperators, scrapMotives);
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
        Div chart = scrapAnalysisPage == null ? null : scrapAnalysisPage.chart();
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

        Div recent = scrapAnalysisPage == null ? null : scrapAnalysisPage.recent();
        if (recent != null) {
            recent.removeAll();
            LocalDate productiveToday = Norm.productiveToday();
            if (Objects.equals(scrapStart, productiveToday) && Objects.equals(scrapEnd, productiveToday)) {
                scrapAnalysisPage.renderRecent(rows);
            }
        }
    }

    private void renderScrapDimension(Div host, List<RefugoRecord> rows, String dimension) {
        Map<String, Double> aggregate = scraps.aggregate(rows, dimension);
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
            labels.put(e.getKey(), scrapAnalysisPage.displayLabel(e.getKey(), dimension));
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
        scrapAnalysisPage.alignChartTitle(barChart);
        attachScrapContextMenu(barChart, rows, dimension);
        chartLine.add(barChart);
        host.add(chartLine);
        host.add(scrapPagination(dimension, page, totalPages));
        if ("Setor".equals(dimension)) {
            scrapAnalysisPage.renderTopReasonsBySector(host, rows, scrapSelectedDimension,
                    scrapSelectedKey, scrapSectors);
        }
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? "NÃO INFORMADO" : value;
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
        if (scrapAnalysisPage != null) {
            scrapAnalysisPage.renderKpis(rows, dimension, scrapSelectedDimension,
                    scrapSelectedKey, scrapStart, scrapEnd);
        }

        Div details = scrapAnalysisPage == null ? null : scrapAnalysisPage.details();
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

        // O item permanece visível em todos os perfis para que o menu do
        // gráfico não mude de estrutura. A permissão continua sendo aplicada
        // na ação e o item fica desabilitado para quem não é administrador.
        MenuItem transfer = menu.addItem(t("Enviar para outro setor"));
        Set<String> sectorSet = new LinkedHashSet<>();
        catalog.sectors().stream()
                .filter(Objects::nonNull)
                .map(MainView::uppercaseSector)
                .filter(v -> !v.isBlank())
                .forEach(sectorSet::add);
        // Mesmo durante uma sincronização incompleta, o menu continua útil:
        // os setores presentes nos próprios lançamentos formam um fallback.
        rows.stream()
                .map(RefugoRecord::sector)
                .filter(Objects::nonNull)
                .map(MainView::uppercaseSector)
                .filter(v -> !v.isBlank())
                .forEach(sectorSet::add);
        List<String> sectors = sectorSet.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        for (String sector : sectors) {
            transfer.getSubMenu().addItem(sector, e -> transferSelectedScrapToSector(dimension, sector));
        }
        transfer.setEnabled(!sectors.isEmpty());

        MenuItem exclude = menu.addItem(t("Excluir item"), e -> {
            if (!Objects.equals(scrapSelectedDimension, dimension) || scrapSelectedKey.isBlank()) {
                notify(t("Selecione um item no gráfico"));
                return;
            }
            rows.stream().filter(r -> scraps.matches(r, dimension, scrapSelectedKey)).map(RefugoRecord::analysisId).forEach(scrapExcludedIds::add);
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
                .filter(r -> scraps.matches(r, dimension, scrapSelectedKey))
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
        String selected = monthly && Objects.equals(scrapSelectedDimension, dimension) ? scrapSelectedKey : null;
        scrapAnalysisPage.renderComparison(host, rows, monthly, selected,
                key -> toggleScrapSelectionAndRefreshKpis(dimension, key),
                key -> selectScrapForContextMenu(dimension, key),
                chart -> attachScrapContextMenu(chart, rows, dimension));
    }

    private void renderScrapLaunches(Div host, List<RefugoRecord> rows, String dimension, String key) {
        List<RefugoRecord> selected = rows.stream().filter(r -> scraps.matches(r, dimension, key)).toList();
        if (selected.isEmpty()) return;
        H3 title = new H3(t("Lançamentos") + " · " + scrapAnalysisPage.displayLabel(key, dimension));
        title.addClassName("gp-subsection-title");
        Grid<RefugoRecord> grid = new Grid<>(RefugoRecord.class, false);
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(r -> Norm.br(r.productiveDate())).setHeader(t("Data")).setWidth("108px").setFlexGrow(0);
        grid.addColumn(ScrapAnalysisPage::loadTime).setHeader(t("Hora"));
        grid.addColumn(RefugoRecord::orderNumber).setHeader(t("Nº OP")).setWidth("84px").setFlexGrow(0);
        grid.addColumn(RefugoRecord::sector).setHeader(t("Setor"));
        grid.addColumn(RefugoRecord::machine).setHeader(t("Máquina"));
        grid.addColumn(RefugoRecord::product).setHeader(t("Código Produto")).setWidth("140px").setFlexGrow(0);
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
                .filter(r -> Objects.equals(scraps.analysisKey(r, "Descrição"), key))
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
            if (multiDay) grid.addColumn(ScrapDetailRow::date).setHeader(t("Data")).setWidth("108px").setFlexGrow(0);
            grid.addColumn(ScrapDetailRow::order).setHeader(t("Nº OP")).setWidth("84px").setFlexGrow(0);
            if (multiProducts) grid.addColumn(ScrapDetailRow::product).setHeader(t("Código Produto")).setWidth("140px").setFlexGrow(0);
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

    private Popover scrapFilterDropdown(Button target, Runnable refresh, Runnable clearView) {
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
            clearView.run();
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

    private void showRegistry() {
        new RegistryDialog(catalog, launches, this::t, this::dialog, this::forceUppercase,
                this::formatInt, this::invalidateDataCaches, this::notify).open();
    }

    private void showUsers() {
        new UsersDialog(auth, user, catalog::sectors, this::t, this::dialog,
                this::forceUppercase, this::notify).open();
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
        return ViewComponents.dialog(title, t("Fechar"));
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
                total += Double.parseDouble(p.replace(',', '.'));
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

    private record LaunchFilterState(LocalDate start, LocalDate end, String search, Set<String> sectors, Set<String> machines, Set<String> clients, int limit) {}


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
