package dev.mintychochip.provenance;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Restart-stable holder labels for provenance observations.
 */
public final class ProvenanceLocations {

    private ProvenanceLocations() {
    }

    public static @NotNull StackLocation forContainer(final @NotNull Container container, final int slot) {
        if (container instanceof BlockEntity blockEntity) {
            return forBlockEntity(blockEntity, slot);
        }
        return StackLocation.unknown();
    }

    public static @NotNull StackLocation forBlockEntity(final @NotNull BlockEntity blockEntity, final int slot) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            return StackLocation.unknown();
        }
        final BlockPos pos = blockEntity.getBlockPos();
        return StackLocation.labeled(
            "container:" + level.dimension().identifier()
                + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + ":" + slot
        );
    }

    public static @NotNull StackLocation forEntityContainer(final @NotNull UUID entityId, final int slot) {
        return StackLocation.labeled("container-entity:" + entityId + ":" + slot);
    }
}
