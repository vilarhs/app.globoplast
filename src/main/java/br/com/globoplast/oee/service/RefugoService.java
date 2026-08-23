package br.com.globoplast.oee.service;

import br.com.globoplast.oee.db.Database;
import br.com.globoplast.oee.model.RefugoRecord;
import br.com.globoplast.oee.model.User;
import br.com.globoplast.oee.util.Norm;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Pipeline analítico de Refugo espelhado do appv723.
 * O staging ERP permanece bruto: Qtd.Planej é convertida de milhares para unidades
 * somente aqui, turnos inválidos são diluídos somente aqui e o Turno C é atribuído
 * ao dia produtivo anterior somente aqui.
 */
@Service
public class RefugoService {
    private static final List<String> VALID_SHIFTS = List.of("A", "B", "C");
    private final Database db;

    public RefugoService(Database db) { this.db = db; }

    public LocalDate[] dateBounds() {
        LocalDate today = Norm.productiveToday(), min = today, max = today;
        String sql = "SELECT MIN(substr(data_apon,1,10)) min_d, MAX(substr(data_apon,1,10)) max_d FROM erp_refugo_raw WHERE data_apon IS NOT NULL AND data_apon<>''";
        try (Connection c = db.open(); PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
            if (r.next()) {
                LocalDate a = Norm.isoDate(r.getString("min_d"));
                LocalDate b = Norm.isoDate(r.getString("max_d"));
                if (a != null) min = a.minusDays(1); // pode existir Turno C no primeiro dia bruto
                if (b != null) max = b;
            }
        } catch (SQLException ignored) {}
        if (today.isBefore(min)) min = today;
        if (today.isAfter(max)) max = today;
        return new LocalDate[]{min, max};
    }

    public List<RefugoRecord> load(LocalDate start, LocalDate end) {
        if (start == null) start = Norm.productiveToday();
        if (end == null) end = start;
        if (end.isBefore(start)) { LocalDate t = start; start = end; end = t; }

        LocalDate rawEnd = end.plusDays(1);
        List<RawScrap> rawRows = new ArrayList<>();
        String sql = "SELECT erp_id,data_apon,ordem,qtd_planej,maquina,produto,descricao,cliente,turno,operador,qtd_refugo,motivo,peso_br,qtd_itens,COALESCE(primeiro_sincronizado_em,sincronizado_em) primeiro_sincronizado_em " +
                "FROM erp_refugo_raw WHERE data_apon BETWEEN ? AND ? ORDER BY erp_id DESC";

        try (Connection c = db.open(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, start.toString());
            p.setString(2, rawEnd.toString());
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) rawRows.add(raw(r));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

        Map<Long, String> sectorOverrides = loadSectorOverrides();
        if (!sectorOverrides.isEmpty()) {
            rawRows.replaceAll(row -> sectorOverrides.containsKey(row.erpId())
                    ? withSector(row, sectorOverrides.get(row.erpId()))
                    : row);
        }

        // Mesmo mapa do Python: turnos válidos encontrados em Setor + OP.
        Map<String, LinkedHashSet<String>> validBySectorOrder = new LinkedHashMap<>();
        for (RawScrap row : rawRows) {
            if (VALID_SHIFTS.contains(row.shift())) {
                validBySectorOrder
                        .computeIfAbsent(row.sector() + "¦" + row.orderNumber(), k -> new LinkedHashSet<>())
                        .add(row.shift());
            }
        }

        List<RefugoRecord> out = new ArrayList<>();
        for (RawScrap row : rawRows) {
            if (VALID_SHIFTS.contains(row.shift())) {
                emit(out, row, row.shift(), row.scrapKg(), row.itemCount(), start, end);
                continue;
            }

            LinkedHashSet<String> found = validBySectorOrder.get(row.sector() + "¦" + row.orderNumber());
            List<String> destinations = new ArrayList<>();
            if (found != null) {
                for (String shift : VALID_SHIFTS) if (found.contains(shift)) destinations.add(shift);
            }
            if (destinations.isEmpty()) destinations.addAll(VALID_SHIFTS);

            double divisor = destinations.size();
            for (String shift : destinations) {
                emit(out, row, shift, row.scrapKg() / divisor, row.itemCount() / divisor, start, end);
            }
        }
        Map<String, String> analysisOverrides = loadAnalysisSectorOverrides();
        if (!analysisOverrides.isEmpty()) {
            out.replaceAll(row -> analysisOverrides.containsKey(row.analysisId())
                    ? withSector(row, analysisOverrides.get(row.analysisId()))
                    : row);
        }
        return out;
    }

