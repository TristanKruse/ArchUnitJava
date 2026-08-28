package dev.archunitjava.baseline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class FingerprintHash {
    private final MessageDigest digest;

    FingerprintHash() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JDK does not provide SHA-256", error);
        }
    }

    FingerprintHash add(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(new byte[] {
                (byte) (bytes.length >>> 24), (byte) (bytes.length >>> 16),
                (byte) (bytes.length >>> 8), (byte) bytes.length
        });
        digest.update(bytes);
        return this;
    }

    String finish() {
        return HexFormat.of().formatHex(digest.digest());
    }

    static String require(String value, String role) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(role + " must be lowercase SHA-256 hex");
        }
        return value;
    }
}
