package br.com.globoplast.oee.service;

import br.com.globoplast.oee.config.AppConfig;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class PasswordService {
    private final SecureRandom random = new SecureRandom();

    public Hash hash(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return new Hash(HexFormat.of().formatHex(salt), derive(password, salt));
    }

    public boolean matches(String password, String saltHex, String expectedHex) {
        try {
            byte[] actual = HexFormat.of().parseHex(derive(password, HexFormat.of().parseHex(saltHex)));
            byte[] expected = HexFormat.of().parseHex(expectedHex);
            return MessageDigest.isEqual(actual, expected);
        } catch (Exception ex) { return false; }
    }

    private String derive(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec((password == null ? "" : password).toCharArray(), salt, AppConfig.PBKDF2_ITERATIONS, 256);
            byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(out);
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    public record Hash(String saltHex, String hashHex) {}
}
