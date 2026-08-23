package br.com.globoplast.oee.service;

import br.com.globoplast.oee.db.Database;
import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.model.Sector;
import br.com.globoplast.oee.model.User;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.Locale;

@Service
public class CatalogService {
    private final Database db;
    private final AuthService auth;
    public CatalogService(Database db,AuthService auth){this.db=db;this.auth=auth;}

    public List<Sector> sectorEntries(){List<Sector>x=new ArrayList<>();try(Connection c=db.open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT id,setor FROM setores ORDER BY setor COLLATE NOCASE")){while(r.next())x.add(new Sector(r.getLong(1),r.getString(2)));}catch(SQLException e){throw new IllegalStateException(e);}return x;}
    public List<String> sectors(){List<String>x=new ArrayList<>();try(Connection c=db.open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT setor FROM setores ORDER BY setor COLLATE NOCASE")){while(r.next())x.add(r.getString(1));}catch(SQLException e){throw new IllegalStateException(e);}return x;}
    public List<Machine> machines(){List<Machine>x=new ArrayList<>();try(Connection c=db.open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT id,maquina,capacidade,setor FROM maquinas ORDER BY maquina COLLATE NOCASE")){while(r.next())x.add(new Machine(r.getLong(1),r.getString(2),r.getInt(3),r.getString(4)));}catch(SQLException e){throw new IllegalStateException(e);}return x;}
    public Map<String,Machine> machineMap(){Map<String,Machine>m=new LinkedHashMap<>();for(Machine x:machines())m.put(x.name(),x);return m;}
    public List<Machine> allowedMachines(User u){if(u==null||u.isReadOnly())return List.of();if(u.isAdmin())return machines();if(u.sector()==null)return List.of();return machines().stream().filter(m->u.sector().equalsIgnoreCase(m.sector())).toList();}
    public void saveSector(Long id,String sector){
        String v=sector==null?"":sector.trim().toUpperCase(Locale.ROOT);
        if(v.isBlank())throw new IllegalArgumentException("Informe o setor.");
        try(Connection c=db.open()){
            if(id==null){
                try(PreparedStatement p=c.prepareStatement("INSERT INTO setores(setor) VALUES(?)")){p.setString(1,v);p.executeUpdate();}
            }else{
                String old="";
                try(PreparedStatement q=c.prepareStatement("SELECT setor FROM setores WHERE id=?")){q.setLong(1,id);ResultSet r=q.executeQuery();if(r.next())old=r.getString(1);}
                if(old.isBlank())throw new IllegalArgumentException("Setor não encontrado.");
                try(PreparedStatement p=c.prepareStatement("UPDATE setores SET setor=? WHERE id=?")){p.setString(1,v);p.setLong(2,id);p.executeUpdate();}
                if(!old.equalsIgnoreCase(v)){
                    try(PreparedStatement p=c.prepareStatement("UPDATE maquinas SET setor=? WHERE setor=? COLLATE NOCASE")){p.setString(1,v);p.setString(2,old);p.executeUpdate();}
                    try(PreparedStatement p=c.prepareStatement("UPDATE usuarios SET setor=? WHERE perfil='padrao' AND setor=? COLLATE NOCASE")){p.setString(1,v);p.setString(2,old);p.executeUpdate();}
                }
            }
        }catch(SQLException e){
            if(String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT).contains("unique"))throw new IllegalArgumentException("O setor '"+v+"' já está cadastrado no sistema.");
            throw new IllegalStateException(e);
        }
    }
    public void deleteSector(long id){
        try(Connection c=db.open()){
            String name=null;
            try(PreparedStatement q=c.prepareStatement("SELECT setor FROM setores WHERE id=? LIMIT 1")){
                q.setLong(1,id); ResultSet rs=q.executeQuery(); if(rs.next()) name=rs.getString(1);
            }
            if(name==null) throw new IllegalArgumentException("Setor não encontrado.");
            try(PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM maquinas WHERE setor=? COLLATE NOCASE")){
                q.setString(1,name); ResultSet rs=q.executeQuery();
                if(rs.next()&&rs.getInt(1)>0)
                    throw new IllegalArgumentException("Não é possível excluir o setor enquanto houver máquinas vinculadas a ele. Reatribua ou exclua essas máquinas primeiro.");
            }
            try(PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM usuarios WHERE perfil='padrao' AND setor=? COLLATE NOCASE")){
                q.setString(1,name); ResultSet rs=q.executeQuery();
                if(rs.next()&&rs.getInt(1)>0) throw new IllegalArgumentException("Não é possível excluir o setor enquanto houver usuários do perfil Padrão vinculados a ele.");
            }
            try(PreparedStatement p=c.prepareStatement("DELETE FROM setores WHERE id=?")){p.setLong(1,id);p.executeUpdate();}
        }catch(SQLException e){throw new IllegalStateException(e);}
    }
    public void saveMachine(Long id,String name,int capacity,String sector){
        if(name==null||name.isBlank())throw new IllegalArgumentException("O nome da máquina não pode ficar em branco.");
        if(capacity<=0)throw new IllegalArgumentException("A capacidade da máquina deve ser preenchida com um valor maior que zero.");
        String machine=name.trim().toUpperCase(Locale.ROOT);
        String targetSector=sector==null?null:sector.trim().toUpperCase(Locale.ROOT);
        if(targetSector==null||targetSector.isBlank())throw new IllegalArgumentException("Cadastre ou selecione um setor para a máquina.");
        try(Connection c=db.open()){
            try(PreparedStatement q=c.prepareStatement("SELECT 1 FROM setores WHERE setor=? COLLATE NOCASE LIMIT 1")){
                q.setString(1,targetSector); if(!q.executeQuery().next())throw new IllegalArgumentException("Setor inválido.");
            }
            String oldName=null; int oldCapacity=0; String oldSector=null;
            if(id!=null){
                try(PreparedStatement q=c.prepareStatement("SELECT maquina,capacidade,setor FROM maquinas WHERE id=? LIMIT 1")){
                    q.setLong(1,id); ResultSet rs=q.executeQuery(); if(rs.next()){oldName=rs.getString(1);oldCapacity=rs.getInt(2);oldSector=rs.getString(3);}
                }
            }
            if(id==null){
                try(PreparedStatement p=c.prepareStatement("INSERT INTO maquinas(maquina,capacidade,setor) VALUES(?,?,?)")){
                    p.setString(1,machine);p.setInt(2,capacity);p.setString(3,targetSector);p.executeUpdate();
                }
            }else{
                try(PreparedStatement p=c.prepareStatement("UPDATE maquinas SET maquina=?,capacidade=?,setor=? WHERE id=?")){
                    p.setString(1,machine);p.setInt(2,capacity);p.setString(3,targetSector);p.setLong(4,id);p.executeUpdate();
                }
            }
            if(oldName!=null&&!oldName.isBlank()&&oldCapacity>0)rememberMachineSnapshot(c,oldName,oldCapacity,oldSector);
            rememberMachineSnapshot(c,machine,capacity,targetSector);
        }catch(SQLException e){
            if(String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT).contains("unique"))throw new IllegalArgumentException("A máquina '"+machine+"' já está cadastrada no sistema.");
            throw new IllegalStateException(e);
        }
    }

    public void deleteMachine(long id){
        try(Connection c=db.open()){
            try(PreparedStatement q=c.prepareStatement("SELECT maquina,capacidade,setor FROM maquinas WHERE id=? LIMIT 1")){
                q.setLong(1,id); ResultSet rs=q.executeQuery(); if(rs.next())rememberMachineSnapshot(c,rs.getString(1),rs.getInt(2),rs.getString(3));
            }
            try(PreparedStatement p=c.prepareStatement("DELETE FROM maquinas WHERE id=?")){p.setLong(1,id);p.executeUpdate();}
        }catch(SQLException e){throw new IllegalStateException(e);}
    }

    private static void rememberMachineSnapshot(Connection c,String machine,int capacity,String sector) throws SQLException{
        if(machine==null||machine.isBlank()||capacity<=0)return;
        try(PreparedStatement p=c.prepareStatement("INSERT INTO maquinas_snapshot(maquina,capacidade,setor,atualizado_em) VALUES(?,?,?,CURRENT_TIMESTAMP) ON CONFLICT(maquina) DO UPDATE SET capacidade=excluded.capacidade,setor=excluded.setor,atualizado_em=excluded.atualizado_em")){
            p.setString(1,machine.trim().toUpperCase(Locale.ROOT));p.setInt(2,capacity);p.setString(3,sector);p.executeUpdate();
        }
    }
}
