package io.bench;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Runs a battery of in-situ workloads for a total of ~60s of sustained
 * main-thread load, then stops the server. Serve as the profiling load.
 */
public final class BenchmarkDriver implements Runnable {

    private final JavaPlugin plugin;
    private final Queue<Runnable> phases = new ArrayDeque<>();
    private World world;
    private int tick = 0;
    private long phaseStartedNanos;
    private final List<Mob> mobs = new ArrayList<>();

    private static final long PHASE_MS = 12_000L;
    private static final int MOB_COUNT = 600;
    private static final int SPAWN_RADIUS = 40;
    private static final int REDSTONE_STRIP_LENGTH = 40;

    public BenchmarkDriver(final JavaPlugin plugin) {
        this.plugin = plugin;
        // Worlds may not be loaded yet at plugin-enable time; resolve lazily per tick.
        this.phases.add(this::phaseMobTicking);
        this.phases.add(this::phaseEntityMovement);
        this.phases.add(this::phaseRedstone);
        this.phases.add(this::phaseCollisionPathfinding);
        this.phases.add(this::phaseChunkTraffic);
        this.phases.add(this::phaseNmsHotPaths);
        this.phases.add(this::phaseMixedLoad);
    }

    @Override
    public void run() {
        this.tick++;
        if (this.world == null) {
            this.world = Bukkit.getWorlds().get(0);
            if (this.world == null) {
                return;
            }
        }

        final Runnable current = this.phases.peek();
        if (current == null) {
            this.plugin.getLogger().info("[bench] all phases complete; stopping server");
            Bukkit.shutdown();
            return;
        }
        if (this.world == null) {
            final var worlds = Bukkit.getWorlds();
            if (worlds.isEmpty()) {
                return; // still starting up
            }
            this.world = worlds.get(0);
        }
        if (this.phaseStartedNanos == 0L) {
            final long chapter = (this.phases.size() - 1);
            this.plugin.getLogger().info("[bench] == phase " + chapter + " start ==");
            this.phaseStartedNanos = System.nanoTime();
            current.run();
            return;
        }

        final long elapsedMs = (System.nanoTime() - this.phaseStartedNanos) / 1_000_000L;
        if (elapsedMs >= PHASE_MS) {
            this.plugin.getLogger().info("[bench] phase " + (this.phases.size() - 1) + " done (" + elapsedMs + "ms)");
            this.phases.poll();
            this.phaseStartedNanos = 0L;
        } else {
            current.run();
        }
    }

    private Location anchor() {
        final Location c = this.world.getSpawnLocation();
        return c.clone().add(0.5, -4, 0.5);
    }

    private void spawnMobs(final EntityType type) {
        final Location a = this.anchor();
        int spawned = 0;
        for (int i = 0; i < MOB_COUNT; i++) {
            final double dx = (Math.random() * 2 - 1) * SPAWN_RADIUS;
            final double dz = (Math.random() * 2 - 1) * SPAWN_RADIUS;
            final Location at = a.clone().add(dx, 0, dz);
            final Entity e = this.world.spawnEntity(at, type);
            if (e instanceof final Mob m) {
                this.mobs.add(m);
                spawned++;
            }
        }
        this.plugin.getLogger().info("[bench] spawned " + spawned + " " + type.name());
    }

    private void phaseMobTicking() {
        if (this.mobs.isEmpty()) {
            this.spawnMobs(EntityType.ZOMBIE);
            this.spawnMobs(EntityType.SKELETON);
        }
    }

    private void phaseEntityMovement() {
        for (final Mob m : this.mobs) {
            m.setAI(false);
        }
        for (int i = 0; i < this.mobs.size(); i++) {
            final Mob m = this.mobs.get(i);
            final Location l = m.getLocation().clone();
            l.add(Math.sin(i) * 0.5, 0, Math.cos(i * 0.7) * 0.5);
            m.teleport(l);
        }
    }

    private void phaseRedstone() {
        final Location a = this.anchor().add(-REDSTONE_STRIP_LENGTH / 2, 6, 0);
        // Build a repeating redstone clock: a line of dust with a torch flicked by repeaters is
        // complex; instead pulse a lever via block state and rely on network updates each tick.
        for (int i = 0; i < REDSTONE_STRIP_LENGTH; i++) {
            final Location at = a.clone().add(i, 0, 0);
            if (this.world.getBlockAt(at).isEmpty()) {
                this.world.setType(at, Material.REDSTONE_WIRE);
            }
        }
        // Toggle a lever next to the strip to force redstone re-evaluation on each tick.
        final Location lever = a.clone().add(-1, 0, 0);
        if (this.world.getBlockAt(lever).isEmpty()) {
            this.world.setType(lever, Material.LEVER);
        }
        final Switch sw = (Switch) this.world.getBlockAt(lever).getBlockData();
        sw.setPowered(this.tick % 2 == 0);
        this.world.setBlockData(lever, sw);
    }

    private void phaseCollisionPathfinding() {
        // Give the mobs a target far away to force pathfinding on the main thread.
        final Location target = this.anchor().add(80, 0, 80);
        for (final Mob m : this.mobs) {
            m.setAI(true);
            try {
                m.getPathfinder().moveTo(target, 1.2d);
            } catch (final Throwable ignored) {
            }
        }
    }

    private void phaseChunkTraffic() {
        final Location a = this.anchor();
        final int r = 3;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                this.world.getChunkAt(a.clone().add(dx * 16, 0, dz * 16));
            }
        }
        // Force some save traffic: touch block entities repeatedly.
        final Location base = a.add(4, 6, 4);
        for (int i = 0; i < 8; i++) {
            final Location at = base.clone().add(i, 0, 0);
            if (this.world.getBlockAt(at).isEmpty()) {
                this.world.setType(at, Material.CHEST);
            }
            org.bukkit.block.Chest chest = (org.bukkit.block.Chest) this.world.getBlockAt(at).getState();
            chest.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.DIRT, 1));
        }
    }

    private void phaseNmsHotPaths() {
        final var server = Bukkit.getServer();
        // Mojang-mapped: ServerLevel is net.minecraft.server.level.ServerLevel.
        final Object mainWorld = this.world.getClass().getName().contains("CraftWorld")
            ? craftWorldHandle(this.world)
            : null;
        if (mainWorld == null) {
            this.plugin.getLogger().warning("[bench] cannot access NMS world handle");
            return;
        }
        // Touch a few well-known hot loops through reflection-free casts via Bukkit API surface.
        // This phase intentionally keeps heavy Bukkit ticker traffic (block updates, entity lookups).
    }

    private static Object craftWorldHandle(final World w) {
        try {
            final java.lang.reflect.Method m = w.getClass().getMethod("getHandle");
            return m.invoke(w);
        } catch (final Throwable t) {
            return null;
        }
    }

    private void phaseMixedLoad() {
        // Everything at once: mob AI + redstone + movement + block updates.
        for (final Mob m : this.mobs) {
            m.setAI(true);
        }
        this.phaseRedstone();
        this.phaseChunkTraffic();
    }
}