    private RawScrap raw(ResultSet r) throws SQLException {
        long erpId = r.getLong("erp_id");
        LocalDate rawDate = Norm.isoDate(r.getString("data_apon"));
        String order = informed(Norm.order(r.getString("ordem")));
        String product = informed(Norm.product(r.getString("produto")));
        String sector = Norm.scrapSector(product);
        String machine = informed(Norm.text(r.getString("maquina")));
        if ("777021".equals(product)) machine = "NÃO INFORMADO";
        String description = informed(Norm.text(r.getString("descricao")));
        String client = informed(Norm.text(r.getString("cliente")));
        String shift = informed(Norm.token(r.getString("turno")));
        String operator = informed(Norm.text(r.getString("operador"))).toUpperCase(Locale.ROOT);
        double scrapKg = Math.max(0.0, r.getDouble("qtd_refugo"));
        double unitWeightG = Math.max(0.0, r.getDouble("peso_br"));

        // Regra oficial: Qtd.Planej do DealerSystem está em milhares de peças.
        double plannedUnits = Math.max(0.0, r.getDouble("qtd_planej")) * 1000.0;

        // Se Qtd Itens veio do ERP, usa exatamente o valor recebido. Somente NULL usa fallback.
        Object itemObject = r.getObject("qtd_itens");
        double itemCount;
        if (itemObject instanceof Number n) {
            itemCount = Math.max(0.0, n.doubleValue());
        } else if (unitWeightG > 0.0) {
            itemCount = Math.rint((scrapKg * 1000.0) / unitWeightG);
        } else {
            itemCount = 0.0;
        }

        String motive = Norm.scrapMotive(product, r.getString("motivo"));
        if (motive == null || motive.isBlank()) motive = "NÃO INFORMADO";

        return new RawScrap(
                erpId, rawDate, order, plannedUnits, machine, product, description, client,
                shift, operator, scrapKg, motive, unitWeightG, itemCount, sector,
                informed(Norm.text(r.getString("primeiro_sincronizado_em")))
        );
    }

    private void emit(List<RefugoRecord> out, RawScrap row, String shift, double kg, double items,
                      LocalDate start, LocalDate end) {
        LocalDate productive = Norm.productiveScrapDate(row.rawDate(), shift, row.firstDetectedAt());
        if (productive == null || productive.isBefore(start) || productive.isAfter(end)) return;
        String analysisId = row.erpId() + "¦" + shift;
        out.add(new RefugoRecord(
                row.erpId(), analysisId, productive, row.rawDate(), row.orderNumber(), row.plannedQty(),
                row.machine(), row.product(), row.description(), row.client(), shift, row.operator(),
                kg, row.motive(), row.unitWeightG(), items, row.sector(), row.firstDetectedAt()
        ));
    }

    private static String informed(String value) {
        String v = value == null ? "" : value.trim();
        return v.isBlank() ? "NÃO INFORMADO" : v;
    }

