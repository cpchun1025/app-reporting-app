package com.tradingreporting.api.security;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

/**
 * Password hashing compatible with the Python backend's passlib {@code pbkdf2_sha256} scheme, so
 * password hashes are interchangeable between the two backends against the same database.
 *
 * <p>Hash format: {@code $pbkdf2-sha256$<rounds>$<ab64-salt>$<ab64-digest>}, where "ab64" is
 * passlib's adapted base64 alphabet: standard RFC 4648 base64 with {@code +} replaced by
 * {@code .} and no padding. This matches {@code passlib.utils.binary.ab64_encode/ab64_decode}.
 */
@Component
public class PasswordHasher {

    private static final String PREFIX = "$pbkdf2-sha256$";
    private static final int DEFAULT_ROUNDS = 29_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] digest = pbkdf2(password, salt, DEFAULT_ROUNDS);
        return PREFIX + DEFAULT_ROUNDS + "$" + ab64Encode(salt) + "$" + ab64Encode(digest);
    }

    public boolean verify(String password, String encodedHash) {
        if (encodedHash == null || !encodedHash.startsWith(PREFIX)) {
            return false;
        }
        String[] parts = encodedHash.substring(PREFIX.length()).split("\\$");
        if (parts.length != 3) {
            return false;
        }
        try {
            int rounds = Integer.parseInt(parts[0]);
            byte[] salt = ab64Decode(parts[1]);
            byte[] expected = ab64Decode(parts[2]);
            byte[] actual = pbkdf2(password, salt, rounds, expected.length * 8);
            return constantTimeEquals(expected, actual);
        } catch (RuntimeException error) {
            return false;
        }
    }

    private byte[] pbkdf2(String password, byte[] salt, int rounds) {
        return pbkdf2(password, salt, rounds, KEY_BITS);
    }

    private byte[] pbkdf2(String password, byte[] salt, int rounds, int keyBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, rounds, keyBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException error) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is unavailable.", error);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    private static String ab64Encode(byte[] data) {
        String standard = Base64.getEncoder().withoutPadding().encodeToString(data);
        return standard.replace('+', '.');
    }

    private static byte[] ab64Decode(String value) {
        String standard = value.replace('.', '+');
        int padding = (4 - (standard.length() % 4)) % 4;
        standard = standard + "=".repeat(padding);
        return Base64.getDecoder().decode(standard);
    }
}
