package br.com.globoplast.oee.service;

import br.com.globoplast.oee.db.Database;
import br.com.globoplast.oee.util.Norm;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class StockService {
    private final Database db;
    public StockService(Database db){this.db=db;}

    public List<StockItem> search(String value){
        String query=Norm.text(value);
        if(query.isBlank())return List.of();
        String like="%"+query+"%";
        List<StockItem> rows=new ArrayList<>();
        String sql="SELECT erp_id,ordem,produto,descricao,lote,localizacao,divisao,data_producao,quantidade,qtd_caixas,conteudo FROM erp_estoque_raw WHERE quantidade>0 AND (CAST(ordem AS TEXT) LIKE ? OR produto LIKE ? COLLATE NOCASE OR descricao LIKE ? COLLATE NOCASE OR lote LIKE ? COLLATE NOCASE OR localizacao LIKE ? COLLATE NOCASE) ORDER BY produto,localizacao LIMIT 1000";
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement(sql)){
            for(int i=1;i<=5;i++)p.setString(i,like);
            try(ResultSet r=p.executeQuery()){while(r.next())rows.add(new StockItem(r.getLong(1),Norm.text(r.getObject(2)),Norm.text(r.getString(3)),Norm.text(r.getString(4)),Norm.text(r.getString(5)),Norm.text(r.getString(6)),Norm.text(r.getString(7)),Norm.isoDate(r.getString(8)),r.getDouble(9),r.getDouble(10),r.getInt(11)));}
        }catch(Exception e){throw new IllegalStateException(e);}
        return rows;
    }

    public record StockItem(long erpId,String order,String product,String description,String lot,String location,
                            String division,LocalDate productionDate,double quantity,double boxesQuantity,int content){
        public long quantityPcs(){return Math.round(quantity*1000.0);}
        public long boxes(){return Math.round(boxesQuantity);}
    }
}
