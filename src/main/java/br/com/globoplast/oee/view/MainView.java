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
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.contextmenu.HasMenuItems;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.Registration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
    private final LaunchCells launchCells;
    private final ProductionSummaryScreen productionSummary;
    private final ScrapScreen scrapScreen;
    private final LaunchesScreen launchesScreen;

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

    private String productionOrderSearch = "";

    private String lastSyncSignature = "";

    // Cache por sessão para várias faixas: trocar de aba e voltar não relê
    // nem recalcula o mesmo período do SQLite. O limite evita crescimento livre.
    private static final int RANGE_CACHE_LIMIT = 8;
    private long dataRevision = 0;
    private final RangeCache<LaunchRecord> launchRangeCache = new RangeCache<>(RANGE_CACHE_LIMIT);
    private final RangeCache<RefugoRecord> scrapRangeCache = new RangeCache<>(RANGE_CACHE_LIMIT);
    private final Map<ProductionSource, Long> launchBoundsRevisions = new EnumMap<>(ProductionSource.class);
    private final Map<ProductionSource, LocalDate[]> launchBoundsCaches = new EnumMap<>(ProductionSource.class);
    private long scrapBoundsRevision = -1;
    private LocalDate[] scrapBoundsCache;
    private boolean syncRefreshRunning = false;
    private Registration syncPollRegistration;
    private MenuItem menuSyncItem = null;

    public MainView(AuthService auth, CatalogService catalog, LaunchService launches, RefugoService scraps, SyncService sync) {
        this.auth = auth;
        this.catalog = catalog;
        this.launches = launches;
        this.scraps = scraps;
        this.sync = sync;
        this.launchCells = new LaunchCells(this::t, this::formatInt, this::format1, this::format);
        this.productionSummary = new ProductionSummaryScreen(launches, launchCells, () -> language,
                this::cachedLaunchBounds, this::cachedLaunchData, this::launchGrid);
        this.scrapScreen = new ScrapScreen(scraps, catalog, () -> user, () -> language,
                content, this::cachedScrapBounds, this::cachedScrapData, this::invalidateDataCaches, this::notify);
        this.launchesScreen = new LaunchesScreen(catalog, launches, () -> language,
                () -> user.canModifyLaunches() && productionSource == ProductionSource.MANUAL,
                content, this::cachedLaunchBounds, this::cachedLaunchData, this::launchGrid,
                () -> launchDialogs().open(null), this::refreshMenuSyncStatus);
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
        stopSyncPolling();
        applyThemeMode();
        ensureFavicon();
        UI.getCurrent().setLocale(locale());
        shell.removeAll();
        shell.add(new LoginPage(this::t, logoPair("gp-login-logo"), this::forceUppercase,
                (username, password) -> {
            loginInteractionStarted = true;
            User authenticated = auth.authenticate(username, password);
            if (authenticated == null) return false;
            user = authenticated;
            language = user.language();
            markTabAuthenticated(authenticated);
            buildApp();
            return true;
        }));
    }

    private void buildApp() {
        shell.removeAll();
        renderedTabKey = "";
        launchesScreen.restoreLaunchFilterState();
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
        stopSyncPolling();
        UI.getCurrent().setPollInterval(30000);
        syncPollRegistration = UI.getCurrent().addPollListener(e -> {
            if (syncRefreshRunning) return;
            syncRefreshRunning = true;
            try {
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

    private void stopSyncPolling() {
        if (syncPollRegistration != null) {
            syncPollRegistration.remove();
            syncPollRegistration = null;
        }
        UI.getCurrent().setPollInterval(-1);
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
            case "dia" -> { productionSource = ProductionSource.AUTOMATIC; productionSummary.renderDay(content); }
            case "mes" -> { productionSource = ProductionSource.AUTOMATIC; productionSummary.renderMonth(content); }
            case "manual_dia" -> { productionSource = ProductionSource.MANUAL; productionSummary.renderDay(content); }
            case "manual_mes" -> { productionSource = ProductionSource.MANUAL; productionSummary.renderMonth(content); }
            case "refugo" -> scrapScreen.renderScrap();
            case SCRAP_REPORT_KEY -> scrapScreen.renderScrapReport();
            case "estoque", "producao" -> renderOrderProduction();
            case "manual_lancamentos" -> { productionSource = ProductionSource.MANUAL; launchesScreen.renderLaunches(); }
            default -> { productionSource = ProductionSource.AUTOMATIC; launchesScreen.renderLaunches(); }
        }
    }

    private void renderOrderProduction() {
        content.removeAll();
        OrderStockPage page = new OrderStockPage(launches, productionOrderSearch,
                value -> productionOrderSearch = value, this::t, this::formatInt, launchCells::fullText);
        content.add(page.components());
    }

    private Grid<LaunchRecord> launchGrid() {
        return LaunchesPage.grid(this::t, this::formatInt, this::format, launchCells::fullText,
                launchCells::product, launchCells::order,
                record -> launchCells.oee(record, 1), this::launchActions);
    }

    private Component launchActions(LaunchRecord record) {
        Button view = actionIcon(VaadinIcon.EYE, t("Visualizar lançamento"));
        view.addClickListener(e -> launchDialogs().view(record));
        List<Button> buttons = new ArrayList<>();
        buttons.add(view);
        if (canActOnLaunch(record)) {
            Button edit = actionIcon(VaadinIcon.EDIT, t("Editar"));
            edit.addClickListener(e -> launchDialogs().open(record));
            Button delete = actionIcon(VaadinIcon.TRASH, t("Excluir"));
            delete.addClickListener(e -> launchDialogs().confirmDelete(record));
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

    private void refreshMenuSyncStatus() {
        SyncStatusMenu.update(menuSyncItem, launches.syncStatus(), this::t);
    }

    private void invalidateDataCaches() {
        dataRevision++;
        launchRangeCache.clear();
        scrapRangeCache.clear();
        launchBoundsRevisions.clear();
        launchBoundsCaches.clear();
        scrapBoundsRevision = -1;
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
        launchRangeCache.put(key, loaded);
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
        scrapRangeCache.put(key, loaded);
        return loaded;
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

    private LaunchDialog launchDialogs() {
        return new LaunchDialog(catalog, launches, user, language, launchCells,
                record -> {
                    invalidateDataCaches();
                    if (!record.isErp() && !"manual_lancamentos".equals(renderedTabKey)) {
                        selectTab("manual_lancamentos");
                    } else {
                        launchesScreen.renderLaunches();
                    }
                }, () -> {
                    invalidateDataCaches();
                    launchesScreen.renderLaunches();
                }, this::notify);
    }

    private void showLaunchTrash(String type) {
        new LaunchTrashDialog(launches, user, type, this::t, this::dialog,
                launchCells::product, launchCells::order, LaunchDateFormat::trash,
                () -> {
                    invalidateDataCaches();
                    launchesScreen.refreshLaunchGrid();
                }, this::notify).open();
    }

    private void forceUppercase(TextField field) {
        InputControls.forceUppercase(field);
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
        new PasswordDialog(auth, user, this::t, this::dialog, this::notify).open();
    }

    private Dialog dialog(String title) {
        return ViewComponents.dialog(title, t("Fechar"));
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

}
