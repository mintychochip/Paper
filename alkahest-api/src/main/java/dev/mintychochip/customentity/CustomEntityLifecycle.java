package dev.mintychochip.customentity;

import dev.mintychochip.behavior.BlockDataView;
import dev.mintychochip.behavior.Decision;
import dev.mintychochip.behavior.EntityView;
import dev.mintychochip.behavior.Position;
import dev.mintychochip.behavior.ValueOverride;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;

/**
 * Spawn / apply path for custom entities on vanilla carriers (Bukkit API only, no NMS).
 *
 * <p>Primary host: {@link EntityHostType#BLOCK_MODEL} -> {@link BlockDisplay} with block model + transform + PDC identity.
 */
public final class CustomEntityLifecycle {

    private CustomEntityLifecycle() {
    }

    /**
     * Apply a registered (or any) block-model definition onto an existing {@link BlockDisplay}.
     * Sets block data, transformation, and stamps custom identity.
     */
    public static void apply(
        @NotNull final BlockDisplay display,
        @NotNull final CustomEntityDefinition definition
    ) {
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(definition, "definition");
        final BlockModelHostSpec host = requireBlockModel(definition);
        apply(display, definition, host.blockMaterial().createBlockData());
    }

    /**
     * Core apply path with injected {@link BlockData} so unit tests can drive the shipped
     * presentation + identity logic without a full block registry bootstrap.
     */
    public static void apply(
        @NotNull final BlockDisplay display,
        @NotNull final CustomEntityDefinition definition,
        @NotNull final BlockData blockData
    ) {
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(blockData, "blockData");
        final BlockModelHostSpec host = requireBlockModel(definition);
        final EntityApplyResult plan = requireApplyResult(
            definition.behavior().onApply().receive(new EntityApplyContext(
                snapshotEntity(display),
                Optional.empty(),
                Optional.of(snapshotBlockData(blockData))
            ))
        );
        if (plan.decision() == Decision.DENY) {
            throw new IllegalStateException("custom entity apply denied: " + definition.namespacedKey());
        }

        display.setBlock(resolveBlockData(plan.blockData(), blockData));
        final Transformation transform = host.toTransformation();
        display.setTransformation(transform);
        CustomEntities.stamp(display, definition);
    }

    /**
     * Spawn a new {@link BlockDisplay} at {@code location} and apply the definition.
     *
     * @return the live in-world carrier (type remains {@link EntityType#BLOCK_DISPLAY})
     */
    public static @NotNull BlockDisplay spawn(
        @NotNull final Location location,
        @NotNull final CustomEntityDefinition definition
    ) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(definition, "definition");
        final World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("location has no world");
        }
        requireBlockModel(definition);
        final EntitySpawnResult plan = requireSpawnResult(
            definition.behavior().onSpawn().receive(new EntitySpawnContext(snapshotPosition(location), Optional.empty()))
        );
        if (plan.decision() == Decision.DENY) {
            throw new IllegalStateException("custom entity spawn denied: " + definition.namespacedKey());
        }

        return world.spawn(location, BlockDisplay.class, entity -> apply(entity, definition, plan));
    }

    /**
     * Spawn by registered key.
     *
     * @throws IllegalArgumentException if the key is not registered or not block-model
     */
    public static @NotNull BlockDisplay spawn(
        @NotNull final Location location,
        @NotNull final NamespacedKey key
    ) {
        Objects.requireNonNull(key, "key");
        final CustomEntityDefinition def = CustomEntities.get(key)
            .orElseThrow(() -> new IllegalArgumentException("custom entity not registered: " + key));
        return spawn(location, def);
    }

    /**
     * Whether this entity is a block-display carrier suitable for block-model custom entities.
     */
    public static boolean isBlockModelCarrier(@NotNull final Entity entity) {
        Objects.requireNonNull(entity, "entity");
        return entity instanceof BlockDisplay || entity.getType() == EntityType.BLOCK_DISPLAY;
    }

    private static void apply(
        @NotNull final BlockDisplay display,
        @NotNull final CustomEntityDefinition definition,
        @NotNull final EntitySpawnResult spawnPlan
    ) {
        final BlockModelHostSpec host = requireBlockModel(definition);
        final BlockData carrierData = resolveBlockData(
            spawnPlan.blockData(),
            host.blockMaterial().createBlockData()
        );
        apply(display, definition, carrierData);
    }

    private static EntitySpawnResult requireSpawnResult(final EntitySpawnResult result) {
        if (result == null) {
            throw new IllegalStateException("custom entity spawn receiver returned null");
        }
        return result;
    }

    private static EntityApplyResult requireApplyResult(final EntityApplyResult result) {
        if (result == null) {
            throw new IllegalStateException("custom entity apply receiver returned null");
        }
        return result;
    }

    private static BlockData resolveBlockData(
        final ValueOverride<BlockDataView> override,
        final BlockData defaultData
    ) {
        Objects.requireNonNull(override, "override");
        Objects.requireNonNull(defaultData, "defaultData");
        if (override instanceof ValueOverride.UseDefault<BlockDataView>) {
            return defaultData;
        }
        final BlockDataView view = ((ValueOverride.Value<BlockDataView>) override).value();
        return Bukkit.createBlockData(view.serialized());
    }

    private static BlockDataView snapshotBlockData(final BlockData blockData) {
        Material material = blockData.getMaterial();
        if (material == null) {
            material = Material.AIR;
        }
        String serialized = blockData.getAsString();
        if (serialized == null || serialized.isBlank()) {
            serialized = material.getKey().toString();
        }
        return new BlockDataView(material, serialized);
    }

    private static EntityView snapshotEntity(final Entity entity) {
        final UUID uniqueId = entity.getUniqueId() == null
            ? new UUID(0L, 0L)
            : entity.getUniqueId();
        final EntityType type = entity.getType();
        final NamespacedKey carrierType = type == null ? EntityType.BLOCK_DISPLAY.getKey() : type.getKey();
        final Location location = entity.getLocation();
        final Position position = location == null
            ? new Position("unknown", 0.0, 0.0, 0.0)
            : snapshotPosition(location);
        return new EntityView(uniqueId, carrierType, position, CustomEntities.keyOf(entity));
    }

    private static Position snapshotPosition(final Location location) {
        final World world = location.getWorld();
        final String worldKey = world == null || world.getKey() == null
            ? "unknown"
            : world.getKey().toString();
        return new Position(worldKey, location.getX(), location.getY(), location.getZ());
    }

    private static BlockModelHostSpec requireBlockModel(final CustomEntityDefinition definition) {
        final EntityHostSpec host = definition.host();
        if (!(host instanceof BlockModelHostSpec blockHost)) {
            throw new IllegalArgumentException(
                "custom entity " + definition.namespacedKey() + " host is not BLOCK_MODEL: " + definition.hostType()
            );
        }
        return blockHost;
    }
}
