package dev.mintychochip.genetics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

@Normal
class GeneticsAdminBootstrapTest {

    @Test
    void craftServerRegistersGeneticsDiagnostics() throws Exception {
        final String source = readProjectFile(
            "src/main/java/org/bukkit/craftbukkit/CraftServer.java",
            "paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java"
        );
        assertTrue(source.contains("dev.mintychochip.genetics.GeneticsBootstrap.ensureInstalled(this)"));
    }

    @Test
    void bootstrapUsesOneTimeCommandRegistration() throws Exception {
        final String source = readProjectFile(
            "src/main/java/dev/mintychochip/genetics/GeneticsBootstrap.java",
            "paper-server/src/main/java/dev/mintychochip/genetics/GeneticsBootstrap.java"
        );
        assertTrue(source.contains("if (installed)"));
        assertTrue(source.contains("Bukkit.getCommandMap().register"));
        assertTrue(source.contains("new GeneticsBukkitCommand()"));
    }

    private static String readProjectFile(final String... relativeCandidates) throws Exception {
        Path cwd = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            for (final String relative : relativeCandidates) {
                final Path candidate = cwd.resolve(relative);
                if (Files.isRegularFile(candidate)) {
                    return Files.readString(candidate);
                }
            }
            final Path parent = cwd.getParent();
            if (parent == null) {
                break;
            }
            cwd = parent;
        }
        throw new java.nio.file.NoSuchFileException(String.join(" | ", relativeCandidates));
    }
}
