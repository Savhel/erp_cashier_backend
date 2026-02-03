package com.erp.cashier.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.regex.Pattern;
import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Service;

/**
 * Password verification service compatible with legacy plaintext and scrypt hashes.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Service
public class PasswordService {
    private static final Pattern HEX_PAIR = Pattern.compile("^[0-9a-fA-F]+$");
    private static final String SALT_DELIMITER = ":";
    private static final int SALT_PARTS = 2;
    private static final int SCRYPT_N = 16384;
    private static final int SCRYPT_R = 8;
    private static final int SCRYPT_P = 1;
    private static final int SALT_SIZE_BYTES = 16;

    /**
     * Verifies a raw password against the stored value.
     *
     * @param rawPassword raw password
     * @param stored stored password or scrypt hash
     * @return true if the password matches
     */
    public boolean verifyPassword(String rawPassword, String stored) {
        if (rawPassword == null || stored == null) {
            return false;
        }
        if (!stored.contains(SALT_DELIMITER)) {
            return stored.equals(rawPassword);
        }
        String[] parts = stored.split(SALT_DELIMITER, SALT_PARTS);
        if (parts.length != SALT_PARTS) {
            return false;
        }
        String saltHex = parts[0];
        String hashHex = parts[1];
        if (!isHex(saltHex) || !isHex(hashHex)) {
            return false;
        }
        byte[] salt = hexToBytes(saltHex);
        byte[] expected = hexToBytes(hashHex);
        byte[] derived = SCrypt.generate(
                rawPassword.getBytes(StandardCharsets.UTF_8),
                salt,
                SCRYPT_N,
                SCRYPT_R,
                SCRYPT_P,
                expected.length
        );
        return MessageDigest.isEqual(derived, expected);
    }

    /**
     * Hashes a password using scrypt with a random salt.
     *
     * @param rawPassword raw password
     * @return salt and hash encoded as hex with a delimiter
     * @throws IllegalArgumentException when the password is null
     */
    public String hashPassword(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        byte[] salt = new byte[SALT_SIZE_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] derived = SCrypt.generate(
                rawPassword.getBytes(StandardCharsets.UTF_8),
                salt,
                SCRYPT_N,
                SCRYPT_R,
                SCRYPT_P,
                64
        );
        return bytesToHex(salt) + SALT_DELIMITER + bytesToHex(derived);
    }

    private boolean isHex(String value) {
        return value != null && value.length() % 2 == 0 && HEX_PAIR.matcher(value).matches();
    }

    private byte[] hexToBytes(String value) {
        int length = value.length();
        byte[] result = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            int high = Character.digit(value.charAt(i), 16);
            int low = Character.digit(value.charAt(i + 1), 16);
            result[i / 2] = (byte) ((high << 4) + low);
        }
        return result;
    }

    private String bytesToHex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte b : value) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
