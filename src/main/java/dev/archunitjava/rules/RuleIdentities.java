package dev.archunitjava.rules;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class RuleIdentities {
    private RuleIdentities() {}

    static String semantic(String family, String... parts) {
        return family + ":" + digest(parts);
    }

    static String violation(String semanticIdentity, String... parts) {
        return semanticIdentity + ":" + digest(parts);
    }

    private static String digest(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(new byte[] {
                        (byte) (bytes.length >>> 24),
                        (byte) (bytes.length >>> 16),
                        (byte) (bytes.length >>> 8),
                        (byte) bytes.length
                });
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JDK does not provide SHA-256", error);
        }
    }
}
