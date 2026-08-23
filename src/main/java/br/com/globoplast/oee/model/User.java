package br.com.globoplast.oee.model;

public record User(long id, String username, boolean admin, String profile, String sector, String language) {
    public boolean isAdmin() { return admin || "administrador".equalsIgnoreCase(profile); }
    public boolean isReadOnly() {
        return "acompanhamento".equalsIgnoreCase(profile) || "conferente".equalsIgnoreCase(profile);
    }
    public boolean canModifyLaunches() {
        return isAdmin() || "padrao".equalsIgnoreCase(profile);
    }
    public boolean canSeeSummaries() {
        return !"conferente".equalsIgnoreCase(profile);
    }
}
