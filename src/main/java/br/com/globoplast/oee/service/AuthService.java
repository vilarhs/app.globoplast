package br.com.globoplast.oee.service;

import br.com.globoplast.oee.config.AppConfig;
import br.com.globoplast.oee.db.Database;
import br.com.globoplast.oee.model.User;
import br.com.globoplast.oee.util.Norm;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;

@Service
public class AuthService {
    public static final String SESSION_USER = "gp_user";
    private static final String COOKIE = "globoplast_auth_session_v1";
    private final Database db;
    private final PasswordService passwords;
    private final SecureRandom random = new SecureRandom();

    public AuthService(Database db, PasswordService passwords) { this.db=db; this.passwords=passwords; }

    @PostConstruct
    public void ensureAdmin() throws Exception {
        try (Connection c=db.open()) {
            try (PreparedStatement q=c.prepareStatement("SELECT id,perfil FROM usuarios WHERE is_admin=1 ORDER BY id LIMIT 1"); ResultSet rs=q.executeQuery()) {
                if (rs.next()) {
                    if (!AppConfig.PROFILE_ADMIN.equalsIgnoreCase(Norm.text(rs.getString("perfil")))) {
                        try (PreparedStatement up=c.prepareStatement("UPDATE usuarios SET perfil=? WHERE id=?")) {
                            up.setString(1,AppConfig.PROFILE_ADMIN); up.setLong(2,rs.getLong("id")); up.executeUpdate();
                        }
                    }
                    return;
                }
            }

            Long existingAdminId=null;
            try (PreparedStatement q=c.prepareStatement("SELECT id FROM usuarios WHERE usuario=? COLLATE NOCASE LIMIT 1")) {
                q.setString(1,"ADMIN"); ResultSet rs=q.executeQuery(); if(rs.next()) existingAdminId=rs.getLong(1);
            }
            if(existingAdminId!=null){
                try(PreparedStatement up=c.prepareStatement("UPDATE usuarios SET is_admin=1,perfil=? WHERE id=?")){
                    up.setString(1,AppConfig.PROFILE_ADMIN); up.setLong(2,existingAdminId); up.executeUpdate();
                }
                return;
            }

            PasswordService.Hash h=passwords.hash("0000");
            try (PreparedStatement ps=c.prepareStatement("INSERT INTO usuarios(usuario,senha_hash,senha_salt,is_admin,perfil,idioma) VALUES(?,?,?,?,?,?)")) {
                ps.setString(1,"ADMIN"); ps.setString(2,h.hashHex()); ps.setString(3,h.saltHex()); ps.setInt(4,1); ps.setString(5,AppConfig.PROFILE_ADMIN); ps.setString(6,"pt-BR"); ps.executeUpdate();
            }
        }
    }

    public User current() {
        Object u=VaadinSession.getCurrent().getAttribute(SESSION_USER);
        if (u instanceof User user) return refresh(user.id());
        User restored=restoreCookie();
        if (restored!=null) VaadinSession.getCurrent().setAttribute(SESSION_USER, restored);
        return restored;
    }

