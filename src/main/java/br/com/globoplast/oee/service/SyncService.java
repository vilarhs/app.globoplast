package br.com.globoplast.oee.service;

import br.com.globoplast.oee.config.AppConfig;
import br.com.globoplast.oee.db.Database;
import br.com.globoplast.oee.model.SyncState;
import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.util.Norm;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class SyncService {
    private final Database db;private final JsonMapper json;private final CatalogService catalog;
    public SyncService(Database db,JsonMapper json,CatalogService catalog){this.db=db;this.json=json;this.catalog=catalog;}
    public Map<String,Object> importBatch(String source,List<Map<String,Object>>records,String connectorId,String sentAt){String src=source==null?"":source.toLowerCase(Locale.ROOT);if(!src.equals("apontamento")&&!src.equals("refugo"))throw new IllegalArgumentException("Fonte ERP inválida");Map<Long,Map<String,Object>>unique=new LinkedHashMap<>();for(Map<String,Object>r:records){long id=longVal(r.get("erp_id"));if(id==0||(src.equals("refugo")&&id<0))throw new IllegalArgumentException(src.toUpperCase()+" sem erp_id válido");unique.put(id,r);}String now=ZonedDateTime.now(AppConfig.ZONE).toString();int changed=0;try(Connection c=db.open()){c.setAutoCommit(false);for(Map.Entry<Long,Map<String,Object>>e:unique.entrySet()){long id=e.getKey();Map<String,Object>r=e.getValue();String hash=hash(r);String table=src.equals("apontamento")?"erp_apontamento_raw":"erp_refugo_raw";String old=null;try(PreparedStatement q=c.prepareStatement("SELECT payload_hash FROM "+table+" WHERE erp_id=?")){q.setLong(1,id);ResultSet rs=q.executeQuery();if(rs.next())old=rs.getString(1);}if(hash.equals(old))continue;if(src.equals("apontamento"))upsertApontamento(c,id,r,hash,now);else upsertRefugo(c,id,r,hash,now);changed++;}try(PreparedStatement p=c.prepareStatement("INSERT INTO erp_sync_lotes(fonte,connector_id,sent_at,recebidos,alterados,recebido_em) VALUES(?,?,?,?,?,?)")){p.setString(1,src);p.setString(2,Norm.text(connectorId));p.setString(3,Norm.text(sentAt));p.setInt(4,unique.size());p.setInt(5,changed);p.setString(6,now);p.executeUpdate();}String table=src.equals("apontamento")?"erp_apontamento_raw":"erp_refugo_raw";long total;Long max;try(Statement s=c.createStatement();ResultSet rs=s.executeQuery("SELECT COUNT(*),MAX(erp_id) FROM "+table)){rs.next();total=rs.getLong(1);max=rs.getObject(2)==null?null:rs.getLong(2);}try(PreparedStatement p=c.prepareStatement("INSERT INTO erp_sync_estado(fonte,ultimo_recebimento,ultimo_erp_id,total_registros) VALUES(?,?,?,?) ON CONFLICT(fonte) DO UPDATE SET ultimo_recebimento=excluded.ultimo_recebimento,ultimo_erp_id=excluded.ultimo_erp_id,total_registros=excluded.total_registros")){p.setString(1,src);p.setString(2,now);if(max==null)p.setNull(3,Types.BIGINT);else p.setLong(3,max);p.setLong(4,total);p.executeUpdate();}c.commit();}catch(Exception e){throw new IllegalStateException(e);}Map<String,Object>out=new LinkedHashMap<>();out.put("fonte",src);out.put("recebidos",unique.size());out.put("alterados",changed);out.put("min_erp_id",unique.keySet().stream().min(Long::compareTo).orElse(null));out.put("max_erp_id",unique.keySet().stream().max(Long::compareTo).orElse(null));return out;}

    public Map<String,Object> importCatalog(List<String> sectors,List<Map<String,Object>> machines,List<Map<String,Object>> historicalMachines,String connectorId,String sentAt){
        List<String> safeSectors=sectors==null?List.of():sectors;
        List<Map<String,Object>> safeMachines=machines==null?List.of():machines;
        List<Map<String,Object>> safeHistorical=historicalMachines==null?List.of():historicalMachines;
        if(safeSectors.size()>1000||safeMachines.size()>5000||safeHistorical.size()>10000)throw new IllegalArgumentException("Catálogo excede o limite permitido");
        String now=ZonedDateTime.now(AppConfig.ZONE).toString();
        int sectorsChanged=0,machinesChanged=0,historicalChanged=0;
        try(Connection c=db.open()){
            c.setAutoCommit(false);
            LinkedHashSet<String> allSectors=new LinkedHashSet<>();
            for(String raw:safeSectors){String v=Norm.text(raw).toUpperCase(Locale.ROOT);if(!v.isBlank())allSectors.add(v);}
            for(Map<String,Object> m:safeMachines){String v=Norm.text(m.get("setor")).toUpperCase(Locale.ROOT);if(!v.isBlank())allSectors.add(v);}
            for(String sector:allSectors){
                boolean exists=false;
                try(PreparedStatement q=c.prepareStatement("SELECT 1 FROM setores WHERE setor=? COLLATE NOCASE LIMIT 1")){q.setString(1,sector);exists=q.executeQuery().next();}
                if(!exists){try(PreparedStatement p=c.prepareStatement("INSERT INTO setores(setor) VALUES(?)")){p.setString(1,sector);p.executeUpdate();sectorsChanged++;}}
            }
            for(Map<String,Object> m:safeMachines){
                String name=Norm.text(m.get("maquina")).toUpperCase(Locale.ROOT);
                String sector=Norm.text(m.get("setor")).toUpperCase(Locale.ROOT);
                int capacity=Norm.integer(m.get("capacidade"),0);
                if(name.isBlank()||sector.isBlank()||capacity<=0)continue;
                Long id=null;int oldCapacity=0;String oldSector="";
                try(PreparedStatement q=c.prepareStatement("SELECT id,capacidade,setor FROM maquinas WHERE maquina=? COLLATE NOCASE LIMIT 1")){q.setString(1,name);ResultSet rs=q.executeQuery();if(rs.next()){id=rs.getLong(1);oldCapacity=rs.getInt(2);oldSector=Norm.text(rs.getString(3));}}
                if(id==null){
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO maquinas(maquina,capacidade,setor) VALUES(?,?,?)")){p.setString(1,name);p.setInt(2,capacity);p.setString(3,sector);p.executeUpdate();machinesChanged++;}
                }else if(oldCapacity!=capacity||!oldSector.equalsIgnoreCase(sector)){
                    try(PreparedStatement p=c.prepareStatement("UPDATE maquinas SET maquina=?,capacidade=?,setor=? WHERE id=?")){p.setString(1,name);p.setInt(2,capacity);p.setString(3,sector);p.setLong(4,id);p.executeUpdate();machinesChanged++;}
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO maquinas_snapshot(maquina,capacidade,setor,atualizado_em) VALUES(?,?,?,?) ON CONFLICT(maquina) DO UPDATE SET capacidade=excluded.capacidade,setor=excluded.setor,atualizado_em=excluded.atualizado_em")){p.setString(1,name);p.setInt(2,capacity);p.setString(3,sector);p.setString(4,now);p.executeUpdate();}
            }
            // Histórico do appv723: entra apenas no snapshot. Não cria máquinas no
            // cadastro atual, mas preserva capacidades conhecidas de equipamentos
            // antigos/ERP para que a edição e o OEE continuem funcionando.
            for(Map<String,Object> m:safeHistorical){
                String name=Norm.text(m.get("maquina")).toUpperCase(Locale.ROOT);
                String sector=Norm.text(m.get("setor")).toUpperCase(Locale.ROOT);
                int capacity=Norm.integer(m.get("capacidade"),0);
                if(name.isBlank()||capacity<=0)continue;
                Integer oldCapacity=null;String oldSector="";
                try(PreparedStatement q=c.prepareStatement("SELECT capacidade,setor FROM maquinas_snapshot WHERE maquina=? COLLATE NOCASE LIMIT 1")){q.setString(1,name);ResultSet rs=q.executeQuery();if(rs.next()){oldCapacity=rs.getInt(1);oldSector=Norm.text(rs.getString(2));}}
                String safeSector=sector.isBlank()?oldSector:sector;
                if(oldCapacity==null||oldCapacity!=capacity||(!safeSector.isBlank()&&!safeSector.equalsIgnoreCase(oldSector)))historicalChanged++;
                try(PreparedStatement p=c.prepareStatement("INSERT INTO maquinas_snapshot(maquina,capacidade,setor,atualizado_em) VALUES(?,?,?,?) ON CONFLICT(maquina) DO UPDATE SET capacidade=excluded.capacidade,setor=CASE WHEN excluded.setor IS NULL OR TRIM(excluded.setor)='' THEN maquinas_snapshot.setor ELSE excluded.setor END,atualizado_em=excluded.atualizado_em")){p.setString(1,name);p.setInt(2,capacity);p.setString(3,safeSector);p.setString(4,now);p.executeUpdate();}
            }
            long totalMachines,totalSectors;
            try(Statement st=c.createStatement();ResultSet rs=st.executeQuery("SELECT COUNT(*) FROM maquinas")){rs.next();totalMachines=rs.getLong(1);}
            try(Statement st=c.createStatement();ResultSet rs=st.executeQuery("SELECT COUNT(*) FROM setores")){rs.next();totalSectors=rs.getLong(1);}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO erp_sync_estado(fonte,ultimo_recebimento,ultimo_erp_id,total_registros) VALUES('catalogo',?,NULL,?) ON CONFLICT(fonte) DO UPDATE SET ultimo_recebimento=excluded.ultimo_recebimento,ultimo_erp_id=NULL,total_registros=excluded.total_registros")){p.setString(1,now);p.setLong(2,totalMachines);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO erp_sync_lotes(fonte,connector_id,sent_at,recebidos,alterados,recebido_em) VALUES('catalogo',?,?,?,?,?)")){p.setString(1,Norm.text(connectorId));p.setString(2,Norm.text(sentAt));p.setInt(3,safeMachines.size());p.setInt(4,machinesChanged);p.setString(5,now);p.executeUpdate();}
            c.commit();
            Map<String,Object> out=new LinkedHashMap<>();out.put("setores_recebidos",safeSectors.size());out.put("setores_alterados",sectorsChanged);out.put("maquinas_recebidas",safeMachines.size());out.put("maquinas_alteradas",machinesChanged);out.put("maquinas_historicas_recebidas",safeHistorical.size());out.put("snapshots_historicos_alterados",historicalChanged);out.put("total_setores",totalSectors);out.put("total_maquinas",totalMachines);out.put("total_snapshots",scalarLong(c,"SELECT COUNT(*) FROM maquinas_snapshot WHERE capacidade>0"));return out;
        }catch(Exception e){throw new IllegalStateException(e);}
    }

    public Map<String,Object> oeeDiagnostics(){
        Map<String,Object> out=new LinkedHashMap<>();
        try(Connection c=db.open()){
            long catalogCount=scalarLong(c,"SELECT COUNT(*) FROM maquinas WHERE capacidade>0");
            long snapshot=scalarLong(c,"SELECT COUNT(*) FROM maquinas_snapshot WHERE capacidade>0");
            long erpMachines=scalarLong(c,"SELECT COUNT(DISTINCT UPPER(TRIM(maquina))) FROM erp_apontamento_raw WHERE TRIM(COALESCE(maquina,''))<>''");
            long manualCapacity=scalarLong(c,"SELECT COUNT(DISTINCT UPPER(TRIM(maquina))) FROM historico_oee WHERE capacidade_24h>0");
            out.put("maquinas_catalogo_com_capacidade",catalogCount);
            out.put("maquinas_snapshot_com_capacidade",snapshot);
            out.put("maquinas_erp_distintas",erpMachines);
            out.put("maquinas_historico_com_capacidade",manualCapacity);

            Map<String,Machine> byKey=new LinkedHashMap<>();
            for(Machine m:catalog.machines()){
                if(m.capacity()>0&&!Norm.machineKey(m.name()).isBlank())byKey.put(Norm.machineKey(m.name()),m);
            }

            long resolved=0,unresolved=0;
            List<Map<String,Object>> resolvedSample=new ArrayList<>();
            List<Map<String,Object>> unresolvedSample=new ArrayList<>();
            try(Statement st=c.createStatement();ResultSet rs=st.executeQuery(
                    "SELECT maquina,COUNT(*) apontamentos,ROUND(SUM(COALESCE(qtd_apon,0))*1000,0) pecas FROM erp_apontamento_raw WHERE TRIM(COALESCE(maquina,''))<>'' GROUP BY maquina ORDER BY apontamentos DESC")){
                while(rs.next()){
                    String raw=Norm.text(rs.getString(1));
                    Machine match=byKey.get(Norm.machineKey(raw));
                    Map<String,Object>x=new LinkedHashMap<>();
                    x.put("maquina_erp",raw);
                    x.put("maquina_normalizada",Norm.machine(raw));
                    x.put("apontamentos",rs.getLong(2));
                    x.put("pecas",rs.getLong(3));
                    if(match!=null){
                        resolved++;
                        x.put("maquina_catalogo",match.name());
                        x.put("capacidade",match.capacity());
                        x.put("setor",match.sector());
                        if(resolvedSample.size()<30)resolvedSample.add(x);
                    }else{
                        unresolved++;
                        if(unresolvedSample.size()<60)unresolvedSample.add(x);
                    }
                }
            }
            out.put("maquinas_erp_resolvidas_por_alias",resolved);
            out.put("maquinas_erp_sem_capacidade_catalogada",unresolved);
            out.put("amostra_resolvidas",resolvedSample);
            out.put("maquinas_sem_correspondencia",unresolvedSample);
        }catch(SQLException e){throw new IllegalStateException(e);}
        return out;
    }

    private static long scalarLong(Connection c,String sql)throws SQLException{try(Statement st=c.createStatement();ResultSet rs=st.executeQuery(sql)){return rs.next()?rs.getLong(1):0;}}

    public Map<String,SyncState> status(){Map<String,SyncState>m=new LinkedHashMap<>();try(Connection c=db.open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT fonte,ultimo_recebimento,ultimo_erp_id,total_registros FROM erp_sync_estado ORDER BY fonte")){while(r.next())m.put(r.getString(1),new SyncState(r.getString(1),r.getString(2),r.getObject(3)==null?null:r.getLong(3),r.getLong(4)));}catch(SQLException e){throw new IllegalStateException(e);}return m;}

    /**
     * Reconcilia um recorte completo do Refugo. Somente esta chamada marcada
     * como snapshot completo pode excluir dados; lotes incrementais continuam
     * estritamente aditivos. Isso impede que uma falha ou lote parcial apague
     * registros válidos.
     */
    public Map<String,Object> reconcileRefugoSnapshot(LocalDate start,LocalDate end,
                                                       Collection<Long> presentIds,
                                                       String connectorId,String sentAt){
        if(start==null||end==null||end.isBefore(start))
            throw new IllegalArgumentException("Período de reconciliação do Refugo inválido");
        if(ChronoUnit.DAYS.between(start,end)>30)
            throw new IllegalArgumentException("A reconciliação do Refugo aceita no máximo 31 dias por chamada");
        if(presentIds==null)throw new IllegalArgumentException("snapshot_erp_ids é obrigatório");

        LinkedHashSet<Long> present=new LinkedHashSet<>();
        for(Long id:presentIds){
            if(id==null||id<=0)throw new IllegalArgumentException("snapshot_erp_ids contém ERP_ID inválido");
            present.add(id);
            if(present.size()>100_000)throw new IllegalArgumentException("Snapshot de Refugo excede 100000 IDs");
        }

        String now=ZonedDateTime.now(AppConfig.ZONE).toString();
        List<StaleRefugo> stale=new ArrayList<>();
        long total;Long max;
        try(Connection c=db.open()){
            c.setAutoCommit(false);
            try(PreparedStatement q=c.prepareStatement(
                    "SELECT erp_id,data_apon,ordem,qtd_planej,maquina,produto,descricao,cliente,turno,operador,qtd_refugo,motivo,peso_br,qtd_itens,payload_hash,sincronizado_em " +
                            "FROM erp_refugo_raw WHERE data_apon BETWEEN ? AND ? ORDER BY erp_id")){
                q.setString(1,start.toString());q.setString(2,end.toString());
                try(ResultSet rs=q.executeQuery()){
                    while(rs.next()){
                        long id=rs.getLong(1);
                        if(!present.contains(id)){
                            Map<String,Object> payload=new LinkedHashMap<>();
                            for(String column:List.of("erp_id","data_apon","ordem","qtd_planej","maquina","produto","descricao","cliente","turno","operador","qtd_refugo","motivo","peso_br","qtd_itens","payload_hash","sincronizado_em"))
                                payload.put(column,rs.getObject(column));
                            stale.add(new StaleRefugo(id,rs.getString("data_apon"),json.writeValueAsString(payload)));
                        }
                    }
                }
            }

            try(PreparedStatement audit=c.prepareStatement(
                        "INSERT INTO erp_sync_exclusoes(fonte,erp_id,data_apon,connector_id,payload_json,excluido_em) VALUES('refugo',?,?,?,?,?)");
                PreparedStatement analysis=c.prepareStatement(
                        "DELETE FROM erp_refugo_analysis_setor_overrides WHERE erp_id=?");
                PreparedStatement legacy=c.prepareStatement(
                        "DELETE FROM erp_refugo_setor_overrides WHERE erp_id=?");
                PreparedStatement raw=c.prepareStatement(
                        "DELETE FROM erp_refugo_raw WHERE erp_id=?")){
                for(StaleRefugo row:stale){
                    audit.setLong(1,row.erpId());audit.setString(2,row.date());
                    audit.setString(3,Norm.text(connectorId));audit.setString(4,row.payloadJson());
                    audit.setString(5,now);audit.addBatch();
                    analysis.setLong(1,row.erpId());analysis.addBatch();
                    legacy.setLong(1,row.erpId());legacy.addBatch();
                    raw.setLong(1,row.erpId());raw.addBatch();
                }
                if(!stale.isEmpty()){
                    audit.executeBatch();analysis.executeBatch();legacy.executeBatch();raw.executeBatch();
                }
            }

            try(Statement s=c.createStatement();ResultSet rs=s.executeQuery(
                    "SELECT COUNT(*),MAX(erp_id) FROM erp_refugo_raw")){
                rs.next();total=rs.getLong(1);max=rs.getObject(2)==null?null:rs.getLong(2);
            }
            try(PreparedStatement p=c.prepareStatement(
                    "INSERT INTO erp_sync_estado(fonte,ultimo_recebimento,ultimo_erp_id,total_registros) VALUES('refugo',?,?,?) " +
                            "ON CONFLICT(fonte) DO UPDATE SET ultimo_recebimento=excluded.ultimo_recebimento,ultimo_erp_id=excluded.ultimo_erp_id,total_registros=excluded.total_registros")){
                p.setString(1,now);if(max==null)p.setNull(2,Types.BIGINT);else p.setLong(2,max);p.setLong(3,total);p.executeUpdate();
            }
            try(PreparedStatement p=c.prepareStatement(
                    "INSERT INTO erp_sync_lotes(fonte,connector_id,sent_at,recebidos,alterados,excluidos,recebido_em) VALUES('refugo',?,?,?,0,?,?)")){
                p.setString(1,Norm.text(connectorId));p.setString(2,Norm.text(sentAt));
                p.setInt(3,present.size());p.setInt(4,stale.size());p.setString(5,now);p.executeUpdate();
            }
            c.commit();
        }catch(Exception e){throw new IllegalStateException(e);}

        Map<String,Object> out=new LinkedHashMap<>();
        out.put("snapshot_reconciliado",true);
        out.put("snapshot_inicio",start.toString());
        out.put("snapshot_fim",end.toString());
        out.put("snapshot_presentes",present.size());
        out.put("excluidos",stale.size());
        out.put("total_registros",total);
        return out;
    }

    private record StaleRefugo(long erpId,String date,String payloadJson){}

    private void upsertApontamento(Connection c,long id,Map<String,Object>r,String h,String now)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO erp_apontamento_raw(erp_id,ordem,data_apon,produto,descricao,maquina,qtd_plan,cliente,turno,caixa_ini,caixa_fin,qtd_cx,conteudo,qtd_apon,operador,payload_hash,sincronizado_em) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(erp_id) DO UPDATE SET ordem=excluded.ordem,data_apon=excluded.data_apon,produto=excluded.produto,descricao=excluded.descricao,maquina=excluded.maquina,qtd_plan=excluded.qtd_plan,cliente=excluded.cliente,turno=excluded.turno,caixa_ini=excluded.caixa_ini,caixa_fin=excluded.caixa_fin,qtd_cx=excluded.qtd_cx,conteudo=excluded.conteudo,qtd_apon=excluded.qtd_apon,operador=excluded.operador,payload_hash=excluded.payload_hash,sincronizado_em=excluded.sincronizado_em")){int i=1;p.setLong(i++,id);setLongObj(p,i++,r.get("ordem"));p.setString(i++,date(r.get("data_apon")));p.setString(i++,Norm.text(r.get("produto")));p.setString(i++,Norm.text(r.get("descricao")));p.setString(i++,Norm.text(r.get("maquina")));setDoubleObj(p,i++,r.get("qtd_plan"));p.setString(i++,Norm.text(r.get("cliente")));p.setString(i++,Norm.token(r.get("turno")));setLongObj(p,i++,r.get("caixa_ini"));setLongObj(p,i++,r.get("caixa_fin"));setLongObj(p,i++,r.get("qtd_cx"));setLongObj(p,i++,r.get("conteudo"));setDoubleObj(p,i++,r.get("qtd_apon"));p.setString(i++,Norm.token(r.get("operador")));p.setString(i++,h);p.setString(i,now);p.executeUpdate();}}
    private void upsertRefugo(Connection c,long id,Map<String,Object>r,String h,String now)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO erp_refugo_raw(erp_id,data_apon,ordem,qtd_planej,maquina,produto,descricao,cliente,turno,operador,qtd_refugo,motivo,peso_br,qtd_itens,payload_hash,primeiro_sincronizado_em,sincronizado_em) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(erp_id) DO UPDATE SET data_apon=excluded.data_apon,ordem=excluded.ordem,qtd_planej=excluded.qtd_planej,maquina=excluded.maquina,produto=excluded.produto,descricao=excluded.descricao,cliente=excluded.cliente,turno=excluded.turno,operador=excluded.operador,qtd_refugo=excluded.qtd_refugo,motivo=excluded.motivo,peso_br=excluded.peso_br,qtd_itens=excluded.qtd_itens,payload_hash=excluded.payload_hash,sincronizado_em=excluded.sincronizado_em")){int i=1;p.setLong(i++,id);p.setString(i++,date(r.get("data_apon")));setLongObj(p,i++,r.get("ordem"));setDoubleObj(p,i++,r.get("qtd_planej"));p.setString(i++,Norm.text(r.get("maquina")));p.setString(i++,Norm.text(r.get("produto")));p.setString(i++,Norm.text(r.get("descricao")));p.setString(i++,Norm.text(r.get("cliente")));p.setString(i++,Norm.token(r.get("turno")));p.setString(i++,Norm.token(r.get("operador")));setDoubleObj(p,i++,r.get("qtd_refugo"));p.setString(i++,Norm.token(r.get("motivo")));setDoubleObj(p,i++,r.get("peso_br"));setLongObj(p,i++,r.get("qtd_itens"));p.setString(i++,h);p.setString(i++,now);p.setString(i,now);p.executeUpdate();}}
    private String hash(Map<String,Object>r){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(new TreeMap<>(r))));}catch(Exception e){throw new IllegalStateException(e);}}
    private static long longVal(Object v){try{return Long.parseLong(Norm.text(v).replace(".0",""));}catch(Exception e){return 0;}}
    private static String date(Object v){var d=Norm.isoDate(v);if(d==null)throw new IllegalArgumentException("Registro sem data_apon");return d.toString();}
    private static void setLongObj(PreparedStatement p,int i,Object v)throws SQLException{String s=Norm.text(v);if(s.isBlank())p.setNull(i,Types.BIGINT);else p.setLong(i,longVal(v));}
    private static void setDoubleObj(PreparedStatement p,int i,Object v)throws SQLException{String s=Norm.text(v);if(s.isBlank())p.setNull(i,Types.DOUBLE);else p.setDouble(i,Double.parseDouble(s.replace(',','.')));}
}
