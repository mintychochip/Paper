package dev.mintychochip.provenance;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the server-root provenance directory and migrates the legacy
 * primary-world directory without merging two stores.
 */
public final class ProvenanceStorageLayout {

    private static final String MIGRATION_MARKER = "migration-complete.json";
    private static final String STAGING_MARKER = "migration-staging.json";
    private static final String LOCK_FILE = "writer.lock";
    private static final String[] STORE_FILES = {
        "provenance.db",
        "provenance.db-wal",
        "provenance.db-shm",
        "provenance-spill.log",
        "provenance-spill.log.replay",
        "provenance-audit.jsonl",
        "provenance-audit.jsonl.1",
        "provenance-audit.jsonl.2",
        "provenance-audit.jsonl.3"
    };
    private static final Gson GSON = new Gson();

    private final Path worldContainer;
    private final Path root;
    private final @Nullable Path legacyRoot;
    private @Nullable FileChannel lockChannel;
    private @Nullable FileLock lock;
    private boolean writerLockTransferred;
    private boolean stagedMigration;
    private boolean destinationCreated;

    private ProvenanceStorageLayout(final Path worldContainer, final @Nullable Path primaryWorld) {
        this.worldContainer = Objects.requireNonNull(worldContainer, "worldContainer");
        this.root = worldContainer.resolve("mintychochip");
        this.legacyRoot = primaryWorld == null ? null : primaryWorld.resolve("mintychochip");
    }

    public static ProvenanceStorageLayout resolve(final Path worldContainer, final @Nullable Path primaryWorld) {
        return new ProvenanceStorageLayout(worldContainer, primaryWorld);
    }

    public Path root() {
        return this.root;
    }

    public @Nullable Path legacyRoot() {
        return this.legacyRoot;
    }

    /**
     * Acquires the process-wide migration lock and makes the stable destination
     * ready for the caller to open and recover.
     */
    public synchronized Path migrate() throws IOException {
        this.acquireLock();
        try {
            if (hasSymlinkedComponent(this.root)) {
                throw new IOException("migration-conflict: provenance destination path is symbolic");
            }
            if (Files.isSymbolicLink(this.root.resolve(MIGRATION_MARKER))
                || Files.isSymbolicLink(this.root.resolve(STAGING_MARKER))) {
                throw new IOException("migration-conflict: provenance marker is symbolic");
            }
            if (hasSymlinkedStoreFiles(this.root)) {
                throw new IOException("migration-conflict: provenance store file is symbolic");
            }
            if (isRegularFileNoFollow(this.root.resolve(MIGRATION_MARKER))) {
                if (!this.hasValidCompletionMarker()) {
                    throw new IOException("migration-conflict: completion marker does not match destination");
                }
                return this.root;
            }
            if (isRegularFileNoFollow(this.root.resolve(STAGING_MARKER))) {
                if (!this.hasValidStagingMarker()) {
                    throw new IOException("migration-conflict: staging marker does not match destination");
                }
                this.resumeStaging();
                this.stagedMigration = true;
                this.destinationCreated = true;
                return this.root;
            }

            final Path legacy = this.legacyRoot;
            if (legacy != null && hasSymlinkedComponent(legacy)) {
                throw new IOException("migration-conflict: legacy provenance path is symbolic");
            }
            if (legacy != null && hasSymlinkedStoreFiles(legacy)) {
                throw new IOException("migration-conflict: legacy provenance store file is symbolic");
            }
            if (legacy == null || samePath(legacy, this.root) || !Files.isDirectory(legacy) || !hasStoreFiles(legacy)) {
                if (!Files.exists(this.root)) {
                    Files.createDirectories(this.root);
                    this.destinationCreated = true;
                    force(this.root);
                } else if (!Files.isDirectory(this.root)) {
                    throw new IOException("migration-conflict: provenance destination is not a directory");
                }
                return this.root;
            }

            if (Files.exists(this.root)) {
                if (!Files.isDirectory(this.root)) {
                    throw new IOException("migration-conflict: provenance destination is not a directory");
                }
                if (hasStoreFiles(this.root)) {
                    if (!sameStore(this.root, legacy)) {
                        throw new IOException("migration-conflict: destination and legacy provenance stores differ");
                    }
                    throw new IOException("migration-conflict: matching destination and legacy stores have no completion marker");
                }
                try (DirectoryStream<Path> entries = Files.newDirectoryStream(this.root)) {
                    if (entries.iterator().hasNext()) {
                        throw new IOException("migration-conflict: destination is not empty");
                    }
                }
                Files.delete(this.root);
            }

            Files.createDirectories(this.worldContainer);
            final Path staging = Files.createTempDirectory(this.worldContainer, "mintychochip.staging-");
            boolean moved = false;
            try {
                final Map<String, String> checksums = new LinkedHashMap<>();
                for (final String file : STORE_FILES) {
                    final Path source = legacy.resolve(file);
                    if (Files.isSymbolicLink(source)) {
                        throw new IOException("migration-conflict: legacy file is symbolic " + file);
                    }
                    if (!Files.isRegularFile(source)) {
                        continue;
                    }
                    final Path destination = staging.resolve(file);
                    Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
                    force(destination);
                    final String sourceChecksum = sha256(source);
                    if (!sourceChecksum.equals(sha256(destination))) {
                        throw new IOException("migration checksum mismatch for " + file);
                    }
                    checksums.put(file, sourceChecksum);
                }
                writeStagingMarker(staging, legacy, checksums);
                force(staging);
                moveStagingIntoPlace(staging);
                moved = true;
                this.stagedMigration = true;
                this.destinationCreated = true;
                force(this.worldContainer);
                return this.root;
            } finally {
                if (!moved && !isRegularFileNoFollow(this.root.resolve(STAGING_MARKER))) {
                    deleteTree(staging);
                }
            }
        } catch (final IOException | RuntimeException ex) {
            try {
                this.cleanupCreatedDestination();
            } catch (final IOException cleanupFailure) {
                ex.addSuppressed(cleanupFailure);
            }
            try {
                this.releaseLock();
            } catch (final IOException releaseFailure) {
                ex.addSuppressed(releaseFailure);
            }
            throw ex;
        }
    }

