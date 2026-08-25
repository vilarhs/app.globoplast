package br.com.globoplast.oee.config;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

public final class AppConfig {
    private AppConfig() {}

    public static final String VERSION = "0.0.130";
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    public static final int PBKDF2_ITERATIONS = 260_000;
    public static final String DEFAULT_LANGUAGE = "pt-BR";
    public static final List<String> LANGUAGES = List.of("pt-BR", "en-US");
    public static final String PROFILE_ADMIN = "administrador";
    public static final String PROFILE_STANDARD = "padrao";
    public static final String PROFILE_FOLLOW = "acompanhamento";
    public static final String PROFILE_CHECKER = "conferente";
    public static final int PAGE_SIZE = 20;

    public static Path dbFile() {
        String configured = System.getenv("GLOBOPLAST_DB");
        if (configured == null || configured.isBlank()) {
            configured = "/var/lib/globoplast/database.db";
        }
        return Path.of(configured).toAbsolutePath().normalize();
    }

    public static String syncToken() {
        String[] keys = {
                "GLOBOPLAST_SYNC_TOKEN", "GLOBOPLAST_SYNC_SECRET",
                "GLOBOPLAST_ERP_SYNC_TOKEN", "GLOBOPLAST_ERP_SYNC_SECRET",
                "SYNC_TOKEN", "SYNC_SECRET", "API_TOKEN"
        };
        for (String key : keys) {
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }
}