    public User authenticate(String username, String password) {
        String name=Norm.username(username);
        if (name.isBlank()) return null;
        try (Connection c=db.open(); PreparedStatement ps=c.prepareStatement("SELECT id,usuario,senha_hash,senha_salt,is_admin,perfil,setor,idioma FROM usuarios WHERE usuario=? COLLATE NOCASE LIMIT 1")) {
            ps.setString(1,name);
            ResultSet rs=ps.executeQuery();
            if (!rs.next() || !passwords.matches(password,rs.getString("senha_salt"),rs.getString("senha_hash"))) return null;
            User user=map(rs);
            VaadinSession.getCurrent().setAttribute(SESSION_USER,user);
            String token=createSession(user.id());
            writeCookie(token,30*24*3600);
            return user;
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    public void logout() {
        String token=readCookie();
        if (!token.isBlank()) {
            try (Connection c=db.open(); PreparedStatement ps=c.prepareStatement("DELETE FROM sessoes_web WHERE token_hash=?")) {
                ps.setString(1,sha256(token)); ps.executeUpdate();
            } catch (SQLException ignored) {}
        }
        VaadinSession.getCurrent().setAttribute(SESSION_USER,null);
        writeCookie("",0);
    }

    public List<User> users() {
        List<User> out=new ArrayList<>();
        try (Connection c=db.open(); Statement st=c.createStatement(); ResultSet rs=st.executeQuery("SELECT id,usuario,is_admin,perfil,setor,idioma FROM usuarios ORDER BY is_admin DESC,usuario COLLATE NOCASE")) {
            while(rs.next()) out.add(map(rs));
        } catch(SQLException ex){ throw new IllegalStateException(ex); }
        return out;
    }

    public void saveUser(Long id,String username,String profile,String sector,String password) {
        String name = Norm.username(username);
        if (name.isBlank()) throw new IllegalArgumentException("Informe o nome do usuário.");
        if (name.length() < 3) throw new IllegalArgumentException("O usuário deve ter pelo menos 3 caracteres.");

        String p = normalizeProfile(profile,false);
        boolean admin = AppConfig.PROFILE_ADMIN.equals(p);
        String targetSector = AppConfig.PROFILE_STANDARD.equals(p) ? emptyToNull(sector) : null;
        if (AppConfig.PROFILE_STANDARD.equals(p) && targetSector == null)
            throw new IllegalArgumentException("Selecione o setor do usuário Padrão.");
        if (id == null && (password == null || password.isBlank()))
            throw new IllegalArgumentException("Informe uma senha.");

        try(Connection c=db.open()) {
            if (targetSector != null) {
                try (PreparedStatement q=c.prepareStatement("SELECT 1 FROM setores WHERE setor=? COLLATE NOCASE LIMIT 1")) {
                    q.setString(1,targetSector);
                    if (!q.executeQuery().next()) throw new IllegalArgumentException("Setor inválido.");
                }
            }

            if(id==null) {
                PasswordService.Hash h=passwords.hash(password);
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO usuarios(usuario,senha_hash,senha_salt,is_admin,perfil,setor,idioma) VALUES(?,?,?,?,?,?,?)")){
                    ps.setString(1,name); ps.setString(2,h.hashHex()); ps.setString(3,h.saltHex());
                    ps.setInt(4,admin?1:0); ps.setString(5,p); ps.setString(6,targetSector); ps.setString(7,"pt-BR");
                    ps.executeUpdate();
                }
            } else {
                boolean wasAdmin=false;
                try(PreparedStatement q=c.prepareStatement("SELECT is_admin FROM usuarios WHERE id=? LIMIT 1")){
                    q.setLong(1,id); ResultSet rs=q.executeQuery();
                    if(!rs.next()) throw new IllegalArgumentException("Usuário não encontrado.");
                    wasAdmin=rs.getInt(1)==1;
                }
                if(wasAdmin && !admin){
                    try(PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM usuarios WHERE is_admin=1 AND id<>?")){
                        q.setLong(1,id); ResultSet rs=q.executeQuery();
                        if(rs.next() && rs.getInt(1)==0) throw new IllegalArgumentException("O sistema deve manter pelo menos um usuário Administrador.");
                    }
                }
                try(PreparedStatement ps=c.prepareStatement("UPDATE usuarios SET usuario=?,is_admin=?,perfil=?,setor=? WHERE id=?")){
                    ps.setString(1,name); ps.setInt(2,admin?1:0); ps.setString(3,p); ps.setString(4,targetSector); ps.setLong(5,id); ps.executeUpdate();
                }
                if(password!=null&&!password.isBlank()) changePassword(id,password);
                User cur=current();
                if(cur!=null && cur.id()==id){ User refreshed=refresh(id); if(refreshed!=null) VaadinSession.getCurrent().setAttribute(SESSION_USER,refreshed); }
            }
        }catch(SQLIntegrityConstraintViolationException ex){
            throw new IllegalArgumentException("O usuário '"+name+"' já está cadastrado.");
        }catch(SQLException ex){
            if(String.valueOf(ex.getMessage()).toLowerCase(Locale.ROOT).contains("unique"))
                throw new IllegalArgumentException("O usuário '"+name+"' já está cadastrado.");
            throw new IllegalStateException(ex);
        }
    }

    public void changePassword(long id,String password){
        if(password==null||password.isBlank()) throw new IllegalArgumentException("Informe uma senha.");
        PasswordService.Hash h=passwords.hash(password);
        try(Connection c=db.open();PreparedStatement ps=c.prepareStatement("UPDATE usuarios SET senha_hash=?,senha_salt=? WHERE id=?")){
            ps.setString(1,h.hashHex()); ps.setString(2,h.saltHex()); ps.setLong(3,id); ps.executeUpdate();
        }catch(SQLException ex){ throw new IllegalStateException(ex); }
    }

    public void deleteUser(long id){
        User current=current();
        if(current!=null&&current.id()==id) throw new IllegalArgumentException("Não é possível excluir o usuário atualmente logado.");
        try(Connection c=db.open()){
            boolean admin=false;
            try(PreparedStatement q=c.prepareStatement("SELECT is_admin FROM usuarios WHERE id=? LIMIT 1")){
                q.setLong(1,id); ResultSet rs=q.executeQuery();
                if(!rs.next()) throw new IllegalArgumentException("Usuário não encontrado.");
                admin=rs.getInt(1)==1;
            }
            if(admin){
                try(PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM usuarios WHERE is_admin=1 AND id<>?")){
                    q.setLong(1,id); ResultSet rs=q.executeQuery();
                    if(rs.next()&&rs.getInt(1)==0) throw new IllegalArgumentException("O sistema deve manter pelo menos um usuário Administrador.");
                }
            }
            try(PreparedStatement ps=c.prepareStatement("DELETE FROM sessoes_web WHERE usuario_id=?")){ ps.setLong(1,id); ps.executeUpdate(); }
            try(PreparedStatement ps=c.prepareStatement("DELETE FROM usuarios WHERE id=?")){ ps.setLong(1,id); ps.executeUpdate(); }
        }catch(SQLException ex){ throw new IllegalStateException(ex); }
    }

    public void saveLanguage(long id,String language){
        String lang=AppConfig.LANGUAGES.contains(language)?language:AppConfig.DEFAULT_LANGUAGE;
        try(Connection c=db.open();PreparedStatement ps=c.prepareStatement("UPDATE usuarios SET idioma=? WHERE id=?")){ps.setString(1,lang);ps.setLong(2,id);ps.executeUpdate();}
        catch(SQLException ex){throw new IllegalStateException(ex);}
        User u=refresh(id); if(u!=null) VaadinSession.getCurrent().setAttribute(SESSION_USER,u);
    }

    private User refresh(long id){
        try(Connection c=db.open();PreparedStatement ps=c.prepareStatement("SELECT id,usuario,is_admin,perfil,setor,idioma FROM usuarios WHERE id=?")){ps.setLong(1,id);ResultSet rs=ps.executeQuery();return rs.next()?map(rs):null;}
        catch(SQLException ex){return null;}
    }

    private User restoreCookie(){
        String token=readCookie(); if(token.isBlank()) return null;
        try(Connection c=db.open();PreparedStatement ps=c.prepareStatement("SELECT u.id,u.usuario,u.is_admin,u.perfil,u.setor,u.idioma FROM sessoes_web s JOIN usuarios u ON u.id=s.usuario_id WHERE s.token_hash=? LIMIT 1")){
            ps.setString(1,sha256(token)); ResultSet rs=ps.executeQuery(); if(!rs.next()) return null; User u=map(rs);
            try(PreparedStatement up=c.prepareStatement("UPDATE sessoes_web SET ultimo_uso=CURRENT_TIMESTAMP WHERE token_hash=?")){up.setString(1,sha256(token));up.executeUpdate();}
            return u;
        }catch(SQLException ex){return null;}
    }

    private String createSession(long userId){
        byte[] b=new byte[32];random.nextBytes(b);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        try(Connection c=db.open()){
            c.createStatement().executeUpdate("DELETE FROM sessoes_web WHERE criado_em < datetime('now','-30 days')");
            try(PreparedStatement ps=c.prepareStatement("INSERT INTO sessoes_web(token_hash,usuario_id) VALUES(?,?)")){ps.setString(1,sha256(token));ps.setLong(2,userId);ps.executeUpdate();}
        }catch(SQLException ex){throw new IllegalStateException(ex);}
        return token;
    }

    private static String normalizeProfile(String profile,boolean admin){
        if(admin)return AppConfig.PROFILE_ADMIN;String p=Norm.fold(profile);
        if(p.equals("administrador"))return AppConfig.PROFILE_ADMIN;if(p.equals("acompanhamento")||p.equals("visualizador"))return AppConfig.PROFILE_FOLLOW;if(p.equals("conferente"))return AppConfig.PROFILE_CHECKER;return AppConfig.PROFILE_STANDARD;
    }
    private static String profileDb(String p,boolean admin){return normalizeProfile(p,admin);}
    private static User map(ResultSet rs)throws SQLException{return new User(rs.getLong("id"),Norm.username(rs.getString("usuario")),rs.getInt("is_admin")==1,profileDb(rs.getString("perfil"),rs.getInt("is_admin")==1),emptyToNull(rs.getString("setor")),AppConfig.LANGUAGES.contains(rs.getString("idioma"))?rs.getString("idioma"):"pt-BR");}
    private static String emptyToNull(String s){return s==null||s.isBlank()?null:s.trim();}
    private static String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String readCookie(){Cookie[] cs=VaadinService.getCurrentRequest()==null?null:VaadinService.getCurrentRequest().getCookies();if(cs!=null)for(Cookie c:cs)if(COOKIE.equals(c.getName()))return c.getValue()==null?"":c.getValue();return "";}
    private static void writeCookie(String value,int age){if(VaadinService.getCurrentResponse()==null)return;Cookie c=new Cookie(COOKIE,value);c.setHttpOnly(true);c.setPath("/");c.setMaxAge(age);VaadinService.getCurrentResponse().addCookie(c);}
}