    /**
     * Commits the migration marker and retains the old directory as a recovery
     * backup. The caller must invoke this only after opening and recovering the
     * destination store successfully.
     */
    public synchronized void markMigrationComplete() throws IOException {
        this.markMigrationComplete(false);
    }

    synchronized void markMigrationCompleteForWriter() throws IOException {
        this.markMigrationComplete(true);
    }

    private void markMigrationComplete(final boolean retainLock) throws IOException {
        this.requireLock();
        try {
            if (this.finishExistingMigration()) {
                this.destinationCreated = false;
                if (!retainLock) {
                    this.releaseLock();
                }
                return;
            }
            if (hasSymlinkedComponent(this.root)) {
                throw new IOException("migration-conflict: provenance destination path is symbolic");
            }
            Files.createDirectories(this.root);
            if (hasSymlinkedStoreFiles(this.root)) {
                throw new IOException("migration-conflict: provenance store file is symbolic");
            }

            final Path legacy = this.legacyRoot;
            if (legacy != null && hasSymlinkedComponent(legacy)) {
                throw new IOException("migration-conflict: legacy provenance path is symbolic");
            }
            if (legacy != null && hasSymlinkedStoreFiles(legacy)) {
                throw new IOException("migration-conflict: legacy provenance store file is symbolic");
            }
            final Path backup = legacy != null
                && !samePath(legacy, this.root)
                && Files.isDirectory(legacy)
                && hasStoreFiles(legacy)
                ? nextLegacyBackup(legacy)
                : null;
            if (backup != null && !isSafeLegacyBackup(backup, legacy)) {
                throw new IOException("migration-conflict: recovery backup is outside legacy parent");
            }

            final JsonObject markerData = new JsonObject();
            markerData.addProperty("status", "complete");
            markerData.addProperty("source", legacy == null ? "" : legacy.toString());
            markerData.addProperty("destination", this.root.toString());
            markerData.addProperty("completedAt", System.currentTimeMillis());
            markerData.addProperty("backup", backup == null ? "" : backup.toString());
            final Map<String, String> completionChecksums = this.completionChecksums();
            final JsonObject files = new JsonObject();
            for (final Map.Entry<String, String> entry : completionChecksums.entrySet()) {
                files.addProperty(entry.getKey(), entry.getValue());
            }
            markerData.add("files", files);

            final Path marker = this.root.resolve(MIGRATION_MARKER);
            final Path temporary = this.root.resolve(MIGRATION_MARKER + ".tmp");
            if (Files.isSymbolicLink(temporary)) {
                throw new IOException("migration-conflict: completion marker temporary file is symbolic");
            }
            Files.writeString(
                temporary,
                GSON.toJson(markerData),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS
            );
            force(temporary);
            moveIntoPlace(temporary, marker);
            force(this.root);

            if (backup != null) {
                moveIntoPlace(legacy, backup);
                force(backup.getParent());
            }
            Files.deleteIfExists(this.root.resolve(STAGING_MARKER));
            force(this.root);
            this.destinationCreated = false;
            this.stagedMigration = false;
            if (!retainLock) {
                this.releaseLock();
            }
        } catch (final IOException | RuntimeException ex) {
            if (!retainLock) {
                try {
                    this.releaseLock();
                } catch (final IOException releaseFailure) {
                    ex.addSuppressed(releaseFailure);
                }
            }
            throw ex;
        }
    }
    private boolean finishExistingMigration() throws IOException {
        final Path marker = this.root.resolve(MIGRATION_MARKER);
        if (!isRegularFileNoFollow(marker)) {
            return false;
        }
        if (!this.hasValidCompletionMarker()) {
            throw new IOException("migration-conflict: completion marker does not match destination");
        }
        final JsonObject markerData = JsonParser.parseString(Files.readString(marker)).getAsJsonObject();
        final Path legacy = this.legacyRoot;
        final String backupValue = markerData.get("backup").getAsString();
        if (legacy != null
            && !hasSymlinkedComponent(legacy)
            && !samePath(legacy, this.root)
            && !Files.isSymbolicLink(legacy)
            && Files.isDirectory(legacy)
            && !hasSymlinkedStoreFiles(legacy)
            && hasStoreFiles(legacy)) {
            if (backupValue.isBlank()) {
                throw new IOException("migration-conflict: completed marker has no recovery backup");
            }
            final Path backup = Path.of(backupValue);
            if (!isSafeLegacyBackup(backup, legacy)) {
                throw new IOException("migration-conflict: recovery backup is outside legacy parent");
            }
            if (Files.exists(backup)) {
                throw new IOException("migration-conflict: recovery backup already exists");
            }
            moveIntoPlace(legacy, backup);
            force(backup.getParent());
        }
        Files.deleteIfExists(this.root.resolve(STAGING_MARKER));
        force(this.root);
        return true;
    }

