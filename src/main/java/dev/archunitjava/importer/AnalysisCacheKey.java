package dev.archunitjava.importer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Content-addressed cache identity covering inputs, runtime target, options, and versions. */
public record AnalysisCacheKey(
        String digest, int schemaVersion, String parserVersion, String libraryVersion) {
    public AnalysisCacheKey {
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("digest must be 64 lowercase hexadecimal characters");
        }
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        parserVersion = requireVersion(parserVersion, "parserVersion");
        libraryVersion = requireVersion(libraryVersion, "libraryVersion");
    }

    public static AnalysisCacheKey create(
            List<CacheInputFingerprint> inputs,
            int targetJavaRelease,
            String optionFingerprint,
            String parserVersion,
            String libraryVersion,
            int schemaVersion) {
        Objects.requireNonNull(inputs, "inputs");
        if (targetJavaRelease < 1) throw new IllegalArgumentException("targetJavaRelease must be positive");
        Objects.requireNonNull(optionFingerprint, "optionFingerprint");
        if (optionFingerprint.isBlank()) throw new IllegalArgumentException("optionFingerprint must not be blank");
        parserVersion = requireVersion(parserVersion, "parserVersion");
        libraryVersion = requireVersion(libraryVersion, "libraryVersion");
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(schemaVersion);
                writeText(output, parserVersion);
                writeText(output, libraryVersion);
                output.writeInt(targetJavaRelease);
                writeText(output, optionFingerprint);
                output.writeInt(inputs.size());
                for (CacheInputFingerprint input : inputs) {
                    CacheInputFingerprint value = Objects.requireNonNull(input, "input");
                    writeText(output, value.identity());
                    output.writeLong(value.byteCount());
                    output.write(HexFormat.of().parseHex(value.sha256()));
                }
            }
            return new AnalysisCacheKey(
                    sha256(bytes.toByteArray()), schemaVersion, parserVersion, libraryVersion);
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory cache-key encoding failed", impossible);
        }
    }

    public static AnalysisCacheKey fromResources(
            List<ClassFileResource> resources,
            ClassPathAssemblyOptions options,
            String parserVersion,
            String libraryVersion,
            int schemaVersion,
            int maximumResourceBytes)
            throws IOException {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(options, "options");
        if (maximumResourceBytes < 1) {
            throw new IllegalArgumentException("maximumResourceBytes must be positive");
        }
        List<CacheInputFingerprint> fingerprints = new ArrayList<>();
        for (ClassFileResource resource : resources.stream().sorted().toList()) {
            Objects.requireNonNull(resource, "resource");
            String identity = resource.precedence() + ":" + resource.name() + ":"
                    + resource.origin().kind() + ":" + resource.origin().input() + ":"
                    + resource.origin().entry();
            fingerprints.add(CacheInputFingerprint.of(
                    identity, resource.readBytes(maximumResourceBytes)));
        }
        String optionsFingerprint = "mode=" + options.mode()
                + ";manifest=" + options.followManifestClassPath()
                + ";manifest-entries=" + options.maximumManifestClassPathEntries()
                + ";manifest-depth=" + options.maximumManifestDepth()
                + ";manifest-bytes=" + options.maximumManifestBytes()
                + ";enumeration=" + options.enumerationOptions()
                + ";import=" + options.importOptions().fingerprintMaterial();
        return create(
                fingerprints,
                options.targetJavaRelease(),
                optionsFingerprint,
                parserVersion,
                libraryVersion,
                schemaVersion);
    }

    byte[] digestBytes() {
        return HexFormat.of().parseHex(digest);
    }

    private static String requireVersion(String value, String role) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(role + " must not be blank");
        if (value.getBytes(StandardCharsets.UTF_8).length > 256) {
            throw new IllegalArgumentException(role + " must not exceed 256 UTF-8 bytes");
        }
        return value;
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }

}
