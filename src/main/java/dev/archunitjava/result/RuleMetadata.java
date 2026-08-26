package dev.archunitjava.result;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/** Immutable rule metadata carried losslessly into every result and renderer. */
public record RuleMetadata(
        String semanticIdentity,
        String presentationIdentity,
        String displayName,
        String description,
        Optional<String> rationale,
        List<String> tags,
        Severity severity)
        implements Comparable<RuleMetadata> {
    public RuleMetadata {
        semanticIdentity = requireText(semanticIdentity, "semanticIdentity");
        presentationIdentity = requirePresentationIdentity(presentationIdentity);
        displayName = requireText(displayName, "displayName");
        description = requireText(description, "description");
        Objects.requireNonNull(rationale, "rationale");
        rationale = rationale.map(value -> requireText(value, "rationale"));
        tags = sortedTags(tags);
        Objects.requireNonNull(severity, "severity");
        String expected = presentationIdentity(
                semanticIdentity, displayName, description, rationale, tags, severity);
        if (!presentationIdentity.equals(expected)) {
            throw new IllegalArgumentException("presentationIdentity does not match metadata");
        }
    }

    public static RuleMetadata of(String semanticIdentity, String description) {
        String identity = requireText(semanticIdentity, "semanticIdentity");
        String text = requireText(description, "description");
        return create(identity, text, text, Optional.empty(), List.of(), Severity.ERROR);
    }

    /** Presentation-only alias; the semantic identity remains unchanged. */
    public RuleMetadata as(String displayName) {
        return create(
                semanticIdentity, requireText(displayName, "displayName"), description,
                rationale, tags, severity);
    }

    public RuleMetadata because(String rationale) {
        return create(
                semanticIdentity, displayName, description,
                Optional.of(requireText(rationale, "rationale")), tags, severity);
    }

    public RuleMetadata tagged(String... values) {
        Objects.requireNonNull(values, "tags");
        return tagged(List.of(values.clone()));
    }

    public RuleMetadata tagged(Collection<String> values) {
        Objects.requireNonNull(values, "tags");
        TreeSet<String> combined = new TreeSet<>(tags);
        values.forEach(value -> combined.add(requireText(value, "tag")));
        return create(
                semanticIdentity, displayName, description, rationale,
                List.copyOf(combined), severity);
    }

    public RuleMetadata withSeverity(Severity value) {
        return create(
                semanticIdentity, displayName, description, rationale, tags,
                Objects.requireNonNull(value, "severity"));
    }

    /** Human-ready wording; escaping remains the renderer's responsibility. */
    public String humanDescription() {
        return rationale.map(value -> displayName + ", because " + value).orElse(displayName);
    }

    @Override
    public int compareTo(RuleMetadata other) {
        int result = semanticIdentity.compareTo(other.semanticIdentity);
        return result != 0 ? result : presentationIdentity.compareTo(other.presentationIdentity);
    }

    private static RuleMetadata create(
            String semanticIdentity,
            String displayName,
            String description,
            Optional<String> rationale,
            List<String> tags,
            Severity severity) {
        List<String> stableTags = sortedTags(tags);
        return new RuleMetadata(
                semanticIdentity,
                presentationIdentity(
                        semanticIdentity, displayName, description, rationale, stableTags, severity),
                displayName,
                description,
                rationale,
                stableTags,
                severity);
    }

    private static String presentationIdentity(
            String semanticIdentity,
            String displayName,
            String description,
            Optional<String> rationale,
            List<String> tags,
            Severity severity) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, semanticIdentity);
            update(digest, displayName);
            update(digest, description);
            update(digest, rationale.orElse(""));
            update(digest, severity.name());
            tags.forEach(tag -> update(digest, tag));
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JDK does not provide SHA-256", error);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(new byte[] {
                (byte) (bytes.length >>> 24),
                (byte) (bytes.length >>> 16),
                (byte) (bytes.length >>> 8),
                (byte) bytes.length
        });
        digest.update(bytes);
    }

    private static List<String> sortedTags(Collection<String> values) {
        Objects.requireNonNull(values, "tags");
        TreeSet<String> sorted = new TreeSet<>();
        values.forEach(value -> sorted.add(requireText(value, "tag")));
        return List.copyOf(sorted);
    }

    private static String requirePresentationIdentity(String value) {
        String identity = requireText(value, "presentationIdentity");
        if (identity.length() != 64 || !identity.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("presentationIdentity must be lowercase SHA-256 hex");
        }
        return identity;
    }

    private static String requireText(String value, String role) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(role + " must not be blank");
        }
        return value;
    }
}