    /**
     * Aborts an incomplete migration, removing only a destination created by
     * this instance and always leaving the legacy source available for retry.
     */
    public synchronized void abortMigration() throws IOException {
        IOException failure = null;
        if (!this.writerLockTransferred) {
            try {
                this.cleanupCreatedDestination();
            } catch (final IOException ex) {
                failure = ex;
            }
        }
        if (!this.writerLockTransferred) {
            try {
                this.releaseLock();
            } catch (final IOException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void requireLock() throws IOException {
        if (this.lock == null || !this.lock.isValid()) {
            throw new IOException("provenance migration lock is not held");
        }
    }

    private void acquireLock() throws IOException {
        if (this.lock != null && this.lock.isValid()) {
            return;
        }
        if (hasSymlinkedComponent(this.worldContainer)) {
            throw new IOException("migration-conflict: world container path is symbolic");
        }
        Files.createDirectories(this.worldContainer);
        final Path lockPath = this.worldContainer.resolve(LOCK_FILE);
        if (Files.isSymbolicLink(lockPath)) {
            throw new IOException("migration-conflict: writer.lock is symbolic");
        }
        final FileChannel channel = FileChannel.open(
            lockPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS
        );
        try {
            final FileLock acquired = channel.tryLock();
            if (acquired == null) {
                throw new IOException("writer.lock is already held");
            }
            this.lockChannel = channel;
            this.lock = acquired;
        } catch (final OverlappingFileLockException ex) {
            channel.close();
            throw new IOException("writer.lock is already held", ex);
        } catch (final IOException | RuntimeException ex) {
            channel.close();
            throw ex;
        }
    }

    private void releaseLock() throws IOException {
        IOException failure = null;
        final FileLock currentLock = this.lock;
        this.lock = null;
        if (currentLock != null) {
            try {
                currentLock.release();
            } catch (final IOException ex) {
                failure = ex;
            }
        }
        final FileChannel currentChannel = this.lockChannel;
        this.lockChannel = null;
        if (currentChannel != null) {
            try {
                currentChannel.close();
            } catch (final IOException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    synchronized AutoCloseable retainMigrationLock() throws IOException {
        this.requireLock();
        this.writerLockTransferred = true;
        return this::releaseMigrationLock;
    }

    synchronized void releaseMigrationLock() throws IOException {
        try {
            this.releaseLock();
        } finally {
            this.writerLockTransferred = false;
        }
    }

    private void cleanupCreatedDestination() throws IOException {
        if (!this.destinationCreated
            || isRegularFileNoFollow(this.root.resolve(MIGRATION_MARKER))
            || isRegularFileNoFollow(this.root.resolve(STAGING_MARKER))) {
            return;
        }
        deleteTree(this.root);
        this.destinationCreated = false;
    }
    private void writeStagingMarker(
        final Path staging,
        final Path source,
        final Map<String, String> checksums
    ) throws IOException {
        final JsonObject markerData = new JsonObject();
        markerData.addProperty("status", "staging");
        markerData.addProperty("source", source.toString());
        markerData.addProperty("destination", this.root.toString());
        markerData.addProperty("staging", staging.toString());
        markerData.addProperty("createdAt", System.currentTimeMillis());
        final JsonObject files = new JsonObject();
        for (final Map.Entry<String, String> entry : checksums.entrySet()) {
            files.addProperty(entry.getKey(), entry.getValue());
        }
        markerData.add("files", files);
        final Path marker = staging.resolve(STAGING_MARKER);
        if (Files.isSymbolicLink(marker)) {
            throw new IOException("migration-conflict: staging marker is symbolic");
        }
        Files.writeString(
            marker,
            GSON.toJson(markerData),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS
        );
        force(marker);
    }

    private boolean hasValidStagingMarker() throws IOException {
        if (!Files.isDirectory(this.root)) {
            return false;
        }
        try {
            final JsonObject marker = JsonParser.parseString(Files.readString(this.root.resolve(STAGING_MARKER))).getAsJsonObject();
            if (!"staging".equals(marker.get("status").getAsString())) {
                return false;
            }
            if (!this.root.toString().equals(marker.get("destination").getAsString())) {
                return false;
            }
            if (!marker.has("source") || marker.get("source").getAsString().isBlank()) {
                return false;
            }
            final Path source = Path.of(marker.get("source").getAsString());
            if (!this.isAllowedLegacyRoot(source)) {
                return false;
            }
            if (marker.get("createdAt").getAsLong() <= 0L) {
                return false;
            }
            if (!marker.has("staging")) {
                return false;
            }
            final Path staging = Path.of(marker.get("staging").getAsString());
            if (Files.isSymbolicLink(staging)
                || !this.isSafeStagingPath(staging)
                || (Files.exists(staging) && !Files.isDirectory(staging))) {
                return false;
            }
            final JsonObject files = marker.getAsJsonObject("files");
            if (files == null) {
                return false;
            }
            if (!files.has("provenance.db")) {
                return false;
            }
            for (final String file : STORE_FILES) {
                if (files.has(file) && !files.get(file).getAsString().matches("[0-9a-f]{64}")) {
                    return false;
                }
            }
            return true;
        } catch (final IOException | RuntimeException ex) {
            return false;
        }
    }

    private boolean isSafeStagingPath(final Path staging) {
        if (hasSymlinkedComponent(staging)) {
            return false;
        }
        final Path normalized = staging.toAbsolutePath().normalize();
        final Path container = this.worldContainer.toAbsolutePath().normalize();
        final Path fileName = normalized.getFileName();
        return normalized.getParent() != null
            && normalized.getParent().equals(container)
            && fileName != null
            && fileName.toString().startsWith("mintychochip.staging-");
    }
    private void resumeStaging() throws IOException {
        final JsonObject marker = JsonParser.parseString(Files.readString(this.root.resolve(STAGING_MARKER))).getAsJsonObject();
        final Path staging = Path.of(marker.get("staging").getAsString());
        final Path sourceRoot = Path.of(marker.get("source").getAsString());
        if (!this.isAllowedLegacyRoot(sourceRoot)) {
            throw new IOException("migration-conflict: staging source is not the configured legacy root");
        }
        final JsonObject files = marker.getAsJsonObject("files");
        for (final String file : STORE_FILES) {
            if (!files.has(file)) {
                continue;
            }
            final String expected = files.get(file).getAsString();
            final Path destination = this.root.resolve(file);
            if (Files.isSymbolicLink(destination)) {
                throw new IOException("migration-conflict: destination file is symbolic " + file);
            }
            if (Files.exists(destination)) {
                if (!Files.isRegularFile(destination)) {
                    throw new IOException("migration-conflict: destination file is not regular " + file);
                }
                if (!expected.equals(sha256(destination))) {
                    throw new IOException("migration-conflict: destination file changed during staging " + file);
                }
                continue;
            }
            final Path staged = staging.resolve(file);
            if (Files.isSymbolicLink(staged)) {
                throw new IOException("migration-conflict: staging file is symbolic " + file);
            }
            if (Files.isRegularFile(staged)) {
                moveIntoPlace(staged, destination);
                force(destination);
                if (!expected.equals(sha256(destination))) {
                    throw new IOException("migration checksum mismatch while resuming " + file);
                }
                continue;
            }
            if (!Files.isDirectory(staging)) {
                throw new IOException("migration-conflict: staging copy is unavailable " + file);
            }
            final Path legacy = sourceRoot.resolve(file);
            if (Files.isSymbolicLink(legacy)) {
                throw new IOException("migration-conflict: legacy file is symbolic " + file);
            }
            if (Files.isRegularFile(legacy)) {
                Files.copy(legacy, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                force(destination);
                if (!expected.equals(sha256(destination))) {
                    throw new IOException("migration checksum mismatch while resuming " + file);
                }
                continue;
            }
            throw new IOException("migration-conflict: incomplete staging file " + file);
        }
        if (Files.isDirectory(staging) && !staging.equals(this.root)) {
            deleteTree(staging);
        }
        force(this.root);
    }

    private boolean hasValidCompletionMarker() throws IOException {
        if (!Files.isDirectory(this.root)) {
            return false;
        }
        try {
            final JsonObject marker = JsonParser.parseString(Files.readString(this.root.resolve(MIGRATION_MARKER))).getAsJsonObject();
            if (!"complete".equals(marker.get("status").getAsString())) {
                return false;
            }
            if (!this.root.toString().equals(marker.get("destination").getAsString())) {
                return false;
            }
            if (!marker.has("source") || !marker.has("backup") || !marker.has("completedAt")) {
                return false;
            }
            if (marker.get("completedAt").getAsLong() <= 0L) {
                return false;
            }
            final JsonObject files = marker.getAsJsonObject("files");
            if (files == null) {
                return false;
            }
            if (!files.has("provenance.db")) {
                return false;
            }
            // The checksum set records the verified migration copy. Store files
            // are mutable after completion, so their current content is checked
            // by ProvenanceRepository during startup rather than pinned here.
            for (final String file : STORE_FILES) {
                if (!files.has(file)) {
                    continue;
                }
                final String checksum = files.get(file).getAsString();
                if (!checksum.matches("[0-9a-f]{64}")) {
                    return false;
                }
                if ("provenance.db".equals(file) && !isRegularFileNoFollow(this.root.resolve(file))) {
                    return false;
                }
            }
            return true;
        } catch (final IOException | RuntimeException ex) {
            return false;
        }
    }

    private static boolean hasSymlinkedComponent(final Path path) {
        final Path absolute = path.toAbsolutePath();
        Path current = absolute.getRoot();
        if (current == null) {
            return false;
        }
        for (final Path component : absolute) {
            if ("..".equals(component.toString())) {
                return true;
            }
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRegularFileNoFollow(final Path path) {
        return !Files.isSymbolicLink(path) && Files.isRegularFile(path);
    }

    private static boolean samePath(final Path first, final Path second) {
        if (first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize())) {
            return true;
        }
        try {
            return Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second);
        } catch (final IOException ex) {
            return false;
        }
    }

    private boolean isAllowedLegacyRoot(final Path source) {
        final Path expected = this.legacyRoot;
        return expected != null
            && !hasSymlinkedComponent(source)
            && !hasSymlinkedComponent(expected)
            && samePath(source, expected);
    }

    private static boolean isSafeLegacyBackup(final Path backup, final Path legacy) {
        final Path parent = legacy.getParent();
        final Path backupName = backup.getFileName();
        final Path legacyName = legacy.getFileName();
        return parent != null
            && !hasSymlinkedComponent(legacy)
            && !hasSymlinkedComponent(backup)
            && backupName != null
            && legacyName != null
            && backup.toAbsolutePath().normalize().getParent().equals(parent.toAbsolutePath().normalize())
            && backupName.toString().startsWith(legacyName + ".legacy-");
    }

    private static Map<String, String> checksumSet(final Path directory) throws IOException {
        final Map<String, String> checksums = new LinkedHashMap<>();
        for (final String file : STORE_FILES) {
            final Path path = directory.resolve(file);
            if (Files.isRegularFile(path)) {
                checksums.put(file, sha256(path));
            }
        }
        return checksums;
    }

    private Map<String, String> completionChecksums() throws IOException {
        final Path stagingMarker = this.root.resolve(STAGING_MARKER);
        if (!isRegularFileNoFollow(stagingMarker)) {
            if (this.stagedMigration) {
                throw new IOException("migration-conflict: staging marker is missing");
            }
            return checksumSet(this.root);
        }
        final JsonObject marker = JsonParser.parseString(Files.readString(stagingMarker)).getAsJsonObject();
        final JsonObject files = marker.getAsJsonObject("files");
        if (files == null) {
            throw new IOException("migration-conflict: staging marker has no checksum set");
        }
        final Map<String, String> checksums = new LinkedHashMap<>();
        for (final String file : STORE_FILES) {
            if (!files.has(file)) {
                continue;
            }
            final String checksum = files.get(file).getAsString();
            if (!checksum.matches("[0-9a-f]{64}")) {
                throw new IOException("migration-conflict: staging marker checksum is invalid");
            }
            checksums.put(file, checksum);
        }
        if (this.stagedMigration && !checksums.containsKey("provenance.db")) {
            throw new IOException("migration-conflict: staging marker has no database checksum");
        }
        return checksums;
    }

    private static boolean hasStoreFiles(final Path directory) {
        for (final String file : STORE_FILES) {
            if (Files.isRegularFile(directory.resolve(file))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSymlinkedStoreFiles(final Path directory) {
        for (final String file : STORE_FILES) {
            if (Files.isSymbolicLink(directory.resolve(file))) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameStore(final Path destination, final Path source) throws IOException {
        for (final String file : STORE_FILES) {
            final Path destinationFile = destination.resolve(file);
            final Path sourceFile = source.resolve(file);
            if (Files.isSymbolicLink(destinationFile) || Files.isSymbolicLink(sourceFile)) {
                return false;
            }
            final boolean destinationExists = Files.isRegularFile(destinationFile);
            final boolean sourceExists = Files.isRegularFile(sourceFile);
            if (destinationExists != sourceExists) {
                return false;
            }
            if (destinationExists && !sha256(destinationFile).equals(sha256(sourceFile))) {
                return false;
            }
        }
        return true;
    }

    private static Path nextLegacyBackup(final Path legacy) {
        final Path parent = legacy.getParent();
        final String prefix = legacy.getFileName() + ".legacy-" + System.currentTimeMillis();
        Path candidate = parent.resolve(prefix);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = parent.resolve(prefix + "-" + suffix++);
        }
        return candidate;
    }

    private static String sha256(final Path path) throws IOException {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                final byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (final NoSuchAlgorithmException ex) {
            throw new AssertionError("SHA-256 is required", ex);
        }
    }

    private static void force(final Path path) throws IOException {
        final StandardOpenOption option = Files.isDirectory(path) ? StandardOpenOption.READ : StandardOpenOption.WRITE;
        try (FileChannel channel = FileChannel.open(path, option)) {
            channel.force(true);
        }
    }

    private void moveStagingIntoPlace(final Path staging) throws IOException {
        try {
            Files.move(staging, this.root, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return;
        } catch (final AtomicMoveNotSupportedException ignored) {
            // Fall back to individually verified moves while retaining the
            // staging marker in the destination for crash-time resumption.
        }
        Files.createDirectories(this.root);
        final Path stagingMarker = staging.resolve(STAGING_MARKER);
        final Path destinationMarker = this.root.resolve(STAGING_MARKER);
        Files.copy(stagingMarker, destinationMarker, StandardCopyOption.REPLACE_EXISTING);
        force(destinationMarker);
        force(this.root);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(staging)) {
            for (final Path entry : entries) {
                if (entry.getFileName().toString().equals(STAGING_MARKER)) {
                    continue;
                }
                final Path destination = this.root.resolve(entry.getFileName().toString());
                moveIntoPlace(entry, destination);
                force(destination);
            }
        }
        deleteTree(staging);
        force(this.root);
    }

    private static void moveIntoPlace(final Path source, final Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (final AtomicMoveNotSupportedException ex) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(final Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            final var entries = paths.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList();
            IOException failure = null;
            for (final Path path : entries) {
                try {
                    Files.deleteIfExists(path);
                } catch (final IOException ex) {
                    if (failure == null) {
                        failure = ex;
                    } else {
                        failure.addSuppressed(ex);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
