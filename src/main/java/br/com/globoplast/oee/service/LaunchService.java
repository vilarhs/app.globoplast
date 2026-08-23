package br.com.globoplast.oee.service;

import br.com.globoplast.oee.config.AppConfig;
import br.com.globoplast.oee.db.Database;
import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.model.User;
import br.com.globoplast.oee.util.Norm;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LaunchService {
    private static final List<String> TRACKED_ORDER_PROCESSES = List.of("770", "771", "772", "773", "775", "776");
    private static final String PRODUCT_METADATA_SQL = "SELECT descricao,cliente FROM erp_apontamento_raw " +
            "WHERE UPPER(REPLACE(TRIM(produto),' ',''))=? " +
            "AND (TRIM(COALESCE(descricao,''))<>'' OR TRIM(COALESCE(cliente,''))<>'') " +
            "ORDER BY sincronizado_em DESC, erp_id DESC LIMIT 50";
    private final Database db; private final CatalogService catalog; private final OeeCalculator oee; private final JsonMapper json;
    public LaunchService(Database db,CatalogService catalog,OeeCalculator oee,JsonMapper json){this.db=db;this.catalog=catalog;this.oee=oee;this.json=json;}

    public LocalDate[] dateBounds(){
        LocalDate today=Norm.productiveToday(),min=today,max=today;
        String sql="SELECT MIN(d) min_d, MAX(d) max_d FROM ("+
                "SELECT substr(data,1,10) d FROM historico_oee "+
                "UNION ALL SELECT substr(data_apon,1,10) d FROM erp_apontamento_raw"+
                ") WHERE d IS NOT NULL AND d<>''";
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){
            if(r.next()){
                LocalDate a=Norm.isoDate(r.getString("min_d")),b=Norm.isoDate(r.getString("max_d"));
                if(a!=null)min=a;if(b!=null)max=b;
            }
        }catch(SQLException ignored){}
        try(Connection c=db.open();Statement st=c.createStatement();ResultSet rs=st.executeQuery(
                "SELECT payload_json FROM erp_lancamento_overrides WHERE oculto=0 AND payload_json IS NOT NULL")){
            while(rs.next()){
                try{
                    Map<String,Object> payload=json.readValue(rs.getString(1),new TypeReference<>(){});
                    LocalDate d=Norm.isoDate(payload.get("date"));
                    if(d!=null){if(d.isBefore(min))min=d;if(d.isAfter(max))max=d;}
                }catch(Exception ignored){}
            }
        }catch(SQLException ignored){}
        if(today.isBefore(min))min=today;if(today.isAfter(max))max=today;
        return new LocalDate[]{min,max};
    }

    public List<LaunchRecord> all(LocalDate start,LocalDate end){
        if(start==null)start=Norm.productiveToday();
        if(end==null)end=start;
        if(end.isBefore(start)){LocalDate t=start;start=end;end=t;}

        final LocalDate requestedStart=start,requestedEnd=end;
        LocalDate[] sourceBounds=sourceBoundsForOverrides(requestedStart,requestedEnd);
        List<LaunchRecord> auto=automatic(sourceBounds[0],sourceBounds[1],sourceBounds[0],sourceBounds[1]);
        auto.removeIf(r->r.getDate()==null||r.getDate().isBefore(requestedStart)||r.getDate().isAfter(requestedEnd));
        List<LaunchRecord> manual=manual(requestedStart,requestedEnd);
        fillMissingProductMetadata(manual);

        // MANUAL e ERP são fontes complementares. Não descartamos o manual só
        // porque Data+OP+Máquina+Produto coincidem: um lançamento manual pode
        // representar uma complementação real da produção automática.
        attachErpScrapToManualOnlyWhenNeeded(manual,auto,requestedStart,requestedEnd);

        // automatic() já reconciliou os itens ERP. Aqui completamos somente
        // os manuais, evitando repetir todo o trabalho e as gravações auxiliares.
        ensureCatalogMetadata(manual,catalog.machineMap());

        List<LaunchRecord> out=new ArrayList<>(auto.size()+manual.size());
        out.addAll(auto);
        out.addAll(manual);
        applyOrderProgress(out);

        // Regra atual: Máquina + Dia produtivo representa uma única operação de
        // 24h e uma única capacidade, mesmo com várias OPs/linhas/turnos.
        oee.recalculate(out);

        out.sort(recentFirst());
        return out;
    }

    public ProductMetadata productMetadata(String productCode) {
        String product = Norm.product(productCode);
        if (product.isBlank()) return new ProductMetadata("", "");
        String description = "";
        String client = "";
        try (Connection c = db.open(); PreparedStatement p = c.prepareStatement(PRODUCT_METADATA_SQL)) {
            p.setString(1, product);
            try (ResultSet r = p.executeQuery()) {
                while (r.next() && (description.isBlank() || client.isBlank())) {
                    if (description.isBlank()) description = Norm.text(r.getString("descricao"));
                    if (client.isBlank()) client = Norm.text(r.getString("cliente"));
                }
            }
        } catch (SQLException ignored) {
        }
        return new ProductMetadata(description, client);
    }

    public String productDescription(String productCode) {
        return productMetadata(productCode).description();
    }

    /**
     * Retorna o realizado acumulado de uma OP, separado por processo e produto.
     * A OP faz parte obrigatória do filtro, pois um produto pode existir em
     * várias ordens diferentes.
     */
    public List<OrderProcessProgress> orderProcessProgress(String orderNumber) {
        String order = Norm.order(orderNumber);
        if (!order.matches("\\d+")) return List.of();
        long orderValue;
        try { orderValue = Long.parseLong(order); }
        catch (NumberFormatException ignored) { return List.of(); }

        String placeholders = String.join(",", Collections.nCopies(TRACKED_ORDER_PROCESSES.size(), "?"));
        String sql = "SELECT CAST(ordem AS TEXT) ordem,SUBSTR(TRIM(produto),1,3) codigo_processo,produto," +
                "MAX(COALESCE(descricao,'')) descricao,MAX(qtd_plan) programado," +
                "MAX(COALESCE(qtd_prod,0)) produzido,MIN(data_plan) primeira_data,MAX(data_plan) ultima_data " +
                "FROM erp_planejamento_raw WHERE ordem=? " +
                "AND SUBSTR(TRIM(produto),1,3) IN (" + placeholders + ") " +
                "GROUP BY ordem,codigo_processo,produto ORDER BY CASE codigo_processo " +
                "WHEN '770' THEN 1 WHEN '771' THEN 2 WHEN '772' THEN 3 WHEN '773' THEN 4 " +
                "WHEN '775' THEN 5 WHEN '776' THEN 6 ELSE 99 END,produto";

        List<OrderProcessProgress> out = new ArrayList<>();
        try (Connection c = db.open(); PreparedStatement p = c.prepareStatement(sql)) {
            int index = 1;
            p.setLong(index++, orderValue);
            for (String process : TRACKED_ORDER_PROCESSES) p.setString(index++, process);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    String process = Norm.text(r.getString("codigo_processo"));
                    int planned = (int) Math.round(Math.max(0, r.getDouble("programado")) * 1000.0);
                    int produced = (int) Math.round(Math.max(0, r.getDouble("produzido")) * 1000.0);
                    out.add(new OrderProcessProgress(
                            Norm.text(r.getString("ordem")), process, Norm.scrapSector(process),
                            Norm.text(r.getString("produto")), Norm.text(r.getString("descricao")),
                            planned, produced, Math.max(0, planned - produced),
                            Norm.isoDate(r.getString("primeira_data")), Norm.isoDate(r.getString("ultima_data"))));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private void fillMissingProductMetadata(List<LaunchRecord> records) {
        if (records == null || records.isEmpty()) return;
        Map<String,List<LaunchRecord>> missingByProduct = new LinkedHashMap<>();
        for (LaunchRecord record : records) {
            if (record == null) continue;
            if (!Norm.text(record.getDescriptionErp()).isBlank() && !Norm.text(record.getClientErp()).isBlank()) continue;
            String product = Norm.product(record.getProduct());
            if (product.isBlank()) continue;
            missingByProduct.computeIfAbsent(product, ignored -> new ArrayList<>()).add(record);
        }
        for (Map.Entry<String,List<LaunchRecord>> entry : missingByProduct.entrySet()) {
            ProductMetadata metadata = productMetadata(entry.getKey());
            for (LaunchRecord record : entry.getValue()) {
                if (Norm.text(record.getDescriptionErp()).isBlank()) record.setDescriptionErp(metadata.description());
                if (Norm.text(record.getClientErp()).isBlank()) record.setClientErp(metadata.client());
            }
        }
    }

    private void applyOrderProgress(List<LaunchRecord> records) {
        if (records == null || records.isEmpty()) return;
        Set<String> relevant = records.stream()
                .filter(record -> !Norm.order(record.getOrderNumber()).isBlank())
                .filter(record -> !Norm.product(record.getProduct()).isBlank())
                .map(record -> orderProgressKey(record.getOrderNumber(), record.getProduct()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (relevant.isEmpty()) return;

        Map<String,double[]> progress = new HashMap<>();
        String sql = "SELECT ordem,produto,MAX(qtd_plan) qtd_plan,MAX(COALESCE(qtd_prod,0)) qtd_prod " +
                "FROM erp_planejamento_raw WHERE ordem IS NOT NULL AND TRIM(COALESCE(produto,''))<>'' " +
                "GROUP BY ordem,produto";
        try (Connection c = db.open(); PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
            while (r.next()) {
                String key = orderProgressKey(r.getString("ordem"), r.getString("produto"));
                if (!relevant.contains(key)) continue;
                double[] values = progress.computeIfAbsent(key, ignored -> new double[2]);
                Object planned = r.getObject("qtd_plan");
                if (planned instanceof Number number) values[0] = Math.max(values[0], number.doubleValue());
                Object launched = r.getObject("qtd_prod");
                if (launched instanceof Number number) values[1] += Math.max(0, number.doubleValue());
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

        for (LaunchRecord record : records) {
            double[] values = progress.get(orderProgressKey(record.getOrderNumber(), record.getProduct()));
            if (values == null || values[0] <= 0) continue;
            record.setOrderProgressAvailable(true);
            record.setOrderPlannedPcs((int) Math.round(values[0] * 1000.0));
            record.setOrderLaunchedPcs((int) Math.round(values[1] * 1000.0));
        }
    }

    private static String orderProgressKey(Object order, Object product) {
        return Norm.order(order) + "|" + Norm.product(product);
    }

    private static Comparator<LaunchRecord> recentFirst() {
        return Comparator.comparingLong(LaunchService::movementEpoch).reversed()
                .thenComparing(LaunchRecord::getDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LaunchService::launchTimeKey, Comparator.reverseOrder())
                .thenComparing(LaunchRecord::getId, Comparator.reverseOrder());
    }

    public List<LaunchRecord> newestFirst(Collection<LaunchRecord> source) {
        List<LaunchRecord> out = source == null ? new ArrayList<>() : new ArrayList<>(source);
        out.sort(recentFirst());
        return out;
    }

    private static long movementEpoch(LaunchRecord r) {
        String value = r == null ? "" : Norm.text(r.getMovementAt());
        if (!value.isBlank()) {
            try { return Instant.parse(value).toEpochMilli(); } catch (Exception ignored) { }
            try { return ZonedDateTime.parse(value).toInstant().toEpochMilli(); } catch (Exception ignored) { }
            try { return OffsetDateTime.parse(value).toInstant().toEpochMilli(); } catch (Exception ignored) { }
            try { return LocalDateTime.parse(value).atZone(AppConfig.ZONE).toInstant().toEpochMilli(); } catch (Exception ignored) { }
        }
        if (r != null && r.getDate() != null) {
            try {
                String time = launchTimeKey(r);
                return r.getDate().atTime(LocalTime.parse(time)).atZone(AppConfig.ZONE).toInstant().toEpochMilli();
            } catch (Exception ignored) { }
            return r.getDate().atStartOfDay(AppConfig.ZONE).toInstant().toEpochMilli();
        }
        return Long.MIN_VALUE;
    }

    private static void applyMovementIfLater(LaunchRecord record, Object timestamp, boolean updateVisibleTime) {
        String candidate = Norm.text(timestamp);
        if (record == null || candidate.isBlank()) return;
        LaunchRecord probe = new LaunchRecord();
        probe.setMovementAt(candidate);
        if (movementEpoch(probe) <= movementEpoch(record)) return;
        record.setMovementAt(candidate);
        if (updateVisibleTime) {
            String time = Norm.syncTime(candidate);
            if (!time.isBlank()) record.setLaunchTime(time);
        }
    }

    private static String launchTimeKey(LaunchRecord r) {
        String value = r == null ? "" : Norm.text(r.getLaunchTime());
        return value.isBlank() ? "00:00:00" : value;
    }

    public List<LaunchRecord> filter(List<LaunchRecord> source,String search,Set<String> sectors,LocalDate start,LocalDate end,Set<String> machines,Set<String> clients){
        List<LaunchRecord> out=new ArrayList<>(source);
        if(sectors!=null&&!sectors.isEmpty())out.removeIf(r->!sectors.contains(r.getSector()));
        if(start!=null&&end!=null)out.removeIf(r->r.getDate()==null||r.getDate().isBefore(start)||r.getDate().isAfter(end));
        if(machines!=null&&!machines.isEmpty())out.removeIf(r->!machines.contains(r.getMachine()));
        if(clients!=null&&!clients.isEmpty())out.removeIf(r->!clients.contains(r.getClientErp()));
        String q=search==null?"":search.trim();
        if(!q.isBlank()){
            String f=Norm.fold(q);
            out.removeIf(r -> !Norm.fold(
                    r.getOrderNumber()+" "+r.getProduct()+" "+r.getMachine()+" "+r.getSector()+" "+r.getClientErp()+" "+r.getDescriptionErp()
            ).contains(f));
        }
        out.sort(recentFirst());return out;
    }

    public List<LaunchRecord> manual(LocalDate start,LocalDate end){
        List<LaunchRecord> out=new ArrayList<>();
        Map<String,Machine> machines=catalog.machineMap();
        String sql="SELECT * FROM historico_oee WHERE data BETWEEN ? AND ? ORDER BY data DESC,id DESC";
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,start.toString());
            p.setString(2,end.toString());
            ResultSet r=p.executeQuery();
            while(r.next()){
                LaunchRecord item=mapManual(r);
                Machine machine=resolveMachine(machines,item.getMachine());
                if(machine!=null){
                    item.setSector(machine.sector());
                    if(item.getCapacity24h()<=0)item.setCapacity24h(machine.capacity());
                }
                out.add(item);
            }
        }catch(SQLException e){throw new IllegalStateException(e);}
        return out;
    }

    public void saveManual(LaunchRecord r,User user){
        if(user==null||!user.canModifyLaunches())throw new IllegalArgumentException("Seu perfil não permite criar lançamentos.");
        Machine m=catalog.machineMap().get(r.getMachine());
        if(m==null)throw new IllegalArgumentException("Máquina inválida.");
        if(!user.isAdmin()&&(user.sector()==null||!user.sector().equalsIgnoreCase(m.sector())))throw new IllegalArgumentException("Seu perfil não permite lançar dados para o setor desta máquina.");
        ZonedDateTime now=ZonedDateTime.now(AppConfig.ZONE);
        r.setSector(m.sector());r.setCapacity24h(m.capacity());r.setErp(false);
        r.setLaunchTime(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        r.setMovementAt(now.toString());
        finalizeManual(r);
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement("INSERT INTO historico_oee(data,data_br,maquina,produto,numero_op,op_producao_detalhe,horas_programadas,capacidade_24h,turno_a_pcs,turno_b_pcs,turno_c_pcs,total_produzido_pcs,peso_unitario_g,refugo_a_kg,refugo_b_kg,refugo_c_kg,refugo_total_kg,refugo_total_pcs,refugo_pct,qtd_trocas,tempo_setup_hrs,horas_paradas_quebra,tempo_produzindo_hrs,disponibilidade_pct,desempenho_pct,qualidade_pct,oee_pct,problema,acao_tomada,hora_lancamento,movimentado_em) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){
            bindManual(p,r);p.setString(31,r.getMovementAt());p.executeUpdate();
        }catch(SQLException e){throw new IllegalStateException(e);}
        recalculateManualDay(r.getDate(),r.getMachine());
    }

    public void updateManual(LaunchRecord r,User user){
        if(r.isErp())throw new IllegalArgumentException("Use override para lançamento ERP.");
        if(user==null||!user.canModifyLaunches())throw new IllegalArgumentException("Sem permissão.");
        Machine m=catalog.machineMap().get(r.getMachine());
        if(m==null)throw new IllegalArgumentException("Máquina inválida.");
        if(!user.isAdmin()&&(user.sector()==null||!user.sector().equalsIgnoreCase(m.sector())))
            throw new IllegalArgumentException("Seu perfil não permite mover o lançamento para outro setor.");
        r.setSector(m.sector());
        r.setCapacity24h(m.capacity());

        LocalDate oldDate=null;
        String oldMachine=null;
        try(Connection c=db.open()){
            try(PreparedStatement q=c.prepareStatement("SELECT data,maquina,hora_lancamento FROM historico_oee WHERE id=? LIMIT 1")){
                q.setLong(1,r.getId());
                ResultSet rs=q.executeQuery();
                if(rs.next()){
                    oldDate=LocalDate.parse(rs.getString("data"));
                    oldMachine=rs.getString("maquina");
                    if(r.getLaunchTime()==null||r.getLaunchTime().isBlank())r.setLaunchTime(rs.getString("hora_lancamento"));
                }
            }
            finalizeManual(r);
            ZonedDateTime now=ZonedDateTime.now(AppConfig.ZONE);
            r.setLaunchTime(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            r.setMovementAt(now.toString());
            try(PreparedStatement p=c.prepareStatement("UPDATE historico_oee SET data=?,data_br=?,maquina=?,produto=?,numero_op=?,op_producao_detalhe=?,horas_programadas=?,capacidade_24h=?,turno_a_pcs=?,turno_b_pcs=?,turno_c_pcs=?,total_produzido_pcs=?,peso_unitario_g=?,refugo_a_kg=?,refugo_b_kg=?,refugo_c_kg=?,refugo_total_kg=?,refugo_total_pcs=?,refugo_pct=?,qtd_trocas=?,tempo_setup_hrs=?,horas_paradas_quebra=?,tempo_produzindo_hrs=?,disponibilidade_pct=?,desempenho_pct=?,qualidade_pct=?,oee_pct=?,problema=?,acao_tomada=?,hora_lancamento=?,movimentado_em=? WHERE id=?")){
                bindManual(p,r);
                p.setString(31,r.getMovementAt());
                p.setLong(32,r.getId());
                p.executeUpdate();
            }
        }catch(SQLException e){throw new IllegalStateException(e);}
        recalculateManualDay(r.getDate(),r.getMachine());
        if(oldDate!=null&&oldMachine!=null&&(!oldDate.equals(r.getDate())||!oldMachine.equals(r.getMachine())))
            recalculateManualDay(oldDate,oldMachine);
    }

    public void deleteManual(long id,User user){
        LocalDate d=null;String machine=null;
        try(Connection c=db.open()){
            c.setAutoCommit(false);
            try{
                LaunchRecord snapshot=null;
                try(PreparedStatement q=c.prepareStatement("SELECT * FROM historico_oee WHERE id=?")){
                    q.setLong(1,id);ResultSet rs=q.executeQuery();
                    if(rs.next())snapshot=mapManual(rs);
                }
                if(snapshot==null)throw new IllegalArgumentException("Lançamento não encontrado.");
                d=snapshot.getDate();machine=snapshot.getMachine();
                ensureUserCanActOnMachine(user,machine);
                putInTrash(c,"MANUAL",String.valueOf(id),snapshot,false,null,user);
                try(PreparedStatement p=c.prepareStatement("DELETE FROM historico_oee WHERE id=?")){p.setLong(1,id);p.executeUpdate();}
                c.commit();
            }catch(Exception e){
                try{c.rollback();}catch(SQLException ignored){}
                if(e instanceof IllegalArgumentException iae)throw iae;
                if(e instanceof SQLException se)throw se;
                throw new IllegalStateException(e);
            }
        }catch(IllegalArgumentException e){throw e;}catch(SQLException e){throw new IllegalStateException(e);}
        if(d!=null)recalculateManualDay(d,machine);
    }

    public void saveErpOverride(LaunchRecord r,User user){
        if(r==null||!r.isErp())throw new IllegalArgumentException("Lançamento ERP inválido.");
        ensureUserCanActOnRecord(user,r);
        rememberMachineMetadata(List.of(r));
        try{
            Map<String,Object>m=payload(r);String body=json.writeValueAsString(m);
            try(Connection c=db.open();PreparedStatement p=c.prepareStatement("INSERT INTO erp_lancamento_overrides(erp_chave,oculto,payload_json,atualizado_em) VALUES(?,0,?,?) ON CONFLICT(erp_chave) DO UPDATE SET oculto=0,payload_json=excluded.payload_json,atualizado_em=excluded.atualizado_em")){
                p.setString(1,r.getErpKey());p.setString(2,body);p.setString(3,ZonedDateTime.now(AppConfig.ZONE).toString());p.executeUpdate();
            }
        }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}
    }

    public void hideErp(LaunchRecord r,User user){
        if(r==null||!r.isErp())throw new IllegalArgumentException("Lançamento ERP inválido.");
        ensureUserCanActOnRecord(user,r);
        rememberMachineMetadata(List.of(r));
        try(Connection c=db.open()){
            c.setAutoCommit(false);
            try{
                boolean previousExists=false;String previousPayload=null;
                try(PreparedStatement q=c.prepareStatement("SELECT payload_json FROM erp_lancamento_overrides WHERE erp_chave=?")){
                    q.setString(1,r.getErpKey());ResultSet rs=q.executeQuery();
                    if(rs.next()){previousExists=true;previousPayload=rs.getString(1);}
                }
                putInTrash(c,"ERP",r.getErpKey(),r,previousExists,previousPayload,user);
                try(PreparedStatement p=c.prepareStatement("INSERT INTO erp_lancamento_overrides(erp_chave,oculto,payload_json,atualizado_em) VALUES(?,1,NULL,?) ON CONFLICT(erp_chave) DO UPDATE SET oculto=1,payload_json=NULL,atualizado_em=excluded.atualizado_em")){
                    p.setString(1,r.getErpKey());p.setString(2,ZonedDateTime.now(AppConfig.ZONE).toString());p.executeUpdate();
                }
                c.commit();
            }catch(Exception e){
                try{c.rollback();}catch(SQLException ignored){}
                if(e instanceof IllegalArgumentException iae)throw iae;
                if(e instanceof SQLException se)throw se;
                throw new IllegalStateException(e);
            }
        }catch(IllegalArgumentException e){throw e;}catch(SQLException e){throw new IllegalStateException(e);}
    }

    public List<TrashItem> trash(User user){
        cleanupExpiredTrash();
        if(user==null||!user.canModifyLaunches())return List.of();
        List<TrashItem> out=new ArrayList<>();
        String sql="SELECT id,tipo,payload_json,excluido_por,excluido_em,expira_em FROM lancamentos_lixeira ORDER BY excluido_em DESC,id DESC";
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement(sql);ResultSet rs=p.executeQuery()){
            while(rs.next()){
                LaunchRecord record=recordFromTrashJson(rs.getString("payload_json"));
                if(record==null||!canUserActOnTrashRecord(user,record))continue;
                out.add(new TrashItem(rs.getLong("id"),rs.getString("tipo"),record,rs.getString("excluido_por"),rs.getString("excluido_em"),rs.getString("expira_em")));
            }
        }catch(Exception e){throw new IllegalStateException(e);}
        return out;
    }

    public void restoreTrash(long trashId,User user){
        cleanupExpiredTrash();
        LocalDate manualDate=null;String manualMachine=null;
        try(Connection c=db.open()){
            c.setAutoCommit(false);
            try{
                String type=null,key=null,payloadJson=null,previousPayload=null;boolean previousExists=false;
                try(PreparedStatement q=c.prepareStatement("SELECT tipo,chave_origem,payload_json,override_anterior_existia,override_anterior_json FROM lancamentos_lixeira WHERE id=?")){
                    q.setLong(1,trashId);ResultSet rs=q.executeQuery();
                    if(rs.next()){
                        type=rs.getString("tipo");key=rs.getString("chave_origem");payloadJson=rs.getString("payload_json");
                        previousExists=rs.getInt("override_anterior_existia")==1;previousPayload=rs.getString("override_anterior_json");
                    }
                }
                if(type==null)throw new IllegalArgumentException("Lançamento não encontrado na lixeira ou já expirou.");
                LaunchRecord record=recordFromTrashJson(payloadJson);
                if(record==null)throw new IllegalArgumentException("Lançamento da lixeira inválido.");
                if("ERP".equalsIgnoreCase(type)){
                    ensureUserCanActOnRecord(user,record);
                    if(previousExists){
                        try(PreparedStatement p=c.prepareStatement("INSERT INTO erp_lancamento_overrides(erp_chave,oculto,payload_json,atualizado_em) VALUES(?,0,?,?) ON CONFLICT(erp_chave) DO UPDATE SET oculto=0,payload_json=excluded.payload_json,atualizado_em=excluded.atualizado_em")){
                            p.setString(1,key);p.setString(2,previousPayload);p.setString(3,ZonedDateTime.now(AppConfig.ZONE).toString());p.executeUpdate();
                        }
                    }else{
                        try(PreparedStatement p=c.prepareStatement("DELETE FROM erp_lancamento_overrides WHERE erp_chave=?")){p.setString(1,key);p.executeUpdate();}
                    }
                }else if("MANUAL".equalsIgnoreCase(type)){
                    ensureUserCanActOnMachine(user,record.getMachine());
                    manualDate=record.getDate();manualMachine=record.getMachine();
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO historico_oee(data,data_br,maquina,produto,numero_op,op_producao_detalhe,horas_programadas,capacidade_24h,turno_a_pcs,turno_b_pcs,turno_c_pcs,total_produzido_pcs,peso_unitario_g,refugo_a_kg,refugo_b_kg,refugo_c_kg,refugo_total_kg,refugo_total_pcs,refugo_pct,qtd_trocas,tempo_setup_hrs,horas_paradas_quebra,tempo_produzindo_hrs,disponibilidade_pct,desempenho_pct,qualidade_pct,oee_pct,problema,acao_tomada,hora_lancamento,movimentado_em) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){
                        bindManual(p,record);p.setString(31,record.getMovementAt());p.executeUpdate();
                    }
                }else throw new IllegalArgumentException("Tipo de lançamento inválido na lixeira.");
                try(PreparedStatement p=c.prepareStatement("DELETE FROM lancamentos_lixeira WHERE id=?")){p.setLong(1,trashId);p.executeUpdate();}
                c.commit();
            }catch(Exception e){
                try{c.rollback();}catch(SQLException ignored){}
                if(e instanceof IllegalArgumentException iae)throw iae;
                if(e instanceof SQLException se)throw se;
                throw new IllegalStateException(e);
            }
        }catch(IllegalArgumentException e){throw e;}catch(SQLException e){throw new IllegalStateException(e);}
        if(manualDate!=null)recalculateManualDay(manualDate,manualMachine);
    }

    @Scheduled(fixedDelay=3600000,initialDelay=60000)
    public void cleanupExpiredTrash(){
        long now=System.currentTimeMillis();
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement("DELETE FROM lancamentos_lixeira WHERE expira_epoch<=?")){
            p.setLong(1,now);p.executeUpdate();
        }catch(SQLException ignored){}
    }

    private void putInTrash(Connection c,String type,String key,LaunchRecord record,boolean previousExists,String previousPayload,User user)throws Exception{
        ZonedDateTime deleted=ZonedDateTime.now(AppConfig.ZONE);ZonedDateTime expires=deleted.plusDays(30);
        String body=trashJson(record);
        String sql="INSERT INTO lancamentos_lixeira(tipo,chave_origem,payload_json,override_anterior_existia,override_anterior_json,excluido_por,excluido_em,expira_em,expira_epoch) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(tipo,chave_origem) DO UPDATE SET payload_json=excluded.payload_json,override_anterior_existia=excluded.override_anterior_existia,override_anterior_json=excluded.override_anterior_json,excluido_por=excluded.excluido_por,excluido_em=excluded.excluido_em,expira_em=excluded.expira_em,expira_epoch=excluded.expira_epoch";
        try(PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,type);p.setString(2,key);p.setString(3,body);p.setInt(4,previousExists?1:0);p.setString(5,previousPayload);
            p.setString(6,user==null?"":Norm.text(user.username()));p.setString(7,deleted.toString());p.setString(8,expires.toString());p.setLong(9,expires.toInstant().toEpochMilli());p.executeUpdate();
        }
    }

    private boolean canUserActOnTrashRecord(User user,LaunchRecord record){
        if(user==null||!user.canModifyLaunches()||record==null)return false;
        if(user.isAdmin())return true;
        Machine m=resolveMachine(catalog.machineMap(),record.getMachine());
        String target=m==null?Norm.text(record.getSector()):Norm.text(m.sector());
        String allowed=Norm.text(user.sector());
        return !target.isBlank()&&!allowed.isBlank()&&target.equalsIgnoreCase(allowed);
    }

    private void ensureUserCanActOnMachine(User user,String machine){
        if(user==null||!user.canModifyLaunches())throw new IllegalArgumentException("Seu perfil não permite modificar lançamentos.");
        if(user.isAdmin())return;
        Machine m=resolveMachine(catalog.machineMap(),machine);
        String target=m==null?"":Norm.text(m.sector());
        String allowed=Norm.text(user.sector());
        if(target.isBlank()||allowed.isBlank()||!target.equalsIgnoreCase(allowed))
            throw new IllegalArgumentException("Seu perfil não permite modificar lançamentos deste setor.");
    }

    private void ensureUserCanActOnRecord(User user,LaunchRecord record){
        if(user==null||!user.canModifyLaunches())throw new IllegalArgumentException("Seu perfil não permite modificar lançamentos.");
        if(user.isAdmin())return;
        Machine current=resolveMachine(catalog.machineMap(),record==null?null:record.getMachine());
        String target=current!=null?Norm.text(current.sector()):Norm.text(record==null?null:record.getSector());
        String allowed=Norm.text(user.sector());
        if(target.isBlank()||allowed.isBlank()||!target.equalsIgnoreCase(allowed))
            throw new IllegalArgumentException("Seu perfil não permite modificar lançamentos deste setor.");
    }

    public List<LaunchRecord> automatic(LocalDate start,LocalDate end,LocalDate linkStart,LocalDate linkEnd){
        if(end.isBefore(start)){LocalDate t=start;start=end;end=t;}LocalDate rawEnd=end.plusDays(1);
        Map<String,Machine>mm=catalog.machineMap();Map<String,Machine>knownMm=knownMachineMetadataMap(mm);List<A>a=new ArrayList<>();List<R>rr=new ArrayList<>();
        try(Connection c=db.open()){
            try(PreparedStatement p=c.prepareStatement("SELECT erp_id,ordem,data_apon,produto,descricao,cliente,maquina,turno,qtd_apon,operador,sincronizado_em FROM erp_apontamento_raw WHERE data_apon BETWEEN ? AND ? ORDER BY erp_id")){p.setString(1,start.toString());p.setString(2,rawEnd.toString());ResultSet r=p.executeQuery();while(r.next())a.add(new A(r.getLong(1),r.getString(2),Norm.isoDate(r.getString(3)),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getDouble(9),r.getString(10),r.getString(11)));}
            try(PreparedStatement p=c.prepareStatement("SELECT erp_id,data_apon,ordem,maquina,produto,turno,qtd_refugo,peso_br,qtd_itens,COALESCE(primeiro_sincronizado_em,sincronizado_em) FROM erp_refugo_raw WHERE data_apon BETWEEN ? AND ? ORDER BY erp_id")){p.setString(1,start.toString());p.setString(2,rawEnd.toString());ResultSet r=p.executeQuery();while(r.next()){Object oi=r.getObject(9);Integer items=oi instanceof Number n?n.intValue():null;rr.add(new R(r.getLong(1),Norm.isoDate(r.getString(2)),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getDouble(7),r.getDouble(8),items,r.getString(10)));};}
        }catch(SQLException e){throw new IllegalStateException(e);}
        Map<String,LaunchRecord> groups=new LinkedHashMap<>();
        for(A x:a){LocalDate d=Norm.productiveDate(x.date,x.shift);if(d==null||d.isBefore(start)||d.isAfter(end))continue;String machineNormalized=Norm.machine(x.machine);Machine matchedMachine=resolveMachine(knownMm,machineNormalized);String machine=matchedMachine!=null?matchedMachine.name():machineNormalized,op=Norm.order(x.order),prod=Norm.text(x.product),key=erpKey(d,op,machine,prod);LaunchRecord item=groups.computeIfAbsent(key,k->{LaunchRecord z=new LaunchRecord();z.setErp(true);z.setErpKey(k);z.setId(erpId(k));z.setDate(d);z.setMachine(machine);z.setProduct(prod);z.setOrderNumber(op);Machine cm=matchedMachine!=null?matchedMachine:resolveMachine(knownMm,machine);z.setSector(cm!=null&&!Norm.text(cm.sector()).isBlank()?cm.sector():Norm.sectorFromMachineRaw(x.machine));z.setCapacity24h(cm!=null?cm.capacity():0);z.setOperatorErp(Norm.text(x.operator));z.setDescriptionErp(Norm.text(x.description));z.setClientErp(Norm.text(x.client));return z;});item.getErpIds().add(x.id);int pcs=(int)Math.round(x.qty*1000.0);switch(Norm.token(x.shift)){case"A"->item.setShiftA(item.getShiftA()+pcs);case"B"->item.setShiftB(item.getShiftB()+pcs);case"C"->item.setShiftC(item.getShiftC()+pcs);}item.setTotalProduced(item.getTotalProduced()+pcs);String description=Norm.text(x.description);if(!description.isBlank())item.setDescriptionErp(description);String client=Norm.text(x.client);if(!client.isBlank())item.setClientErp(client);applyMovementIfLater(item,x.sync,true);}
        List<LaunchRecord> items=new ArrayList<>(groups.values());if(items.isEmpty())return items;
        Map<String,List<Integer>> period=new HashMap<>(),day=new HashMap<>();for(int i=0;i<items.size();i++){LaunchRecord x=items.get(i);if(x.getDate().isBefore(linkStart)||x.getDate().isAfter(linkEnd))continue;String base=Norm.order(x.getOrderNumber())+"|"+Norm.product(x.getProduct());period.computeIfAbsent(base,k->new ArrayList<>()).add(i);day.computeIfAbsent(x.getDate()+"|"+base,k->new ArrayList<>()).add(i);}
        Map<Integer,Double> noShiftKg=new HashMap<>();
        Map<Integer,Double> weightAccum=new HashMap<>();
        Map<Integer,Double> weightBase=new HashMap<>();
        for(R r:rr){
            LocalDate d=Norm.productiveScrapDate(r.date,r.shift,r.sync);
            if(d==null||d.isBefore(linkStart)||d.isAfter(linkEnd))continue;
            String op=Norm.order(r.order),prod=Norm.product(r.product);
            if(op.isBlank()||prod.isBlank())continue;
            List<Integer> candidates=new ArrayList<>(period.getOrDefault(op+"|"+prod,List.of()));
            if(candidates.isEmpty())continue;
            String sector=Norm.canonicalSector(Norm.scrapSector(prod));
            List<Integer> sectorMatches=candidates.stream().filter(i->Norm.canonicalSector(items.get(i).getSector()).equals(sector)).toList();
            List<Integer> eligible=!sectorMatches.isEmpty()?new ArrayList<>(sectorMatches):(candidates.size()==1?new ArrayList<>(candidates):new ArrayList<>());
            if(eligible.isEmpty())continue;
            Set<Integer> sameDay=new HashSet<>(day.getOrDefault(d+"|"+op+"|"+prod,List.of()));
            List<Integer> ed=eligible.stream().filter(sameDay::contains).toList();
            if(!ed.isEmpty())eligible=new ArrayList<>(ed);
            String rm=Norm.token(Norm.machine(r.machine));
            List<Integer> sameMachine=eligible.stream().filter(i->Norm.token(items.get(i).getMachine()).equals(rm)).toList();
            if(!sameMachine.isEmpty())eligible=new ArrayList<>(sameMachine);
            final String shift=Norm.token(r.shift);
            int target=eligible.stream().max(Comparator
                    .comparingInt((Integer i)->switch(shift){case"A"->items.get(i).getShiftA();case"B"->items.get(i).getShiftB();case"C"->items.get(i).getShiftC();default->0;})
                    .thenComparingInt(i->items.get(i).getTotalProduced())
                    .thenComparing(i->items.get(i).getDate())
                    .thenComparingInt(i->-i)).orElse(eligible.get(0));
            LaunchRecord x=items.get(target);
            double kg=Math.max(0,r.kg);
            if("A".equals(shift))x.setScrapAKg(x.getScrapAKg()+kg);
            else if("B".equals(shift))x.setScrapBKg(x.getScrapBKg()+kg);
            else if("C".equals(shift))x.setScrapCKg(x.getScrapCKg()+kg);
            else noShiftKg.merge(target,kg,Double::sum);

            int pcs;
            if(r.items!=null) pcs=Math.max(0,r.items);
            else if(r.weight>0) pcs=(int)Math.round(kg*1000.0/r.weight);
            else pcs=0;
            x.setScrapTotalPcs(x.getScrapTotalPcs()+pcs);
            if(r.weight>0&&pcs>0){
                weightAccum.merge(target,r.weight*pcs,Double::sum);
                weightBase.merge(target,(double)pcs,Double::sum);
            }
        }
        for(int i=0;i<items.size();i++){
            LaunchRecord x=items.get(i);
            x.setScrapAKg(Norm.round(x.getScrapAKg(),3));
            x.setScrapBKg(Norm.round(x.getScrapBKg(),3));
            x.setScrapCKg(Norm.round(x.getScrapCKg(),3));
            x.setScrapTotalKg(Norm.round(x.getScrapAKg()+x.getScrapBKg()+x.getScrapCKg()+noShiftKg.getOrDefault(i,0.0),3));
            double base=weightBase.getOrDefault(i,0.0);
            if(base>0)x.setUnitWeightG(Norm.round(weightAccum.getOrDefault(i,0.0)/base,4));
        }
        Map<String,List<LaunchRecord>> machineDay=items.stream().collect(Collectors.groupingBy(x->x.getDate()+"|"+x.getMachine(),LinkedHashMap::new,Collectors.toList()));for(List<LaunchRecord> g:machineDay.values()){double total=g.stream().mapToDouble(LaunchRecord::processedPcs).sum();double used=0;for(int i=0;i<g.size();i++){double h=total<=0?24.0/g.size():24.0*g.get(i).processedPcs()/total;if(i==g.size()-1)h=24.0-used;g.get(i).setScheduledHours(Norm.round(Math.max(0,h),4));used+=h;}}
        applyOverrides(items);
        // Overrides antigos da v095 não persistiam Cliente/Descrição. Se o Código
        // foi alterado, applyPayload limpa os metadados antigos e este lookup
        // recompõe ambos pelo novo código, evitando Cliente/Descrição do item anterior.
        fillMissingProductMetadata(items);
        ensureCatalogMetadata(items,mm,knownMm);
        oee.recalculate(items);
        items.sort(recentFirst());
        return items;
    }


    private static String machineLookupKey(Object value){
        return Norm.machineKey(value);
    }

    private static Machine resolveMachine(Map<String,Machine> machines,String name){
        if(machines==null||machines.isEmpty())return null;
        Machine direct=machines.get(name);
        if(direct!=null)return direct;
        String wanted=machineLookupKey(name);
        if(wanted.isBlank())return null;
        for(Machine m:machines.values()){
            if(machineLookupKey(m.name()).equals(wanted))return m;
        }
        return null;
    }

    private void ensureCatalogMetadata(List<LaunchRecord> rows,Map<String,Machine> machines){
        ensureCatalogMetadata(rows,machines,knownMachineMetadataMap(machines));
    }

    private void ensureCatalogMetadata(List<LaunchRecord> rows,Map<String,Machine> machines,Map<String,Machine> known){
        if(rows==null||rows.isEmpty())return;
        LinkedHashMap<String,LaunchRecord> snapshots=new LinkedHashMap<>();
        for(LaunchRecord r:rows){
            Machine current=resolveMachine(machines,r.getMachine());
            Machine fallback=current!=null?current:resolveMachine(known,r.getMachine());
            if(fallback==null)continue;
            // Cadastro atual tem prioridade. Sem cadastro atual, nunca apagamos
            // os metadados que já vieram do item/override; apenas completamos
            // o que estiver ausente com último valor conhecido confiável.
            if(current!=null){
                r.setSector(current.sector());
                if(current.capacity()>0)r.setCapacity24h(current.capacity());
            }else{
                if((Norm.text(r.getSector()).isBlank()||"Sem Setor".equalsIgnoreCase(Norm.text(r.getSector())))&&!Norm.text(fallback.sector()).isBlank())r.setSector(fallback.sector());
                if(r.getCapacity24h()<=0&&fallback.capacity()>0)r.setCapacity24h(fallback.capacity());
            }
            if(!Norm.text(r.getMachine()).isBlank()&&r.getCapacity24h()>0)
                snapshots.put(machineLookupKey(r.getMachine()),r);
        }
        rememberMachineMetadata(snapshots.values());
    }

    private Map<String,Machine> knownMachineMetadataMap(Map<String,Machine> current){
        LinkedHashMap<String,Machine> out=new LinkedHashMap<>();
        if(current!=null)for(Machine m:current.values())addKnownMachine(out,m,true);
        try(Connection c=db.open();Statement st=c.createStatement();ResultSet rs=st.executeQuery("SELECT maquina,capacidade,setor FROM maquinas_snapshot WHERE capacidade>0 ORDER BY atualizado_em DESC")){
            while(rs.next())addKnownMachine(out,new Machine(0,rs.getString(1),rs.getInt(2),rs.getString(3)),false);
        }catch(SQLException ignored){}
        try(Connection c=db.open();Statement st=c.createStatement();ResultSet rs=st.executeQuery(
                "SELECT h.maquina,h.capacidade_24h FROM historico_oee h JOIN ("+
                "SELECT maquina,MAX(id) id FROM historico_oee WHERE capacidade_24h>0 GROUP BY maquina"+
                ") latest ON latest.id=h.id ORDER BY h.id DESC")){
            while(rs.next())addKnownMachine(out,new Machine(0,rs.getString(1),rs.getInt(2),"Sem Setor"),false);
        }catch(SQLException ignored){}
        return out;
    }

    private static void addKnownMachine(Map<String,Machine> out,Machine candidate,boolean replace){
        if(candidate==null||Norm.text(candidate.name()).isBlank()||candidate.capacity()<=0)return;
        String wanted=machineLookupKey(candidate.name());
        String equivalent=null;
        for(Map.Entry<String,Machine> e:out.entrySet())if(machineLookupKey(e.getValue().name()).equals(wanted)){equivalent=e.getKey();break;}
        if(equivalent==null)out.put(candidate.name(),candidate);
        else if(replace){out.remove(equivalent);out.put(candidate.name(),candidate);}
    }

    private void rememberMachineMetadata(Collection<LaunchRecord> rows){
        if(rows==null||rows.isEmpty())return;
        String now=ZonedDateTime.now(AppConfig.ZONE).toString();
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement(
                "INSERT INTO maquinas_snapshot(maquina,capacidade,setor,atualizado_em) VALUES(?,?,?,?) "+
                "ON CONFLICT(maquina) DO UPDATE SET capacidade=excluded.capacidade,"+
                "setor=CASE WHEN excluded.setor IS NULL OR TRIM(excluded.setor)='' OR excluded.setor='Sem Setor' "+
                "THEN maquinas_snapshot.setor ELSE excluded.setor END,atualizado_em=excluded.atualizado_em")){
            c.setAutoCommit(false);
            for(LaunchRecord r:rows){
                if(r==null||Norm.text(r.getMachine()).isBlank()||r.getCapacity24h()<=0)continue;
                p.setString(1,r.getMachine().trim().toUpperCase(Locale.ROOT));
                p.setInt(2,r.getCapacity24h());
                p.setString(3,r.getSector());
                p.setString(4,now);
                p.addBatch();
            }
            p.executeBatch();
            c.commit();
        }catch(SQLException ignored){}
    }

    private void allocateUnifiedScheduledHours(List<LaunchRecord> rows){
        Map<String,List<LaunchRecord>> groups=rows.stream()
                .filter(r->r.getDate()!=null&&r.getMachine()!=null&&!r.getMachine().isBlank())
                .collect(Collectors.groupingBy(r->r.getDate()+"|"+r.getMachine(),LinkedHashMap::new,Collectors.toList()));
        for(List<LaunchRecord> g:groups.values()){
            List<LaunchRecord> flexible=g.stream().filter(r->r.isErp()&&!r.isManualOverride()).toList();
            if(flexible.isEmpty()) continue;
            double fixed=g.stream().filter(r->!r.isErp()||r.isManualOverride())
                    .mapToDouble(r->Math.max(0,r.getScheduledHours())).sum();
            double remaining=Math.max(0,24.0-fixed);
            double weight=flexible.stream().mapToDouble(LaunchRecord::processedPcs).sum();
            double used=0;
            for(int i=0;i<flexible.size();i++){
                LaunchRecord r=flexible.get(i);
                double h=(weight<=0?remaining/flexible.size():remaining*r.processedPcs()/weight);
                if(i==flexible.size()-1) h=Math.max(0,remaining-used);
                h=Norm.round(Math.max(0,h),4);
                r.setScheduledHours(h);
                used+=h;
            }
        }
    }

    /**
     * Quando existe produção somente manual para uma OP e o ERP entrega
     * Refugo para essa mesma produção, cruza o Refugo automaticamente. Se já
     * existe apontamento ERP candidato, o Refugo já foi vinculado por
     * automatic() e não é somado novamente. Refugo digitado manualmente em um
     * turno também tem precedência, evitando duplicidade.
     */
    private void attachErpScrapToManualOnlyWhenNeeded(List<LaunchRecord> manual,List<LaunchRecord> auto,LocalDate start,LocalDate end){
        if(manual.isEmpty()) return;
        List<R> raw=new ArrayList<>();
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement(
                "SELECT erp_id,data_apon,ordem,maquina,produto,turno,qtd_refugo,peso_br,qtd_itens,COALESCE(primeiro_sincronizado_em,sincronizado_em) FROM erp_refugo_raw WHERE data_apon BETWEEN ? AND ? ORDER BY erp_id")){
            p.setString(1,start.toString());
            p.setString(2,end.plusDays(1).toString());
            ResultSet r=p.executeQuery();
            while(r.next()){Object oi=r.getObject(9);Integer items=oi instanceof Number n?n.intValue():null;raw.add(new R(r.getLong(1),Norm.isoDate(r.getString(2)),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getDouble(7),r.getDouble(8),items,r.getString(10)));};
        }catch(SQLException e){throw new IllegalStateException(e);}

        Map<String,Boolean> allowShift=new HashMap<>();
        for(LaunchRecord m:manual){
            allowShift.put(m.getId()+"|A",m.getScrapAKg()<=0);
            allowShift.put(m.getId()+"|B",m.getScrapBKg()<=0);
            allowShift.put(m.getId()+"|C",m.getScrapCKg()<=0);
        }

        for(R r:raw){
            LocalDate d=Norm.productiveScrapDate(r.date,r.shift,r.sync);
            if(d==null||d.isBefore(start)||d.isAfter(end)) continue;
            String op=Norm.order(r.order),prod=Norm.product(r.product),shift=Norm.token(r.shift);
            if(op.isBlank()||prod.isBlank()||!(shift.equals("A")||shift.equals("B")||shift.equals("C"))) continue;

            boolean hasAuto=auto.stream().anyMatch(x->Objects.equals(x.getDate(),d)
                    &&Norm.order(x.getOrderNumber()).equals(op)
                    &&Norm.product(x.getProduct()).equals(prod));
            if(hasAuto) continue;

            List<LaunchRecord> candidates=manual.stream().filter(x->Objects.equals(x.getDate(),d)
                    &&Norm.order(x.getOrderNumber()).equals(op)
                    &&Norm.product(x.getProduct()).equals(prod)).toList();
            if(candidates.isEmpty()) continue;

            String rawMachine=Norm.token(Norm.machine(r.machine));
            List<LaunchRecord> sameMachine=candidates.stream().filter(x->Norm.token(x.getMachine()).equals(rawMachine)).toList();
            if(!sameMachine.isEmpty()) candidates=sameMachine;
            final String targetShift=shift;
            LaunchRecord target=candidates.stream().max(Comparator.comparingInt((LaunchRecord x)->switch(targetShift){
                case "A"->x.getShiftA();case "B"->x.getShiftB();case "C"->x.getShiftC();default->0;
            }).thenComparingInt(LaunchRecord::getTotalProduced)).orElse(candidates.get(0));

            if(!allowShift.getOrDefault(target.getId()+"|"+shift,false)) continue;
            double kg=Math.max(0,r.kg);
            if("A".equals(shift)) target.setScrapAKg(target.getScrapAKg()+kg);
            else if("B".equals(shift)) target.setScrapBKg(target.getScrapBKg()+kg);
            else target.setScrapCKg(target.getScrapCKg()+kg);
            int pcs=r.items!=null?Math.max(0,r.items):(r.weight>0?(int)Math.round(kg*1000.0/r.weight):0);
            target.setScrapTotalPcs(target.getScrapTotalPcs()+Math.max(0,pcs));
            if(r.weight>0) target.setUnitWeightG(r.weight);
        }
        for(LaunchRecord m:manual){
            m.setScrapTotalKg(Norm.round(Math.max(0,m.getScrapAKg())+Math.max(0,m.getScrapBKg())+Math.max(0,m.getScrapCKg()),3));
        }
    }

    private LocalDate[] sourceBoundsForOverrides(LocalDate requestedStart,LocalDate requestedEnd){
        LocalDate sourceStart=requestedStart,sourceEnd=requestedEnd;
        try(Connection c=db.open();Statement st=c.createStatement();ResultSet rs=st.executeQuery(
                "SELECT erp_chave,payload_json FROM erp_lancamento_overrides WHERE oculto=0 AND payload_json IS NOT NULL")){
            while(rs.next()){
                String key=rs.getString(1),body=rs.getString(2);
                if(key==null||key.length()<10||body==null||body.isBlank())continue;
                LocalDate sourceDate=Norm.isoDate(key.substring(0,10));
                Map<String,Object> payload;
                try{payload=json.readValue(body,new TypeReference<>(){});}catch(Exception ignored){continue;}
                LocalDate effectiveDate=Norm.isoDate(payload.get("date"));
                if(sourceDate==null||effectiveDate==null)continue;
                if(!effectiveDate.isBefore(requestedStart)&&!effectiveDate.isAfter(requestedEnd)){
                    if(sourceDate.isBefore(sourceStart))sourceStart=sourceDate;
                    if(sourceDate.isAfter(sourceEnd))sourceEnd=sourceDate;
                }
            }
        }catch(SQLException ignored){}
        return new LocalDate[]{sourceStart,sourceEnd};
    }

    public Map<String,String> syncStatus(){Map<String,String>out=new LinkedHashMap<>();try(Connection c=db.open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT fonte,ultimo_recebimento FROM erp_sync_estado")){while(r.next())out.put(r.getString(1),r.getString(2));}catch(SQLException ignored){}return out;}

    private void applyOverrides(List<LaunchRecord> items){Map<String,Override> ovs=new HashMap<>();try(Connection c=db.open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT erp_chave,oculto,payload_json,atualizado_em FROM erp_lancamento_overrides")){while(r.next()){Map<String,Object>m=null;try{if(r.getString(3)!=null)m=json.readValue(r.getString(3),new TypeReference<>(){});}catch(Exception ignored){}ovs.put(r.getString(1),new Override(r.getInt(2)==1,m,r.getString(4)));}}catch(SQLException ignored){}Iterator<LaunchRecord>it=items.iterator();while(it.hasNext()){LaunchRecord x=it.next();Override ov=ovs.get(x.getErpKey());if(ov==null)continue;if(ov.hidden){it.remove();continue;}if(ov.payload!=null){applyPayload(x,ov.payload);applyMovementIfLater(x,ov.updatedAt,true);x.setManualOverride(true);}}}
    private void applyPayload(LaunchRecord x,Map<String,Object>m){if(m.containsKey("date")){LocalDate d=Norm.isoDate(m.get("date"));if(d!=null)x.setDate(d);}if(m.containsKey("machine"))x.setMachine(Norm.text(m.get("machine")));if(m.containsKey("product")){String oldProduct=Norm.product(x.getProduct());String newProduct=Norm.text(m.get("product"));x.setProduct(newProduct);if(!Objects.equals(oldProduct,Norm.product(newProduct))){x.setDescriptionErp("");x.setClientErp("");}}if(m.containsKey("descriptionErp"))x.setDescriptionErp(Norm.text(m.get("descriptionErp")));if(m.containsKey("clientErp"))x.setClientErp(Norm.text(m.get("clientErp")));if(m.containsKey("orderNumber"))x.setOrderNumber(Norm.text(m.get("orderNumber")));if(m.containsKey("productionDetail"))x.setProductionDetail(Norm.text(m.get("productionDetail")));if(m.containsKey("scheduledHours"))x.setScheduledHours(Norm.dbl(m.get("scheduledHours"),x.getScheduledHours()));if(m.containsKey("capacity24h"))x.setCapacity24h(Norm.integer(m.get("capacity24h"),x.getCapacity24h()));if(m.containsKey("shiftA"))x.setShiftA(Norm.integer(m.get("shiftA"),x.getShiftA()));if(m.containsKey("shiftB"))x.setShiftB(Norm.integer(m.get("shiftB"),x.getShiftB()));if(m.containsKey("shiftC"))x.setShiftC(Norm.integer(m.get("shiftC"),x.getShiftC()));x.setTotalProduced(x.getShiftA()+x.getShiftB()+x.getShiftC());if(m.containsKey("unitWeightG"))x.setUnitWeightG(Norm.dbl(m.get("unitWeightG"),x.getUnitWeightG()));if(m.containsKey("scrapAKg"))x.setScrapAKg(Norm.dbl(m.get("scrapAKg"),x.getScrapAKg()));if(m.containsKey("scrapBKg"))x.setScrapBKg(Norm.dbl(m.get("scrapBKg"),x.getScrapBKg()));if(m.containsKey("scrapCKg"))x.setScrapCKg(Norm.dbl(m.get("scrapCKg"),x.getScrapCKg()));x.setScrapTotalKg(Norm.round(x.getScrapAKg()+x.getScrapBKg()+x.getScrapCKg(),3));if(m.containsKey("scrapTotalPcs"))x.setScrapTotalPcs(Norm.integer(m.get("scrapTotalPcs"),x.getScrapTotalPcs()));if(m.containsKey("changeovers"))x.setChangeovers(Norm.integer(m.get("changeovers"),x.getChangeovers()));if(m.containsKey("setupHours"))x.setSetupHours(Norm.dbl(m.get("setupHours"),x.getSetupHours()));if(m.containsKey("breakdownHours"))x.setBreakdownHours(Norm.dbl(m.get("breakdownHours"),x.getBreakdownHours()));if(m.containsKey("problem"))x.setProblem(Norm.text(m.get("problem")));}
    private Map<String,Object>payload(LaunchRecord x){Map<String,Object>m=new LinkedHashMap<>();m.put("date",String.valueOf(x.getDate()));m.put("machine",x.getMachine());m.put("product",x.getProduct());m.put("descriptionErp",x.getDescriptionErp());m.put("clientErp",x.getClientErp());m.put("orderNumber",x.getOrderNumber());m.put("productionDetail",x.getProductionDetail());m.put("scheduledHours",x.getScheduledHours());m.put("capacity24h",x.getCapacity24h());m.put("shiftA",x.getShiftA());m.put("shiftB",x.getShiftB());m.put("shiftC",x.getShiftC());m.put("unitWeightG",x.getUnitWeightG());m.put("scrapAKg",x.getScrapAKg());m.put("scrapBKg",x.getScrapBKg());m.put("scrapCKg",x.getScrapCKg());m.put("scrapTotalPcs",x.getScrapTotalPcs());m.put("changeovers",x.getChangeovers());m.put("setupHours",x.getSetupHours());m.put("breakdownHours",x.getBreakdownHours());m.put("problem",x.getProblem());return m;}
    private String trashJson(LaunchRecord x)throws Exception{
        Map<String,Object>m=new LinkedHashMap<>();
        m.put("id",x.getId());m.put("erp",x.isErp());m.put("manualOverride",x.isManualOverride());m.put("erpKey",x.getErpKey());m.put("erpIds",x.getErpIds());
        m.put("date",String.valueOf(x.getDate()));m.put("machine",x.getMachine());m.put("product",x.getProduct());m.put("orderNumber",x.getOrderNumber());m.put("productionDetail",x.getProductionDetail());m.put("sector",x.getSector());
        m.put("scheduledHours",x.getScheduledHours());m.put("capacity24h",x.getCapacity24h());m.put("shiftA",x.getShiftA());m.put("shiftB",x.getShiftB());m.put("shiftC",x.getShiftC());m.put("totalProduced",x.getTotalProduced());
        m.put("unitWeightG",x.getUnitWeightG());m.put("scrapAKg",x.getScrapAKg());m.put("scrapBKg",x.getScrapBKg());m.put("scrapCKg",x.getScrapCKg());m.put("scrapTotalKg",x.getScrapTotalKg());m.put("scrapTotalPcs",x.getScrapTotalPcs());m.put("scrapPct",x.getScrapPct());
        m.put("changeovers",x.getChangeovers());m.put("setupHours",x.getSetupHours());m.put("breakdownHours",x.getBreakdownHours());m.put("producingHours",x.getProducingHours());m.put("availabilityPct",x.getAvailabilityPct());m.put("performancePct",x.getPerformancePct());m.put("qualityPct",x.getQualityPct());m.put("oeePct",x.getOeePct());
        m.put("problem",x.getProblem());m.put("actionTaken",x.getActionTaken());m.put("launchTime",x.getLaunchTime());m.put("movementAt",x.getMovementAt());m.put("operatorErp",x.getOperatorErp());m.put("descriptionErp",x.getDescriptionErp());m.put("clientErp",x.getClientErp());m.put("launchCount",x.getLaunchCount());
        return json.writeValueAsString(m);
    }

    private LaunchRecord recordFromTrashJson(String body){
        try{
            Map<String,Object>m=json.readValue(body,new TypeReference<>(){});LaunchRecord x=new LaunchRecord();
            x.setId(longValue(m.get("id")));x.setErp(boolValue(m.get("erp")));x.setManualOverride(boolValue(m.get("manualOverride")));x.setErpKey(Norm.text(m.get("erpKey")));
            Object ids=m.get("erpIds");if(ids instanceof Collection<?> c){List<Long>list=new ArrayList<>();for(Object v:c)list.add(longValue(v));x.setErpIds(list);}
            x.setDate(Norm.isoDate(m.get("date")));x.setMachine(Norm.text(m.get("machine")));x.setProduct(Norm.text(m.get("product")));x.setOrderNumber(Norm.text(m.get("orderNumber")));x.setProductionDetail(Norm.text(m.get("productionDetail")));x.setSector(Norm.text(m.get("sector")));
            x.setScheduledHours(Norm.dbl(m.get("scheduledHours"),0));x.setCapacity24h(Norm.integer(m.get("capacity24h"),0));x.setShiftA(Norm.integer(m.get("shiftA"),0));x.setShiftB(Norm.integer(m.get("shiftB"),0));x.setShiftC(Norm.integer(m.get("shiftC"),0));x.setTotalProduced(Norm.integer(m.get("totalProduced"),0));
            x.setUnitWeightG(Norm.dbl(m.get("unitWeightG"),0));x.setScrapAKg(Norm.dbl(m.get("scrapAKg"),0));x.setScrapBKg(Norm.dbl(m.get("scrapBKg"),0));x.setScrapCKg(Norm.dbl(m.get("scrapCKg"),0));x.setScrapTotalKg(Norm.dbl(m.get("scrapTotalKg"),0));x.setScrapTotalPcs(Norm.integer(m.get("scrapTotalPcs"),0));x.setScrapPct(Norm.dbl(m.get("scrapPct"),0));
            x.setChangeovers(Norm.integer(m.get("changeovers"),0));x.setSetupHours(Norm.dbl(m.get("setupHours"),0));x.setBreakdownHours(Norm.dbl(m.get("breakdownHours"),0));x.setProducingHours(Norm.dbl(m.get("producingHours"),0));x.setAvailabilityPct(Norm.dbl(m.get("availabilityPct"),0));x.setPerformancePct(Norm.dbl(m.get("performancePct"),0));x.setQualityPct(Norm.dbl(m.get("qualityPct"),0));x.setOeePct(Norm.dbl(m.get("oeePct"),0));
            x.setProblem(Norm.text(m.get("problem")));x.setActionTaken(Norm.text(m.get("actionTaken")));x.setLaunchTime(Norm.text(m.get("launchTime")));x.setMovementAt(Norm.text(m.get("movementAt")));x.setOperatorErp(Norm.text(m.get("operatorErp")));x.setDescriptionErp(Norm.text(m.get("descriptionErp")));x.setClientErp(Norm.text(m.get("clientErp")));x.setLaunchCount(Norm.integer(m.get("launchCount"),1));
            return x;
        }catch(Exception e){return null;}
    }

    private static long longValue(Object value){if(value instanceof Number n)return n.longValue();try{return Long.parseLong(String.valueOf(value));}catch(Exception e){return 0L;}}
    private static boolean boolValue(Object value){if(value instanceof Boolean b)return b;if(value instanceof Number n)return n.intValue()!=0;return Boolean.parseBoolean(String.valueOf(value));}

    private void finalizeManual(LaunchRecord r){r.setTotalProduced(Math.max(0,r.getShiftA())+Math.max(0,r.getShiftB())+Math.max(0,r.getShiftC()));r.setScrapTotalKg(Norm.round(Math.max(0,r.getScrapAKg())+Math.max(0,r.getScrapBKg())+Math.max(0,r.getScrapCKg()),2));r.setScrapTotalPcs(r.getUnitWeightG()>0?(int)(r.getScrapTotalKg()*1000.0/r.getUnitWeightG()):0);if(r.getScheduledHours()<=0)r.setScheduledHours(24.0);if(r.getLaunchTime()==null||r.getLaunchTime().isBlank())r.setLaunchTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));oee.recalculate(List.of(r));}
    private void recalculateManualDay(LocalDate d,String machine){if(d==null||machine==null)return;List<LaunchRecord>g=new ArrayList<>();try(Connection c=db.open();PreparedStatement p=c.prepareStatement("SELECT * FROM historico_oee WHERE data=? AND maquina=?")){p.setString(1,d.toString());p.setString(2,machine);ResultSet r=p.executeQuery();while(r.next())g.add(mapManual(r));oee.recalculate(g);try(PreparedStatement u=c.prepareStatement("UPDATE historico_oee SET refugo_pct=?,tempo_produzindo_hrs=?,disponibilidade_pct=?,desempenho_pct=?,qualidade_pct=?,oee_pct=? WHERE id=?")){for(LaunchRecord x:g){u.setDouble(1,x.getScrapPct());u.setDouble(2,x.getProducingHours());u.setDouble(3,x.getAvailabilityPct());u.setDouble(4,x.getPerformancePct());u.setDouble(5,x.getQualityPct());u.setDouble(6,x.getOeePct());u.setLong(7,x.getId());u.addBatch();}u.executeBatch();}}catch(SQLException e){throw new IllegalStateException(e);}}
    private static LaunchRecord mapManual(ResultSet r)throws SQLException{LaunchRecord x=new LaunchRecord();x.setId(r.getLong("id"));x.setErp(false);x.setDate(LocalDate.parse(r.getString("data")));x.setMachine(r.getString("maquina"));x.setProduct(r.getString("produto"));x.setOrderNumber(r.getString("numero_op"));x.setProductionDetail(r.getString("op_producao_detalhe"));x.setScheduledHours(r.getDouble("horas_programadas"));x.setCapacity24h(r.getInt("capacidade_24h"));x.setShiftA(r.getInt("turno_a_pcs"));x.setShiftB(r.getInt("turno_b_pcs"));x.setShiftC(r.getInt("turno_c_pcs"));x.setTotalProduced(r.getInt("total_produzido_pcs"));x.setUnitWeightG(r.getDouble("peso_unitario_g"));x.setScrapAKg(r.getDouble("refugo_a_kg"));x.setScrapBKg(r.getDouble("refugo_b_kg"));x.setScrapCKg(r.getDouble("refugo_c_kg"));x.setScrapTotalKg(r.getDouble("refugo_total_kg"));x.setScrapTotalPcs(r.getInt("refugo_total_pcs"));x.setScrapPct(r.getDouble("refugo_pct"));x.setChangeovers(r.getInt("qtd_trocas"));x.setSetupHours(r.getDouble("tempo_setup_hrs"));x.setBreakdownHours(r.getDouble("horas_paradas_quebra"));x.setProducingHours(r.getDouble("tempo_produzindo_hrs"));x.setAvailabilityPct(r.getDouble("disponibilidade_pct"));x.setPerformancePct(r.getDouble("desempenho_pct"));x.setQualityPct(r.getDouble("qualidade_pct"));x.setOeePct(r.getDouble("oee_pct"));x.setProblem(r.getString("problema"));x.setActionTaken(r.getString("acao_tomada"));x.setLaunchTime(r.getString("hora_lancamento"));x.setMovementAt(r.getString("movimentado_em"));return x;}
    private static void bindManual(PreparedStatement p,LaunchRecord r)throws SQLException{int i=1;p.setString(i++,r.getDate().toString());p.setString(i++,Norm.br(r.getDate()));p.setString(i++,r.getMachine());p.setString(i++,r.getProduct());p.setString(i++,r.getOrderNumber());p.setString(i++,r.getProductionDetail());p.setDouble(i++,r.getScheduledHours());p.setInt(i++,r.getCapacity24h());p.setInt(i++,r.getShiftA());p.setInt(i++,r.getShiftB());p.setInt(i++,r.getShiftC());p.setInt(i++,r.getTotalProduced());p.setDouble(i++,r.getUnitWeightG());p.setDouble(i++,r.getScrapAKg());p.setDouble(i++,r.getScrapBKg());p.setDouble(i++,r.getScrapCKg());p.setDouble(i++,r.getScrapTotalKg());p.setInt(i++,r.getScrapTotalPcs());p.setDouble(i++,r.getScrapPct());p.setInt(i++,r.getChangeovers());p.setDouble(i++,r.getSetupHours());p.setDouble(i++,r.getBreakdownHours());p.setDouble(i++,r.getProducingHours());p.setDouble(i++,r.getAvailabilityPct());p.setDouble(i++,r.getPerformancePct());p.setDouble(i++,r.getQualityPct());p.setDouble(i++,r.getOeePct());p.setString(i++,r.getProblem());p.setString(i++,r.getActionTaken());p.setString(i,r.getLaunchTime());}
    private static String erpKey(LocalDate d,String op,String machine,String product){return d+"|"+Norm.order(op)+"|"+Norm.token(machine)+"|"+Norm.product(product);}
    private static long erpId(String key){try{byte[]b=MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));long v=0;for(int i=0;i<7;i++)v=(v<<8)|(b[i]&255);return 8_000_000_000_000L+(Math.abs(v)%900_000_000_000L);}catch(Exception e){return 8_000_000_000_000L+Math.abs(key.hashCode());}}
    public record ProductMetadata(String description,String client){}
    public record TrashItem(long id,String type,LaunchRecord record,String deletedBy,String deletedAt,String expiresAt){}
    public record OrderProcessProgress(String orderNumber,String process,String processName,String product,
                                       String description,int plannedPcs,
                                       int producedPcs,int remainingPcs,LocalDate firstDate,LocalDate lastDate){}
    private record A(long id,String order,LocalDate date,String product,String description,String client,String machine,String shift,double qty,String operator,String sync){}
    private record R(long id,LocalDate date,String order,String machine,String product,String shift,double kg,double weight,Integer items,String sync){}
    private record Override(boolean hidden,Map<String,Object>payload,String updatedAt){}
}
