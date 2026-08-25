package dev.archunitjava.importer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable input identity and byte-content digest; timestamps are intentionally absent. */
public record CacheInputFingerprint(String identity, long byteCount, String sha256) {
    public CacheInputFingerprint {
        if (identity == null || identity.isBlank()) {
            throw new IllegalArgumentException("identity must not be blank");
        }
        if (byteCount < 0) throw new IllegalArgumentException("byteCount must not be negative");
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hexadecimal characters");
        }
    }

    public static CacheInputFingerprint of(String identity, byte[] bytes) {
        if (bytes == null) throw new NullPointerException("bytes");
        try {
            return new CacheInputFingerprint(
                    identity,
                    bytes.length,
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }
}
