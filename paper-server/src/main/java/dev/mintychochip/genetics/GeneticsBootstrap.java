package dev.mintychochip.genetics;

import org.bukkit.Bukkit;
import org.bukkit.Server;

/** Installs the read-only genetics administration command once per server process. */
public final class GeneticsBootstrap {

    private static volatile boolean installed;

    private GeneticsBootstrap() {
    }

    public static synchronized void ensureInstalled(final Server server) {
        if (installed) {
            return;
        }
        try {
            Bukkit.getCommandMap().register("mintychochip", new GeneticsBukkitCommand());
            Bukkit.getLogger().info("[mintychochip] genetics diagnostics enabled (/genetics)");
        } catch (final Throwable throwable) {
            Bukkit.getLogger().warning("[mintychochip] failed to register /genetics: " + throwable.getMessage());
        }
        installed = true;
    }
}
