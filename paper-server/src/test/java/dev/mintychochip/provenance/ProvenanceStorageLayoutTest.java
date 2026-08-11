package dev.mintychochip.provenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stable server-root storage layout and verified legacy migration.
 */
@Normal
public final class ProvenanceStorageLayoutTest {

    @TempDir
    Path tempDir;

    @Test
    public void migratesLegacyFilesWithChecksumsAndBackup() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path legacy = primaryWorld.resolve("mintychochip");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("provenance.db"), "db");
        Files.writeString(legacy.resolve("provenance.db-wal"), "wal");
        Files.writeString(legacy.resolve("provenance-spill.log"), "spill\n");
        Files.writeString(legacy.resolve("provenance-audit.jsonl"), "audit\n");

        final ProvenanceStorageLayout layout = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        final Path root = layout.migrate();

        assertEquals(worldContainer.resolve("mintychochip"), root);
        assertEquals("db", Files.readString(root.resolve("provenance.db")));
        assertEquals("wal", Files.readString(root.resolve("provenance.db-wal")));
        assertFalse(Files.exists(root.resolve("migration-complete.json")));

        layout.markMigrationComplete();
        assertTrue(Files.exists(root.resolve("migration-complete.json")));
        assertTrue(Files.find(
            primaryWorld,
            1,
            (path, attributes) -> path.getFileName().toString().startsWith("mintychochip.legacy-")
        ).findAny().isPresent());
    }

    @Test
    public void completedDestinationWinsOverRetainedLegacyBackup() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path root = worldContainer.resolve("mintychochip");
        Files.createDirectories(root);
        Files.writeString(root.resolve("provenance.db"), "new");
        final ProvenanceStorageLayout completedLayout = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        completedLayout.migrate();
        completedLayout.markMigrationComplete();
        Files.createDirectories(primaryWorld.resolve("mintychochip.legacy-1"));
        Files.writeString(primaryWorld.resolve("mintychochip.legacy-1/provenance.db"), "old");

        assertEquals(root, ProvenanceStorageLayout.resolve(worldContainer, primaryWorld).migrate());
        assertEquals("new", Files.readString(root.resolve("provenance.db")));
    }

    @Test
    public void corruptCompletedMarkerIsConflict() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path root = worldContainer.resolve("mintychochip");
        Files.createDirectories(root);
        Files.writeString(root.resolve("provenance.db"), "original");

        final ProvenanceStorageLayout layout = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        layout.migrate();
        layout.markMigrationComplete();
        final Path marker = root.resolve("migration-complete.json");
        Files.writeString(marker, Files.readString(marker).replaceFirst("[0-9a-f]{64}", "broken"));

        final IOException failure = assertThrows(
            IOException.class,
            () -> ProvenanceStorageLayout.resolve(worldContainer, primaryWorld).migrate()
        );
        assertTrue(failure.getMessage().contains("migration-conflict"));
    }

    @Test
    public void completedDestinationAllowsMutableStoreUpdates() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path root = worldContainer.resolve("mintychochip");
        Files.createDirectories(root);
        Files.writeString(root.resolve("provenance.db"), "original");

        final ProvenanceStorageLayout layout = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        layout.migrate();
        layout.markMigrationComplete();
        Files.writeString(root.resolve("provenance.db"), "updated");

        assertEquals(root, ProvenanceStorageLayout.resolve(worldContainer, primaryWorld).migrate());
    }

    @Test
    public void resumesStagingDestinationWithoutMergingStores() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path legacy = primaryWorld.resolve("mintychochip");
        final Path root = worldContainer.resolve("mintychochip");
        final Path staging = worldContainer.resolve("mintychochip.staging-crash");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("provenance.db"), "old");
        Files.createDirectories(root);
        Files.createDirectories(staging);
        Files.writeString(staging.resolve("provenance.db"), "old");
        Files.writeString(
            root.resolve("migration-staging.json"),
            "{\"status\":\"staging\",\"source\":\"" + legacy + "\",\"destination\":\"" + root
                + "\",\"staging\":\"" + staging + "\",\"createdAt\":1,\"files\":{\"provenance.db\":\"cba06b5736faf67e54b07b561eae94395e774c517a7d910a54369e1263ccfbd4\"}}"
        );

        final ProvenanceStorageLayout layout = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        assertEquals(root, layout.migrate());
        assertEquals("old", Files.readString(root.resolve("provenance.db")));
        assertTrue(Files.exists(legacy.resolve("provenance.db")));
        layout.abortMigration();
        assertTrue(Files.exists(root.resolve("migration-staging.json")));
    }

    @Test
    public void refusesLegacyOverwriteAfterAtomicStagingMutation() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path legacy = primaryWorld.resolve("mintychochip");
        final Path root = worldContainer.resolve("mintychochip");
        final Path staging = worldContainer.resolve("mintychochip.staging-crash");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("provenance.db"), "old");
        Files.createDirectories(root);
        Files.writeString(root.resolve("provenance.db"), "runtime-update");
        Files.writeString(
            root.resolve("migration-staging.json"),
            "{\"status\":\"staging\",\"source\":\"" + legacy + "\",\"destination\":\"" + root
                + "\",\"staging\":\"" + staging + "\",\"createdAt\":1,\"files\":{\"provenance.db\":\"cba06b5736faf67e54b07b561eae94395e774c517a7d910a54369e1263ccfbd4\"}}"
        );

        final IOException failure = assertThrows(
            IOException.class,
            () -> ProvenanceStorageLayout.resolve(worldContainer, primaryWorld).migrate()
        );
        assertTrue(failure.getMessage().contains("migration-conflict"));
        assertEquals("runtime-update", Files.readString(root.resolve("provenance.db")));
        assertEquals("old", Files.readString(legacy.resolve("provenance.db")));
    }

    @Test
    public void unresolvedTwoStoreConflictDoesNotMerge() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path root = worldContainer.resolve("mintychochip");
        Files.createDirectories(root);
        Files.createDirectories(primaryWorld.resolve("mintychochip"));
        Files.writeString(root.resolve("provenance.db"), "new");
        Files.writeString(primaryWorld.resolve("mintychochip/provenance.db"), "old");

        assertThrows(
            IOException.class,
            () -> ProvenanceStorageLayout.resolve(worldContainer, primaryWorld).migrate()
        );
        assertEquals("new", Files.readString(root.resolve("provenance.db")));
        assertEquals("old", Files.readString(primaryWorld.resolve("mintychochip/provenance.db")));
    }
    @Test
    public void writerReleasesRetainedMigrationLockOnShutdown() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path root = worldContainer.resolve("mintychochip");
        Files.createDirectories(root);

        final ProvenanceStorageLayout layout = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        layout.migrate();
        boolean installed = false;
        try {
            ProvenanceWriter.installLocked(root, message -> {
            }, layout.retainMigrationLock());
            installed = true;
            layout.markMigrationCompleteForWriter();
            assertThrows(
                IOException.class,
                () -> ProvenanceStorageLayout.resolve(worldContainer, primaryWorld).migrate()
            );
        } finally {
            if (installed) {
                ProvenanceWriter.clearInstall();
            } else {
                layout.abortMigration();
            }
        }

        final ProvenanceStorageLayout retry = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        assertEquals(root, retry.migrate());
        retry.abortMigration();
    }

    @Test
    public void retainedWriterLockSurvivesMarkerCompletion() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path root = worldContainer.resolve("mintychochip");
        Files.createDirectories(root);
        Files.writeString(root.resolve("provenance.db"), "db");

        final ProvenanceStorageLayout layout = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        layout.migrate();
        layout.markMigrationCompleteForWriter();

        final IOException failure = assertThrows(
            IOException.class,
            () -> ProvenanceStorageLayout.resolve(worldContainer, primaryWorld).migrate()
        );
        assertTrue(failure.getMessage().contains("writer.lock"));

        layout.releaseMigrationLock();
        final ProvenanceStorageLayout retry = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        assertEquals(root, retry.migrate());
        retry.abortMigration();
    }

    @Test
    public void rejectsSymlinkedDestinationStoreFile() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path root = worldContainer.resolve("mintychochip");
        final Path outside = tempDir.resolve("outside.db");
        Files.createDirectories(root);
        Files.writeString(outside, "outside");
        Files.createSymbolicLink(root.resolve("provenance.db"), outside);

        final IOException failure = assertThrows(
            IOException.class,
            () -> ProvenanceStorageLayout.resolve(worldContainer, primaryWorld).migrate()
        );
        assertTrue(failure.getMessage().contains("migration-conflict"));
        assertEquals("outside", Files.readString(outside));
    }

    @Test
    public void rejectsStagingMarkerWithUnconfiguredSource() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path root = worldContainer.resolve("mintychochip");
        final Path staging = worldContainer.resolve("mintychochip.staging-crash");
        final Path outside = tempDir.resolve("other-world/mintychochip");
        Files.createDirectories(root);
        Files.createDirectories(staging);
        Files.createDirectories(outside);
        Files.writeString(staging.resolve("provenance.db"), "old");
        Files.writeString(outside.resolve("provenance.db"), "outside");
        Files.writeString(
            root.resolve("migration-staging.json"),
            "{\"status\":\"staging\",\"source\":\"" + outside + "\",\"destination\":\"" + root
                + "\",\"staging\":\"" + staging + "\",\"createdAt\":1,\"files\":{\"provenance.db\":\"cba06b5736faf67e54b07b561eae94395e774c517a7d910a54369e1263ccfbd4\"}}"
        );

        final IOException failure = assertThrows(
            IOException.class,
            () -> ProvenanceStorageLayout.resolve(worldContainer, primaryWorld).migrate()
        );
        assertTrue(failure.getMessage().contains("migration-conflict"));
        assertTrue(Files.exists(staging.resolve("provenance.db")));
        assertEquals("outside", Files.readString(outside.resolve("provenance.db")));
    }

    @Test
    public void rejectsCompletionBackupOutsideLegacyParent() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path root = worldContainer.resolve("mintychochip");
        final Path legacy = primaryWorld.resolve("mintychochip");
        final Path escapedBackup = tempDir.resolve("escaped-backup");
        Files.createDirectories(root);
        Files.writeString(root.resolve("provenance.db"), "new");

        final ProvenanceStorageLayout initial = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        initial.migrate();
        initial.markMigrationComplete();
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("provenance.db"), "old");

        final Path marker = root.resolve("migration-complete.json");
        Files.writeString(
            marker,
            Files.readString(marker).replace(
                "\"backup\":\"\"",
                "\"backup\":\"" + escapedBackup + "\""
            )
        );

        final ProvenanceStorageLayout retry = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        retry.migrate();
        final IOException failure = assertThrows(IOException.class, retry::markMigrationComplete);
        assertTrue(failure.getMessage().contains("migration-conflict"));
        assertTrue(Files.exists(legacy.resolve("provenance.db")));
        assertFalse(Files.exists(escapedBackup));
    }
    @Test
    public void completionMarkerKeepsMigrationCopyChecksums() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path legacy = primaryWorld.resolve("mintychochip");
        final Path root = worldContainer.resolve("mintychochip");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("provenance.db"), "old");

        final ProvenanceStorageLayout layout = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        layout.migrate();
        Files.writeString(root.resolve("provenance.db"), "runtime");
        layout.markMigrationComplete();

        final String marker = Files.readString(root.resolve("migration-complete.json"));
        assertTrue(marker.contains("cba06b5736faf67e54b07b561eae94395e774c517a7d910a54369e1263ccfbd4"));
    }
    @Test
    public void completedDestinationIgnoresSymlinkedLegacyCandidate() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path root = worldContainer.resolve("mintychochip");
        final Path legacyTarget = tempDir.resolve("legacy-target/mintychochip");
        final Path legacyLink = primaryWorld.resolve("mintychochip");
        Files.createDirectories(root);
        Files.writeString(root.resolve("provenance.db"), "new");

        final ProvenanceStorageLayout initial = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        initial.migrate();
        initial.markMigrationComplete();
        Files.createDirectories(legacyTarget);
        Files.writeString(legacyTarget.resolve("provenance.db"), "old");
        Files.createDirectories(primaryWorld);
        Files.createSymbolicLink(legacyLink, legacyTarget);

        final ProvenanceStorageLayout retry = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        assertEquals(root, retry.migrate());
        retry.markMigrationComplete();
        assertEquals("new", Files.readString(root.resolve("provenance.db")));
        assertTrue(Files.isSymbolicLink(legacyLink));
    }
    @Test
    public void rejectsSymlinkedWriterLock() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path lockTarget = tempDir.resolve("lock-target");
        Files.createDirectories(worldContainer);
        Files.writeString(lockTarget, "lock");
        Files.createSymbolicLink(worldContainer.resolve("writer.lock"), lockTarget);

        final IOException failure = assertThrows(
            IOException.class,
            () -> ProvenanceStorageLayout.resolve(worldContainer, primaryWorld).migrate()
        );
        assertTrue(failure.getMessage().contains("migration-conflict"));
        assertEquals("lock", Files.readString(lockTarget));
    }
    @Test
    public void lockedWriterLoggerFailureReleasesMigrationOwnership() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path root = worldContainer.resolve("mintychochip");
        Files.createDirectories(root);

        final ProvenanceStorageLayout layout = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        layout.migrate();
        assertThrows(
            IllegalStateException.class,
            () -> ProvenanceWriter.installLocked(
                root,
                message -> {
                    throw new IllegalStateException("logger failed");
                },
                layout.retainMigrationLock()
            )
        );
        assertFalse(ProvenanceWriter.isInstalled());
        assertNull(ItemProvenance.lineage().repository());
        ProvenanceWriter.clearInstall();
        layout.abortMigration();

        final ProvenanceStorageLayout retry = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        assertEquals(root, retry.migrate());
        retry.abortMigration();
    }
    @Test
    public void rejectsSymlinkedCompletionTemporary() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path root = worldContainer.resolve("mintychochip");
        final Path outside = tempDir.resolve("outside-marker");
        Files.createDirectories(root);
        Files.writeString(root.resolve("provenance.db"), "db");
        Files.writeString(outside, "outside");

        final ProvenanceStorageLayout layout = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        layout.migrate();
        Files.createSymbolicLink(root.resolve("migration-complete.json.tmp"), outside);
        final IOException failure = assertThrows(IOException.class, layout::markMigrationComplete);
        assertTrue(failure.getMessage().contains("migration-conflict"));
        assertEquals("outside", Files.readString(outside));
    }
    @Test
    public void rejectsSymlinkedLegacyAncestor() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path outsideWorld = tempDir.resolve("outside/world");
        final Path alias = worldContainer.resolve("alias");
        final Path primaryWorld = alias.resolve("world");
        Files.createDirectories(worldContainer);
        Files.createDirectories(outsideWorld.resolve("mintychochip"));
        Files.writeString(outsideWorld.resolve("mintychochip/provenance.db"), "outside");
        Files.createSymbolicLink(alias, tempDir.resolve("outside"));

        final IOException failure = assertThrows(
            IOException.class,
            () -> ProvenanceStorageLayout.resolve(worldContainer, primaryWorld).migrate()
        );
        assertTrue(failure.getMessage().contains("migration-conflict"));
        assertEquals("outside", Files.readString(outsideWorld.resolve("mintychochip/provenance.db")));
    }
    @Test
    public void missingStagingMarkerCannotReplaceCopyEvidence() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path legacy = primaryWorld.resolve("mintychochip");
        final Path root = worldContainer.resolve("mintychochip");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("provenance.db"), "old");

        final ProvenanceStorageLayout layout = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        layout.migrate();
        Files.writeString(root.resolve("provenance.db"), "runtime");
        Files.delete(root.resolve("migration-staging.json"));

        final IOException failure = assertThrows(IOException.class, layout::markMigrationComplete);
        assertTrue(failure.getMessage().contains("migration-conflict"));
        assertEquals("runtime", Files.readString(root.resolve("provenance.db")));
        assertTrue(Files.exists(legacy.resolve("provenance.db")));
    }
    @Test
    public void markerlessMatchingStoresAreConflict() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path root = worldContainer.resolve("mintychochip");
        final Path legacy = primaryWorld.resolve("mintychochip");
        Files.createDirectories(root);
        Files.createDirectories(legacy);
        Files.writeString(root.resolve("provenance.db"), "same");
        Files.writeString(legacy.resolve("provenance.db"), "same");

        final IOException failure = assertThrows(
            IOException.class,
            () -> ProvenanceStorageLayout.resolve(worldContainer, primaryWorld).migrate()
        );
        assertTrue(failure.getMessage().contains("migration-conflict"));
        assertEquals("same", Files.readString(root.resolve("provenance.db")));
        assertEquals("same", Files.readString(legacy.resolve("provenance.db")));
    }
    @Test
    public void rejectsSymlinkDotDotAncestorAlias() throws Exception {
        final Path server = tempDir.resolve("server");
        final Path outside = tempDir.resolve("outside");
        Files.createDirectories(server);
        Files.createDirectories(outside);
        Files.createSymbolicLink(server.resolve("link"), outside);

        final Path aliasedContainer = server.resolve("link").resolve("..");
        final IOException failure = assertThrows(
            IOException.class,
            () -> ProvenanceStorageLayout.resolve(aliasedContainer, null).migrate()
        );
        assertTrue(failure.getMessage().contains("migration-conflict"));
    }
}
