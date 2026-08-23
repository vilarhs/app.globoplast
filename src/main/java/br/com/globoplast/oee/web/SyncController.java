package br.com.globoplast.oee.web;

import br.com.globoplast.oee.config.AppConfig;
import br.com.globoplast.oee.service.SyncService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
public class SyncController {
    private static final int MAX_RECORDS=5000;private static final int MAX_SKEW=300;
    private final SyncService sync;private final JsonMapper json;
    public SyncController(SyncService sync,JsonMapper json){this.sync=sync;this.json=json;}
    @GetMapping("/health") public Map<String,Object>health(){return Map.of("ok",true,"servico","globoplast-java","versao",AppConfig.VERSION);}
    @GetMapping("/java-sync/v1/health") public Map<String,Object>javaSyncHealth(){return Map.of("ok",true,"servico","globoplast-java","versao",AppConfig.VERSION);}
    @PostMapping(path={"/sync/v1/status","/java-sync/v1/status"},consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)public ResponseEntity<?>status(@RequestBody byte[]body,HttpServletRequest req){if(!auth(body,req))return error(HttpStatus.UNAUTHORIZED,"Autenticação inválida");return ResponseEntity.ok(Map.of("ok",true,"servico","globoplast-java","versao",AppConfig.VERSION,"banco",AppConfig.dbFile().toString(),"status",sync.status()));}
    @PostMapping(path={"/sync/v1/catalogo","/java-sync/v1/catalogo"},consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)public ResponseEntity<?>catalogo(@RequestBody byte[]body,HttpServletRequest req){if(!auth(body,req))return error(HttpStatus.UNAUTHORIZED,"Autenticação inválida");try{Map<String,Object>p=json.readValue(body,new TypeReference<>(){});List<String>setores=new ArrayList<>();Object ss=p.get("setores");if(ss instanceof List<?> list)for(Object x:list)setores.add(String.valueOf(x));List<Map<String,Object>>maquinas=mapList(p.get("maquinas"));List<Map<String,Object>>historicas=mapList(p.get("maquinas_historicas"));return ResponseEntity.ok(Map.of("ok",true,"resultado",sync.importCatalog(setores,maquinas,historicas,String.valueOf(p.getOrDefault("connector_id","")),String.valueOf(p.getOrDefault("sent_at","")))));}catch(IllegalArgumentException e){return error(HttpStatus.BAD_REQUEST,e.getMessage());}catch(Exception e){return error(HttpStatus.INTERNAL_SERVER_ERROR,"Falha interna ao importar catálogo");}}
    @PostMapping(path={"/sync/v1/diagnostico-oee","/java-sync/v1/diagnostico-oee"},consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)public ResponseEntity<?>diagOee(@RequestBody byte[]body,HttpServletRequest req){if(!auth(body,req))return error(HttpStatus.UNAUTHORIZED,"Autenticação inválida");try{return ResponseEntity.ok(Map.of("ok",true,"diagnostico",sync.oeeDiagnostics()));}catch(Exception e){return error(HttpStatus.INTERNAL_SERVER_ERROR,"Falha interna no diagnóstico OEE");}}
    @PostMapping(path={"/sync/v1/apontamento","/java-sync/v1/apontamento"},consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)public ResponseEntity<?>ap(@RequestBody byte[]body,HttpServletRequest req){return receive("apontamento",body,req);}
    @PostMapping(path={"/sync/v1/refugo","/java-sync/v1/refugo"},consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)public ResponseEntity<?>rf(@RequestBody byte[]body,HttpServletRequest req){return receive("refugo",body,req);}
    private static List<Map<String,Object>> mapList(Object value){List<Map<String,Object>>out=new ArrayList<>();if(value instanceof List<?> list)for(Object x:list){if(!(x instanceof Map<?,?>m))continue;Map<String,Object>n=new LinkedHashMap<>();for(var e:m.entrySet())n.put(String.valueOf(e.getKey()),e.getValue());out.add(n);}return out;}
    private ResponseEntity<?>receive(String source,byte[]body,HttpServletRequest req){
        if(!auth(body,req))return error(HttpStatus.UNAUTHORIZED,"Autenticação inválida");
        try{
            Map<String,Object>p=json.readValue(body,new TypeReference<>(){});
            Object rs=p.get("records");
            if(!(rs instanceof List<?> list))return error(HttpStatus.BAD_REQUEST,"records precisa ser uma lista");
            if(list.size()>MAX_RECORDS)return error(HttpStatus.PAYLOAD_TOO_LARGE,"Lote excede 5000 registros");
            List<Map<String,Object>>records=new ArrayList<>();
            for(Object x:list){
                if(!(x instanceof Map<?,?>m))return error(HttpStatus.BAD_REQUEST,"Cada registro precisa ser um objeto");
                Map<String,Object>n=new LinkedHashMap<>();
                for(var e:m.entrySet())n.put(String.valueOf(e.getKey()),e.getValue());
                records.add(n);
            }

            String connector=String.valueOf(p.getOrDefault("connector_id",""));
            String sentAt=String.valueOf(p.getOrDefault("sent_at",""));
            boolean complete=Boolean.parseBoolean(String.valueOf(p.getOrDefault("snapshot_complete",false)));
            LocalDate snapshotStart=null,snapshotEnd=null;
            List<Long> snapshotIds=List.of();
            if(complete){
                if(!"refugo".equals(source))throw new IllegalArgumentException("Snapshot completo é permitido somente para Refugo");
                snapshotStart=isoDate(p.get("snapshot_start"),"snapshot_start");
                snapshotEnd=isoDate(p.get("snapshot_end"),"snapshot_end");
                snapshotIds=positiveIds(p.get("snapshot_erp_ids"));
            }

            Map<String,Object>result=new LinkedHashMap<>(sync.importBatch(source,records,connector,sentAt));
            if(complete)result.putAll(sync.reconcileRefugoSnapshot(snapshotStart,snapshotEnd,snapshotIds,connector,sentAt));
            return ResponseEntity.ok(Map.of("ok",true,"resultado",result));
        }catch(IllegalArgumentException e){return error(HttpStatus.BAD_REQUEST,e.getMessage());}
        catch(Exception e){return error(HttpStatus.INTERNAL_SERVER_ERROR,"Falha interna ao processar sincronização");}
    }

