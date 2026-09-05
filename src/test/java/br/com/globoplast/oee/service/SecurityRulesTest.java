package br.com.globoplast.oee.service;

import br.com.globoplast.oee.config.AppConfig;
import br.com.globoplast.oee.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityRulesTest {
    @Test
    void passwordAndProfilesKeepTheirPermissions() {
        PasswordService passwords = new PasswordService();
        PasswordService.Hash stored = passwords.hash("senha-de-teste");

        assertTrue(passwords.matches("senha-de-teste", stored.saltHex(), stored.hashHex()));
        assertFalse(passwords.matches("senha-errada", stored.saltHex(), stored.hashHex()));

        User standard = new User(1, "PADRAO", false, AppConfig.PROFILE_STANDARD, "IMPRESSÃO", "pt-BR");
        User follow = new User(2, "ACOMPANHA", false, AppConfig.PROFILE_FOLLOW, null, "pt-BR");
        User checker = new User(3, "CONFERE", false, AppConfig.PROFILE_CHECKER, null, "pt-BR");

        assertTrue(standard.canModifyLaunches());
        assertFalse(follow.canModifyLaunches());
        assertTrue(follow.canSeeSummaries());
        assertFalse(checker.canModifyLaunches());
        assertFalse(checker.canSeeSummaries());
    }
}
