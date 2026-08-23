package br.com.globoplast.oee.db;

import br.com.globoplast.oee.config.AppConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.sql.*;

@Component
public class Database {
    public Connection open() throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + AppConfig.dbFile());
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA busy_timeout=10000");
            st.execute("PRAGMA foreign_keys=ON");
        }
        return c;
    }

    @PostConstruct
    public void initialize() throws Exception {
        Files.createDirectories(AppConfig.dbFile().getParent());
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS setores (id INTEGER PRIMARY KEY AUTOINCREMENT, setor TEXT UNIQUE NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS maquinas (id INTEGER PRIMARY KEY AUTOINCREMENT, maquina TEXT UNIQUE NOT NULL, capacidade INTEGER NOT NULL, setor TEXT NOT NULL)");
            // Snapshot local de metadados de máquina: preserva capacidade/setor conhecidos
            // mesmo se o cadastro atual for renomeado ou removido posteriormente.
            s.executeUpdate("CREATE TABLE IF NOT EXISTS maquinas_snapshot (maquina TEXT PRIMARY KEY COLLATE NOCASE, capacidade INTEGER NOT NULL, setor TEXT, atualizado_em TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS usuarios (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario TEXT NOT NULL UNIQUE COLLATE NOCASE, senha_hash TEXT NOT NULL, senha_salt TEXT NOT NULL, is_admin INTEGER NOT NULL DEFAULT 0 CHECK(is_admin IN (0,1)), perfil TEXT NOT NULL DEFAULT 'padrao', setor TEXT, idioma TEXT NOT NULL DEFAULT 'pt-BR', criado_em TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS sessoes_web (token_hash TEXT PRIMARY KEY, usuario_id INTEGER NOT NULL, criado_em TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, ultimo_uso TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS refugo_arquivos_recentes (id INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT NOT NULL, sha256 TEXT NOT NULL UNIQUE, tamanho INTEGER NOT NULL, conteudo BLOB NOT NULL, total_lancamentos INTEGER NOT NULL DEFAULT 0, total_kg REAL NOT NULL DEFAULT 0, aberto_em TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS erp_apontamento_raw (erp_id INTEGER PRIMARY KEY, ordem INTEGER, data_apon TEXT NOT NULL, produto TEXT, descricao TEXT, maquina TEXT, qtd_plan REAL, cliente TEXT, turno TEXT, caixa_ini INTEGER, caixa_fin INTEGER, qtd_cx INTEGER, conteudo INTEGER, qtd_apon REAL, operador TEXT, payload_hash TEXT NOT NULL, sincronizado_em TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS erp_planejamento_raw (erp_id INTEGER PRIMARY KEY, data_plan TEXT NOT NULL, ordem INTEGER, produto TEXT, descricao TEXT, qtd_plan REAL, qtd_prod REAL, qtd_ent REAL, flag_exe TEXT, processo INTEGER, lote TEXT, qtd_perda REAL, conteudo INTEGER, payload_hash TEXT NOT NULL, sincronizado_em TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS erp_refugo_raw (erp_id INTEGER PRIMARY KEY, data_apon TEXT NOT NULL, ordem INTEGER, qtd_planej REAL, maquina TEXT, produto TEXT, descricao TEXT, cliente TEXT, turno TEXT, operador TEXT, qtd_refugo REAL, motivo TEXT, peso_br REAL, qtd_itens INTEGER, payload_hash TEXT NOT NULL, primeiro_sincronizado_em TEXT NOT NULL, sincronizado_em TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS erp_refugo_setor_overrides (erp_id INTEGER PRIMARY KEY, setor TEXT NOT NULL, atualizado_por TEXT NOT NULL, atualizado_em TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS erp_refugo_analysis_setor_overrides (analysis_id TEXT PRIMARY KEY, erp_id INTEGER NOT NULL, setor TEXT NOT NULL, atualizado_por TEXT NOT NULL, atualizado_em TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS erp_sync_lotes (id INTEGER PRIMARY KEY AUTOINCREMENT, fonte TEXT NOT NULL, connector_id TEXT, sent_at TEXT, recebidos INTEGER NOT NULL DEFAULT 0, alterados INTEGER NOT NULL DEFAULT 0, excluidos INTEGER NOT NULL DEFAULT 0, recebido_em TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS erp_sync_exclusoes (id INTEGER PRIMARY KEY AUTOINCREMENT, fonte TEXT NOT NULL, erp_id INTEGER NOT NULL, data_apon TEXT, connector_id TEXT, payload_json TEXT, excluido_em TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS erp_sync_estado (fonte TEXT PRIMARY KEY, ultimo_recebimento TEXT, ultimo_erp_id INTEGER, total_registros INTEGER NOT NULL DEFAULT 0)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS erp_lancamento_overrides (erp_chave TEXT PRIMARY KEY, oculto INTEGER NOT NULL DEFAULT 0, payload_json TEXT, atualizado_em TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS lancamentos_lixeira (id INTEGER PRIMARY KEY AUTOINCREMENT, tipo TEXT NOT NULL, chave_origem TEXT NOT NULL, payload_json TEXT NOT NULL, override_anterior_existia INTEGER NOT NULL DEFAULT 0, override_anterior_json TEXT, excluido_por TEXT NOT NULL, excluido_em TEXT NOT NULL, expira_em TEXT NOT NULL, expira_epoch INTEGER NOT NULL, UNIQUE(tipo,chave_origem))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS historico_oee (id INTEGER PRIMARY KEY AUTOINCREMENT, data TEXT NOT NULL, data_br TEXT NOT NULL, maquina TEXT NOT NULL, produto TEXT NOT NULL, numero_op TEXT, op_producao_detalhe TEXT, horas_programadas REAL NOT NULL, capacidade_24h INTEGER NOT NULL, turno_a_pcs INTEGER NOT NULL, turno_b_pcs INTEGER NOT NULL, turno_c_pcs INTEGER NOT NULL, total_produzido_pcs INTEGER NOT NULL, peso_unitario_g REAL NOT NULL, refugo_a_kg REAL NOT NULL, refugo_b_kg REAL NOT NULL, refugo_c_kg REAL NOT NULL, refugo_total_kg REAL NOT NULL, refugo_total_pcs INTEGER NOT NULL, refugo_pct REAL NOT NULL, qtd_trocas INTEGER NOT NULL, tempo_setup_hrs REAL NOT NULL, horas_paradas_quebra REAL NOT NULL, tempo_produzindo_hrs REAL NOT NULL, disponibilidade_pct REAL NOT NULL, desempenho_pct REAL NOT NULL, qualidade_pct REAL NOT NULL, oee_pct REAL NOT NULL, problema TEXT, acao_tomada TEXT, hora_lancamento TEXT, movimentado_em TEXT)");

            // Migrações defensivas para bancos criados por versões anteriores.
            ensureColumn(c, "historico_oee", "numero_op", "ALTER TABLE historico_oee ADD COLUMN numero_op TEXT");
            ensureColumn(c, "historico_oee", "op_producao_detalhe", "ALTER TABLE historico_oee ADD COLUMN op_producao_detalhe TEXT");
            ensureColumn(c, "historico_oee", "hora_lancamento", "ALTER TABLE historico_oee ADD COLUMN hora_lancamento TEXT");
            ensureColumn(c, "historico_oee", "movimentado_em", "ALTER TABLE historico_oee ADD COLUMN movimentado_em TEXT");
            ensureColumn(c, "usuarios", "setor", "ALTER TABLE usuarios ADD COLUMN setor TEXT");
            ensureColumn(c, "usuarios", "perfil", "ALTER TABLE usuarios ADD COLUMN perfil TEXT NOT NULL DEFAULT 'padrao'");
            ensureColumn(c, "usuarios", "idioma", "ALTER TABLE usuarios ADD COLUMN idioma TEXT NOT NULL DEFAULT 'pt-BR'");
            ensureColumn(c, "erp_sync_lotes", "excluidos", "ALTER TABLE erp_sync_lotes ADD COLUMN excluidos INTEGER NOT NULL DEFAULT 0");
            ensureColumn(c, "erp_sync_exclusoes", "payload_json", "ALTER TABLE erp_sync_exclusoes ADD COLUMN payload_json TEXT");
            ensureColumn(c, "erp_refugo_raw", "primeiro_sincronizado_em", "ALTER TABLE erp_refugo_raw ADD COLUMN primeiro_sincronizado_em TEXT");
            s.executeUpdate("UPDATE erp_refugo_raw SET primeiro_sincronizado_em=sincronizado_em WHERE primeiro_sincronizado_em IS NULL OR TRIM(primeiro_sincronizado_em)=''");
            s.executeUpdate("UPDATE usuarios SET idioma='pt-BR' WHERE idioma IS NULL OR TRIM(idioma) NOT IN ('pt-BR','en-US')");
            s.executeUpdate("UPDATE usuarios SET perfil='administrador' WHERE is_admin=1");
            s.executeUpdate("UPDATE usuarios SET perfil='acompanhamento' WHERE is_admin=0 AND LOWER(TRIM(perfil))='visualizador'");
            s.executeUpdate("UPDATE usuarios SET perfil='padrao' WHERE is_admin=0 AND (perfil IS NULL OR TRIM(perfil)='' OR LOWER(TRIM(perfil)) NOT IN ('padrao','acompanhamento','conferente'))");
            s.executeUpdate("UPDATE usuarios SET usuario=UPPER(TRIM(usuario))");
            s.executeUpdate("UPDATE historico_oee SET movimentado_em=data||'T'||CASE WHEN TRIM(COALESCE(hora_lancamento,''))='' THEN '00:00:00' ELSE substr(hora_lancamento,1,8) END||'-03:00' WHERE movimentado_em IS NULL OR TRIM(movimentado_em)=''");
            s.executeUpdate("INSERT INTO maquinas_snapshot(maquina,capacidade,setor,atualizado_em) SELECT maquina,capacidade,setor,CURRENT_TIMESTAMP FROM maquinas WHERE capacidade>0 ON CONFLICT(maquina) DO UPDATE SET capacidade=excluded.capacidade,setor=excluded.setor,atualizado_em=excluded.atualizado_em");

            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_apontamento_data ON erp_apontamento_raw(data_apon)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_apontamento_maquina_data ON erp_apontamento_raw(maquina, data_apon)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_apontamento_data_sync ON erp_apontamento_raw(data_apon, sincronizado_em)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_apontamento_sync ON erp_apontamento_raw(sincronizado_em)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_apontamento_turno_data ON erp_apontamento_raw(turno, data_apon)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_apontamento_ordem_produto ON erp_apontamento_raw(ordem, produto)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_apontamento_produto_sync ON erp_apontamento_raw(UPPER(REPLACE(TRIM(produto),' ','')), sincronizado_em DESC)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_planejamento_ordem_produto ON erp_planejamento_raw(ordem, produto)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_planejamento_data ON erp_planejamento_raw(data_plan)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_refugo_data ON erp_refugo_raw(data_apon)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_refugo_maquina_data ON erp_refugo_raw(maquina, data_apon)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_refugo_data_sync ON erp_refugo_raw(data_apon, sincronizado_em)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_refugo_setor_override ON erp_refugo_setor_overrides(setor)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_refugo_analysis_setor_override ON erp_refugo_analysis_setor_overrides(setor)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_refugo_analysis_erp ON erp_refugo_analysis_setor_overrides(erp_id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_sync_exclusoes_fonte_data ON erp_sync_exclusoes(fonte, excluido_em)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_erp_sync_exclusoes_erp ON erp_sync_exclusoes(fonte, erp_id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_lancamentos_lixeira_expira ON lancamentos_lixeira(expira_epoch)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_lancamentos_lixeira_excluido ON lancamentos_lixeira(excluido_em DESC)");
            // Consultas mais frequentes das abas Lançamentos/Resumo. Os índices
            // são idempotentes e criados também em bancos já existentes.
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_historico_oee_data ON historico_oee(data)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_historico_oee_maquina_data ON historico_oee(maquina, data)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_historico_oee_maquina_id ON historico_oee(maquina, id DESC)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_historico_oee_movimentado ON historico_oee(movimentado_em DESC)");
        }
    }

    private static void ensureColumn(Connection c, String table, String column, String ddl) throws SQLException {
        boolean found = false;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) { found = true; break; }
            }
        }
        if (!found) try (Statement st = c.createStatement()) { st.executeUpdate(ddl); }
    }
}