    /**
     * Reclassifica o lançamento apenas na camada analítica do Java. O registro
     * bruto sincronizado do ERP permanece imutável e pode ser reprocessado sem
     * perder a escolha feita pelo administrador.
     */
    public int reassignSector(Collection<RefugoRecord> rows, String sector, User actor) {
        if (actor == null || !actor.isAdmin()) {
            throw new IllegalArgumentException("Somente um Administrador pode enviar lançamentos para outro setor.");
        }
        String target = sector == null ? "" : sector.trim().toUpperCase(Locale.ROOT);
        if (target.isBlank()) throw new IllegalArgumentException("Selecione o setor de destino.");
        Map<String, Long> ids = new LinkedHashMap<>();
        if (rows != null) {
            for (RefugoRecord row : rows) {
                if (row != null && row.erpId() > 0 && row.analysisId() != null && !row.analysisId().isBlank()) {
                    ids.put(row.analysisId(), row.erpId());
                }
            }
        }
        if (ids.isEmpty()) throw new IllegalArgumentException("Selecione um lançamento no gráfico.");

        try (Connection c = db.open()) {
            try (PreparedStatement sectorCheck = c.prepareStatement("SELECT setor FROM setores WHERE setor=? COLLATE NOCASE LIMIT 1")) {
                sectorCheck.setString(1, target);
                try (ResultSet rs = sectorCheck.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("O setor de destino não existe no cadastro.");
                    target = rs.getString("setor").trim().toUpperCase(Locale.ROOT);
                }
            }
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO erp_refugo_analysis_setor_overrides(analysis_id,erp_id,setor,atualizado_por,atualizado_em) VALUES(?,?,?,?,CURRENT_TIMESTAMP) " +
                            "ON CONFLICT(analysis_id) DO UPDATE SET erp_id=excluded.erp_id,setor=excluded.setor,atualizado_por=excluded.atualizado_por,atualizado_em=CURRENT_TIMESTAMP")) {
                for (var entry : ids.entrySet()) {
                    p.setString(1, entry.getKey());
                    p.setLong(2, entry.getValue());
                    p.setString(3, target);
                    p.setString(4, actor.username());
                    p.addBatch();
                }
                p.executeBatch();
            }
            c.commit();
            return ids.size();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível alterar o setor dos lançamentos selecionados.", e);
        }
    }

    /**
     * Restaura a classificação original calculada a partir do ERP. A limpeza
     * remove tanto as reclassificações analíticas atuais quanto os overrides
     * legados, sem alterar nenhum registro bruto sincronizado.
     */
    public int clearSectorReassignments(User actor) {
        if (actor == null || !actor.isAdmin()) {
            throw new IllegalArgumentException("Somente um Administrador pode restaurar os setores originais.");
        }
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            int changed;
            try (Statement s = c.createStatement()) {
                changed = s.executeUpdate("DELETE FROM erp_refugo_analysis_setor_overrides");
                changed += s.executeUpdate("DELETE FROM erp_refugo_setor_overrides");
            }
            c.commit();
            return changed;
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível restaurar os setores originais do Refugo.", e);
        }
    }

    private Map<Long, String> loadSectorOverrides() {
        Map<Long, String> out = new HashMap<>();
        try (Connection c = db.open(); PreparedStatement p = c.prepareStatement(
                "SELECT erp_id,setor FROM erp_refugo_setor_overrides"); ResultSet r = p.executeQuery()) {
            while (r.next()) {
                String sector = r.getString("setor");
                if (sector != null && !sector.isBlank()) out.put(r.getLong("erp_id"), sector.trim().toUpperCase(Locale.ROOT));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private Map<String, String> loadAnalysisSectorOverrides() {
        Map<String, String> out = new HashMap<>();
        try (Connection c = db.open(); PreparedStatement p = c.prepareStatement(
                "SELECT analysis_id,setor FROM erp_refugo_analysis_setor_overrides"); ResultSet r = p.executeQuery()) {
            while (r.next()) {
                String id = r.getString("analysis_id");
                String sector = r.getString("setor");
                if (id != null && !id.isBlank() && sector != null && !sector.isBlank()) {
                    out.put(id, sector.trim().toUpperCase(Locale.ROOT));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private static RawScrap withSector(RawScrap row, String sector) {
        return new RawScrap(row.erpId(), row.rawDate(), row.orderNumber(), row.plannedQty(), row.machine(),
                row.product(), row.description(), row.client(), row.shift(), row.operator(), row.scrapKg(),
                row.motive(), row.unitWeightG(), row.itemCount(), sector, row.firstDetectedAt());
    }

    private static RefugoRecord withSector(RefugoRecord row, String sector) {
        return new RefugoRecord(row.erpId(), row.analysisId(), row.productiveDate(), row.rawDate(),
                row.orderNumber(), row.plannedQty(), row.machine(), row.product(), row.description(),
                row.client(), row.shift(), row.operator(), row.scrapKg(), row.motive(), row.unitWeightG(),
                row.itemCount(), sector, row.firstDetectedAt());
    }

    public List<RefugoRecord> filter(List<RefugoRecord> src, String search, Set<String> sectors,
                                     Set<String> orders, Set<String> machines, Set<String> products,
                                     Set<String> descriptions, Set<String> clients, Set<String> shifts,
                                     Set<String> operators, Set<String> motives) {
        List<RefugoRecord> x = new ArrayList<>(src);
        if (sectors != null && !sectors.isEmpty()) x.removeIf(r -> sectors.stream().noneMatch(s -> s.equalsIgnoreCase(r.sector())));
        if (orders != null && !orders.isEmpty()) x.removeIf(r -> !orders.contains(r.orderNumber()));
        if (machines != null && !machines.isEmpty()) x.removeIf(r -> !machines.contains(r.machine()));
        if (products != null && !products.isEmpty()) x.removeIf(r -> !products.contains(r.product()));
        if (descriptions != null && !descriptions.isEmpty()) x.removeIf(r -> !descriptions.contains(r.description()));
        if (clients != null && !clients.isEmpty()) x.removeIf(r -> !clients.contains(r.client()));
        if (shifts != null && !shifts.isEmpty()) x.removeIf(r -> !shifts.contains(r.shift()));
        if (operators != null && !operators.isEmpty()) x.removeIf(r -> !operators.contains(r.operator()));
        if (motives != null && !motives.isEmpty()) x.removeIf(r -> !motives.contains(Norm.scrapMotiveUid(r.product(), r.motive())));
        String q = search == null ? "" : search.trim();
        if (!q.isBlank()) {
            String op = Norm.order(q), prod = Norm.product(q);
            List<RefugoRecord> exact = x.stream()
                    .filter(r -> Norm.order(r.orderNumber()).equals(op) || Norm.product(r.product()).equals(prod))
                    .toList();
            if (!exact.isEmpty()) x = new ArrayList<>(exact);
            else {
                String f = Norm.fold(q);
                x.removeIf(r -> !Norm.fold(r.orderNumber() + " " + r.product() + " " + r.description()).contains(f));
            }
        }
        return x;
    }

    public Map<String, Double> aggregate(List<RefugoRecord> rows, String dimension) {
        Map<String, Double> m = new HashMap<>();
        for (RefugoRecord r : rows) {
            String k = switch (dimension) {
                case "Máquina" -> r.machine();
                case "Turno" -> r.shift();
                case "Descrição" -> r.description();
                case "Motivo" -> r.motive();
                default -> r.sector();
            };
            if (k == null || k.isBlank()) k = "NÃO INFORMADO";
            m.merge(k, r.scrapKg(), Double::sum);
        }
        List<Map.Entry<String, Double>> sorted = m.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).toList();
        Map<String, Double> out = new LinkedHashMap<>();
        for (var e : sorted) out.put(e.getKey(), Norm.round(e.getValue(), 3));
        return out;
    }

    public double totalKg(List<RefugoRecord> rows) {
        return Norm.round(rows.stream().mapToDouble(RefugoRecord::scrapKg).sum(), 3);
    }

    public long orders(List<RefugoRecord> rows) {
        return rows.stream().map(RefugoRecord::orderNumber).filter(s -> s != null && !s.isBlank()).distinct().count();
    }

    private record RawScrap(
            long erpId, LocalDate rawDate, String orderNumber, double plannedQty, String machine,
            String product, String description, String client, String shift, String operator,
            double scrapKg, String motive, double unitWeightG, double itemCount, String sector,
            String firstDetectedAt
    ) {}
}
