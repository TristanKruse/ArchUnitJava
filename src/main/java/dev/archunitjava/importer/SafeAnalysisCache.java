package dev.archunitjava.importer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Bounded, content-addressed analysis cache with a fixed binary envelope.
 *
 * <p>Payloads are opaque bytes. This class never uses Java serialization, reflection, class
 * loading, or type names from cache data. Every hit validates the key and payload digest before
 * bytes are returned. Invalid entries are replaced atomically while holding process and file
 * locks.
 */
public final class SafeAnalysisCache {
    private static final int MAGIC = 0x41554A43; // AUJC
    private static final int FORMAT_VERSION = 1;
    private static final int DIGEST_BYTES = 32;
    private static final int MAXIMUM_ENVELOPE_OVERHEAD = 2048;
    private static final Object[] JVM_LOCKS = locks();

    private final Path root;
    private final int maximumPayloadBytes;

    public SafeAnalysisCache(Path root, int maximumPayloadBytes) throws IOException {
        Objects.requireNonNull(root, "root");
        if (maximumPayloadBytes < 1) {
            throw new IllegalArgumentException("maximumPayloadBytes must be positive");
        }
        this.root = root.toAbsolutePath().normalize();
        if (Files.exists(this.root, LinkOption.NOFOLLOW_LINKS)
                && Files.isSymbolicLink(this.root)) {
            throw new IOException("Cache root must not be a symbolic link");
        }
        Files.createDirectories(this.root);
        if (!Files.isDirectory(this.root, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(this.root)) {
            throw new IOException("Cache root must be a non-symbolic directory");
        }
        this.maximumPayloadBytes = maximumPayloadBytes;
    }

    public Path root() {
        return root;
    }

    public Path entryPath(AnalysisCacheKey key) {
        Objects.requireNonNull(key, "key");
        Path entry = root.resolve(key.digest() + ".aujc").normalize();
        if (!root.equals(entry.getParent())) {
            throw new IllegalArgumentException("Cache entry escaped the cache root");
        }
        return entry;
    }

    public AnalysisCacheResult loadOrCompute(
            AnalysisCacheKey key, AnalysisPayloadSupplier supplier) throws IOException {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(supplier, "supplier");
        Path entry = entryPath(key);
        Object monitor = JVM_LOCKS[Math.floorMod(key.digest().hashCode(), JVM_LOCKS.length)];
        synchronized (monitor) {
            ensureSafeRoot();
            Path lockPath = root.resolve(key.digest() + ".lock").normalize();
            try (FileChannel lockChannel = FileChannel.open(
                            lockPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS);
                    var ignored = lockChannel.lock()) {
                ReadOutcome read = read(entry, key);
                if (read.kind == EntryKind.HIT) {
                    return new AnalysisCacheResult(read.payload, AnalysisCacheStatus.HIT);
                }
                byte[] computed = Objects.requireNonNull(supplier.get(), "supplier payload").clone();
                if (computed.length > maximumPayloadBytes) {
                    throw new IOException("Analysis payload exceeds the configured cache limit");
                }
                writeAtomically(entry, key, computed);
                return new AnalysisCacheResult(computed, replacementStatus(read.kind));
            }
        }
    }

    private ReadOutcome read(Path entry, AnalysisCacheKey key) {
        if (!Files.exists(entry, LinkOption.NOFOLLOW_LINKS)) return ReadOutcome.miss();
        if (Files.isSymbolicLink(entry) || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
            return ReadOutcome.invalid(EntryKind.CORRUPT);
        }
        try {
            long size = Files.size(entry);
            if (size > (long) maximumPayloadBytes + MAXIMUM_ENVELOPE_OVERHEAD) {
                return ReadOutcome.invalid(EntryKind.CORRUPT);
            }
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                    Files.newInputStream(entry, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)))) {
                if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) {
                    return ReadOutcome.invalid(EntryKind.FOREIGN);
                }
                if (input.readInt() != key.schemaVersion()) {
                    return ReadOutcome.invalid(EntryKind.FOREIGN);
                }
                if (!input.readUTF().equals(key.parserVersion())
                        || !input.readUTF().equals(key.libraryVersion())) {
                    return ReadOutcome.invalid(EntryKind.FOREIGN);
                }
                byte[] storedKey = input.readNBytes(DIGEST_BYTES);
                if (storedKey.length != DIGEST_BYTES) return ReadOutcome.invalid(EntryKind.PARTIAL);
                if (!MessageDigest.isEqual(storedKey, key.digestBytes())) {
                    return ReadOutcome.invalid(EntryKind.FOREIGN);
                }
                int payloadLength = input.readInt();
                if (payloadLength < 0 || payloadLength > maximumPayloadBytes) {
                    return ReadOutcome.invalid(EntryKind.CORRUPT);
                }
                byte[] payload = input.readNBytes(payloadLength);
                if (payload.length != payloadLength) return ReadOutcome.invalid(EntryKind.PARTIAL);
                byte[] storedDigest = input.readNBytes(DIGEST_BYTES);
                if (storedDigest.length != DIGEST_BYTES) return ReadOutcome.invalid(EntryKind.PARTIAL);
                if (input.read() != -1 || !MessageDigest.isEqual(storedDigest, sha256(payload))) {
                    return ReadOutcome.invalid(EntryKind.CORRUPT);
                }
                return new ReadOutcome(EntryKind.HIT, payload);
            }
        } catch (EOFException failure) {
            return ReadOutcome.invalid(EntryKind.PARTIAL);
        } catch (IOException | RuntimeException failure) {
            return ReadOutcome.invalid(EntryKind.CORRUPT);
        }
    }

    private void writeAtomically(Path entry, AnalysisCacheKey key, byte[] payload) throws IOException {
        Path temporary = Files.createTempFile(root, key.digest() + ".", ".tmp");
        if (!root.equals(temporary.getParent())) {
            throw new IOException("Temporary cache entry escaped the cache root");
        }
        try {
            try (FileChannel channel = FileChannel.open(
                            temporary,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            LinkOption.NOFOLLOW_LINKS);
                    DataOutputStream output = new DataOutputStream(
                            new BufferedOutputStream(Channels.newOutputStream(channel)))) {
                output.writeInt(MAGIC);
                output.writeInt(FORMAT_VERSION);
                output.writeInt(key.schemaVersion());
                output.writeUTF(key.parserVersion());
                output.writeUTF(key.libraryVersion());
                output.write(key.digestBytes());
                output.writeInt(payload.length);
                output.write(payload);
                output.write(sha256(payload));
                output.flush();
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        entry,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temporary, entry, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void ensureSafeRoot() throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IOException("Cache root is no longer a safe directory");
        }
    }

    private static AnalysisCacheStatus replacementStatus(EntryKind kind) {
        return switch (kind) {
            case MISS -> AnalysisCacheStatus.MISS_STORED;
            case CORRUPT -> AnalysisCacheStatus.CORRUPT_REPLACED;
            case FOREIGN -> AnalysisCacheStatus.FOREIGN_REPLACED;
            case PARTIAL -> AnalysisCacheStatus.PARTIAL_REPLACED;
            case HIT -> throw new IllegalArgumentException("A cache hit is not replaced");
        };
    }

    private static byte[] sha256(byte[] payload) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(payload);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }

    private static Object[] locks() {
        Object[] result = new Object[64];
        Arrays.setAll(result, ignored -> new Object());
        return result;
    }

    private enum EntryKind {
        HIT,
        MISS,
        CORRUPT,
        FOREIGN,
        PARTIAL
    }

    private record ReadOutcome(EntryKind kind, byte[] payload) {
        private static ReadOutcome miss() {
            return new ReadOutcome(EntryKind.MISS, new byte[0]);
        }

        private static ReadOutcome invalid(EntryKind kind) {
            return new ReadOutcome(kind, new byte[0]);
        }
    }
}
