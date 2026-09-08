package br.com.globoplast.oee.service;

import br.com.globoplast.oee.config.AppConfig;
import br.com.globoplast.oee.db.Database;
import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.model.RefugoRecord;
import br.com.globoplast.oee.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProductionLifecycleIntegrationTest {
    @TempDir Path temporaryDirectory;

    private Database database;
    private CatalogService catalog;
    private LaunchService launches;
    private RefugoService scrap;
    private SyncService sync;
    private final User admin = new User(1, "TESTE", true, AppConfig.PROFILE_ADMIN, null, "pt-BR");
    private final LocalDate productionDate = YearMonth.now(AppConfig.ZONE).minusMonths(1).atDay(15);

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("globoplast.db", temporaryDirectory.resolve("database.db").toString());
        database = new Database();
        database.initialize();
        AuthService auth = new AuthService(database, new PasswordService());
        catalog = new CatalogService(database, auth);
        JsonMapper json = JsonMapper.builder().build();
        launches = new LaunchService(database, catalog, new OeeCalculator(), json);
        scrap = new RefugoService(database);
        sync = new SyncService(database, json, catalog);

        catalog.saveSector(null, "COL DE TAMPA");
        catalog.saveMachine(null, "HOT AIR 1", 24_000, "COL DE TAMPA");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("globoplast.db");
    }

    @Test
    void validatesPastMonthProductionAndRemovesEveryTestLaunch() throws Exception {
        String order = "990001";
        String launchProduct = "7761234567";
        String scrapProduct = "7751234567";

        Map<String, Object> production = values(
                "erp_id", 1001L, "ordem", order, "data_apon", productionDate.toString(),
                "produto", launchProduct, "descricao", "ITEM DE TESTE", "maquina", "HOT AIR 1",
                "qtd_plan", 24.0, "cliente", "TESTE", "turno", "B", "qtd_apon", 10.0,
                "operador", "TESTE");
        Map<String, Object> rejection = values(
                "erp_id", 2001L, "data_apon", productionDate.toString(), "ordem", order,
                "qtd_planej", 24.0, "maquina", "HOT AIR 1", "produto", scrapProduct,
                "descricao", "ITEM DE TESTE", "cliente", "TESTE", "turno", "B",
                "operador", "TESTE", "qtd_refugo", 2.0, "motivo", "TESTE",
                "peso_br", 10.0, "qtd_itens", 200);
        Map<String, Object> planning = values(
                "erp_id", 3001L, "data_plan", productionDate.toString(), "ordem", order,
                "produto", launchProduct, "descricao", "ITEM DE TESTE", "qtd_plan", 24.0,
                "qtd_prod", 10.0, "processo", 776, "flag_exe", "S");

        assertEquals(1, sync.importBatch("apontamento", List.of(production), "test", "test").get("alterados"));
        assertEquals(1, sync.importBatch("refugo", List.of(rejection), "test", "test").get("alterados"));
        assertEquals(1, sync.importBatch("planejamento", List.of(planning), "test", "test").get("alterados"));
        assertEquals(0, sync.importBatch("refugo", List.of(rejection), "test", "test").get("alterados"),
                "Reenviar o mesmo registro não pode duplicá-lo");
        assertEquals(1, count("erp_refugo_raw"));

        LaunchRecord automatic = assertSingle(launches.automaticOnly(productionDate, productionDate));
        assertEquals(10_000, automatic.getTotalProduced());
        assertEquals(2.0, automatic.getScrapBKg(), 0.001);
        assertEquals(200, automatic.getScrapTotalPcs());
        assertEquals(10.0, automatic.getUnitWeightG(), 0.001);
        assertTrue(automatic.isOrderProgressAvailable());
        assertEquals(24_000, automatic.getOrderPlannedPcs());
        assertEquals(10_000, automatic.getOrderLaunchedPcs());
        assertEquals(14_000, automatic.getOrderRemainingPcs());

        RefugoRecord analyzedScrap = assertSingle(scrap.load(productionDate, productionDate));
        assertEquals("Fechamento de Fundo", analyzedScrap.sector());
        assertEquals(2.0, analyzedScrap.scrapKg(), 0.001);

        LaunchService.ScrapByShift available = launches.remainingManualScrapByShift(
                productionDate, order, "COL DE TAMPA", "", launchProduct);
        assertEquals(2.0, available.shiftB(), 0.001,
                "O refugo 775 da Hot Air deve alimentar a produção 776 de Colocação de Tampa");

        LaunchRecord manual = manualLaunch(order, launchProduct, 10_000, available.shiftB());
        launches.saveManual(manual, admin);
        LaunchRecord saved = assertSingle(launches.manualOnly(productionDate, productionDate));
        assertEquals(productionDate, saved.getDate());
        assertNotEquals(YearMonth.now(AppConfig.ZONE), YearMonth.from(saved.getDate()));
        assertEquals(200, saved.getScrapTotalPcs(), "2 kg com peso de 10 g devem resultar em 200 peças");
        assertEquals(24_000, saved.getOrderPlannedPcs());
        assertEquals(10_000, saved.getOrderLaunchedPcs());
        assertEquals(14_000, saved.getOrderRemainingPcs());
        assertEquals(0.0, launches.remainingManualScrapByShift(
                productionDate, order, "COL DE TAMPA", "", launchProduct).shiftB(), 0.001,
                "O mesmo refugo não pode ser carregado novamente");

        String originalTime = saved.getLaunchTime();
        saved.setShiftB(13_000);
        launches.updateManual(saved, admin);
        LaunchRecord edited = assertSingle(launches.manualOnly(productionDate, productionDate));
        assertEquals(originalTime, edited.getLaunchTime());
        assertFalse(edited.getEditedAt().isBlank());

        Machine machine = assertSingle(catalog.machines());
        catalog.saveMachine(machine.id(), machine.name(), 12_000, machine.sector());
        launches.refreshAllMachineCapacities();
        LaunchRecord recalculated = assertSingle(launches.manualOnly(productionDate, productionDate));
        assertEquals(12_000, recalculated.getCapacity24h());
        assertTrue(recalculated.getOeePct() > 100.0, "O OEE atual pode ultrapassar 100%");

        LaunchRecord forbidden = manualLaunch("990002", launchProduct, 1_000, 0);
        User otherSector = new User(2, "OUTRO", false, AppConfig.PROFILE_STANDARD, "IMPRESSÃO", "pt-BR");
        assertThrows(IllegalArgumentException.class, () -> launches.saveManual(forbidden, otherSector));
        assertEquals(1, count("historico_oee"));

        launches.deleteManual(recalculated.getId(), admin);
        assertEquals(0, count("historico_oee"));
        LaunchService.TrashItem deleted = assertSingle(launches.trash(admin, "MANUAL"));
        launches.restoreTrash(deleted.id(), admin);
        LaunchRecord restored = assertSingle(launches.manualOnly(productionDate, productionDate));
        launches.deleteManual(restored.getId(), admin);
        LaunchService.TrashItem deletedAgain = assertSingle(launches.trash(admin, "MANUAL"));
        launches.deleteTrash(deletedAgain.id(), admin);

        assertEquals(0, count("historico_oee"));
        assertEquals(0, count("lancamentos_lixeira"));
    }

    @Test
    void usesAutomaticLaunchMachineForOrderDefaults() {
        catalog.saveMachine(null, "COL DE TAMPA 1", 50_000, "COL DE TAMPA");
        Map<String, Object> production = values(
                "erp_id", 4101L, "ordem", "991001", "data_apon", productionDate.toString(),
                "produto", "7761234567", "maquina", "COL DE TAMPA 1", "turno", "A", "qtd_apon", 10.0);
        Map<String, Object> rejection = values(
                "erp_id", 4102L, "ordem", "991001", "data_apon", productionDate.toString(),
                "produto", "7751234567", "maquina", "HOT AIR 1", "turno", "A", "qtd_refugo", 1.0);
        sync.importBatch("apontamento", List.of(production), "test", "test");
        sync.importBatch("refugo", List.of(rejection), "test", "test");

        LaunchService.OrderLaunchDefaults defaults = launches.orderLaunchDefaults("991001", "COL DE TAMPA", productionDate);
        assertEquals("7761234567", defaults.product());
        assertEquals("COL DE TAMPA 1", defaults.machine());
    }

    @Test
    void factoryLaunchComplementsManualProductionAndKeepsItsOrigin() throws Exception {
        sync.importBatch("refugo", List.of(values(
                "erp_id", 2002L, "data_apon", productionDate.toString(), "ordem", "990003",
                "maquina", "HOT AIR 1", "produto", "7751234567", "turno", "B",
                "qtd_refugo", 2.0, "peso_br", 10.0, "qtd_itens", 200)), "test", "test");
        LaunchRecord factory = manualLaunch("990003", "7761234567", 4_000, 0);
        launches.saveFactoryLaunch(factory, admin);

        assertTrue(factory.getId() > 0);
        LaunchRecord saved = assertSingle(launches.factoryLaunches(admin));
        assertEquals("FABRICA", saved.getOrigin());
        assertEquals(productionDate, saved.getDate());
        assertEquals(4_000, saved.getShiftB());
        assertEquals(2.0, saved.getScrapBKg(), 0.001);
        assertEquals(200, saved.getScrapTotalPcs());
        assertEquals(1, launches.factoryLaunches(admin, productionDate, productionDate).size());
        assertTrue(launches.factoryLaunches(admin, productionDate.plusDays(1), productionDate.plusDays(1)).isEmpty());
        assertEquals(1, launches.manualOnly(productionDate, productionDate).size());

        saved.setShiftB(5_000);
        launches.updateFactoryLaunch(saved, admin);
        assertEquals(5_000, assertSingle(launches.factoryLaunches(admin)).getShiftB());
        assertEquals(2.0, assertSingle(launches.manualOnly(productionDate, productionDate)).getScrapBKg(), 0.001);
        assertEquals(1, count("historico_oee"), "Editar a fábrica deve atualizar o mesmo lançamento");

        launches.deleteManual(saved.getId(), admin);
        assertTrue(launches.factoryLaunches(admin).isEmpty());
        LaunchService.TrashItem trash = assertSingle(launches.trash(admin, "MANUAL"));
        launches.restoreTrash(trash.id(), admin);
        assertEquals("FABRICA", assertSingle(launches.factoryLaunches(admin)).getOrigin());
    }

    private LaunchRecord manualLaunch(String order, String product, int shiftB, double scrapBKg) {
        LaunchRecord record = new LaunchRecord();
        record.setDate(productionDate);
        record.setMachine("HOT AIR 1");
        record.setProduct(product);
        record.setOrderNumber(order);
        record.setScheduledHours(24);
        record.setShiftB(shiftB);
        record.setUnitWeightG(10);
        record.setScrapBKg(scrapBKg);
        return record;
    }

    private long count(String table) throws Exception {
        try (Connection connection = database.open();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return result.next() ? result.getLong(1) : 0;
        }
    }

    private static <T> T assertSingle(List<T> values) {
        assertEquals(1, values.size());
        return values.getFirst();
    }

    private static Map<String, Object> values(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return values;
    }
}
