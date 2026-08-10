package io.bench;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class BenchmarkPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("HotLoopsBench enabling");
        Bukkit.getScheduler().runTaskTimer(this, new BenchmarkDriver(this), 80L, 20L);
    }

    @Override
    public void onDisable() {
        getLogger().info("HotLoopsBench disabling");
    }
}