    private static List<Long> positiveIds(Object value){
        if(!(value instanceof List<?> list))throw new IllegalArgumentException("snapshot_erp_ids precisa ser uma lista");
        LinkedHashSet<Long> ids=new LinkedHashSet<>();
        for(Object raw:list){
            long id;
            try{id=raw instanceof Number n?n.longValue():Long.parseLong(String.valueOf(raw));}
            catch(Exception e){throw new IllegalArgumentException("snapshot_erp_ids contém ERP_ID inválido");}
            if(id<=0)throw new IllegalArgumentException("snapshot_erp_ids contém ERP_ID inválido");
            ids.add(id);
            if(ids.size()>100_000)throw new IllegalArgumentException("Snapshot de Refugo excede 100000 IDs");
        }
        return new ArrayList<>(ids);
    }

    private static LocalDate isoDate(Object value,String field){
        String text=value==null?"":String.valueOf(value).trim();
        if(text.isBlank())throw new IllegalArgumentException(field+" é obrigatório");
        try{return LocalDate.parse(text);}
        catch(Exception e){throw new IllegalArgumentException(field+" precisa estar no formato AAAA-MM-DD");}
    }
    private boolean auth(byte[]body,HttpServletRequest req){if(body.length>8*1024*1024)return false;String token=AppConfig.syncToken();if(token.isBlank())return false;String auth=req.getHeader("Authorization");if(auth==null||!auth.startsWith("Bearer ")||!constant(auth.substring(7).trim(),token))return false;String ts=req.getHeader("X-Globoplast-Timestamp");long t;try{t=Long.parseLong(ts);}catch(Exception e){return false;}if(Math.abs(Instant.now().getEpochSecond()-t)>MAX_SKEW)return false;String sig=req.getHeader("X-Globoplast-Signature");if(sig==null||!sig.startsWith("sha256="))return false;try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));mac.update(ts.getBytes(StandardCharsets.US_ASCII));mac.update((byte)'.');String expected=HexFormat.of().formatHex(mac.doFinal(body));return constant(sig.substring(7).trim(),expected);}catch(Exception e){return false;}}
    private static boolean constant(String a,String b){return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    private static ResponseEntity<Map<String,Object>>error(HttpStatus s,String m){return ResponseEntity.status(s).body(Map.of("ok",false,"erro",m));}
}
