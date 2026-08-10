package dev.mintychochip.customblock;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable behavior receivers owned by a custom block definition.
 *
 * <p>Receivers receive API snapshots and return plans. They never receive mutable Bukkit or NMS
 * handles and never mutate an event or world.</p>
 */
public final class CustomBlockBehavior {

    @FunctionalInterface
    public interface BlockPlaceReceiver {
        PlacementResult receive(BlockPlaceContext context);
    }

    @FunctionalInterface
    public interface BlockBreakReceiver {
        BreakResult receive(BlockBreakContext context);
    }

    @FunctionalInterface
    public interface BlockInteractReceiver {
        InteractionResult receive(BlockInteractContext context);
    }

    @FunctionalInterface
    public interface BlockMoveReceiver {
        MovementResult receive(BlockMoveContext context);
    }

    @FunctionalInterface
    public interface BlockExplodeReceiver {
        ExplosionResult receive(BlockExplodeContext context);
    }

    private final BlockPlaceReceiver onPlace;
    private final BlockBreakReceiver onBreak;
    private final BlockInteractReceiver onInteract;
    private final BlockMoveReceiver onMove;
    private final BlockExplodeReceiver onExplode;

    private CustomBlockBehavior(
        final BlockPlaceReceiver onPlace,
        final BlockBreakReceiver onBreak,
        final BlockInteractReceiver onInteract,
        final BlockMoveReceiver onMove,
        final BlockExplodeReceiver onExplode
    ) {
        this.onPlace = Objects.requireNonNull(onPlace, "onPlace");
        this.onBreak = Objects.requireNonNull(onBreak, "onBreak");
        this.onInteract = Objects.requireNonNull(onInteract, "onInteract");
        this.onMove = Objects.requireNonNull(onMove, "onMove");
        this.onExplode = Objects.requireNonNull(onExplode, "onExplode");
    }

    public static @NotNull CustomBlockBehavior defaults() {
        return builder().build();
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public @NotNull BlockPlaceReceiver onPlace() {
        return this.onPlace;
    }

    public @NotNull BlockBreakReceiver onBreak() {
        return this.onBreak;
    }

    public @NotNull BlockInteractReceiver onInteract() {
        return this.onInteract;
    }

    public @NotNull BlockMoveReceiver onMove() {
        return this.onMove;
    }

    public @NotNull BlockExplodeReceiver onExplode() {
        return this.onExplode;
    }

    public static final class Builder {
        private BlockPlaceReceiver onPlace = context -> PlacementResult.defaults();
        private BlockBreakReceiver onBreak = context -> BreakResult.defaults();
        private BlockInteractReceiver onInteract = context -> InteractionResult.defaults();
        private BlockMoveReceiver onMove = context -> MovementResult.defaults();
        private BlockExplodeReceiver onExplode = context -> ExplosionResult.defaults();

        private Builder() {
        }

        public Builder onPlace(final BlockPlaceReceiver onPlace) {
            this.onPlace = Objects.requireNonNull(onPlace, "onPlace");
            return this;
        }

        public Builder onBreak(final BlockBreakReceiver onBreak) {
            this.onBreak = Objects.requireNonNull(onBreak, "onBreak");
            return this;
        }

        public Builder onInteract(final BlockInteractReceiver onInteract) {
            this.onInteract = Objects.requireNonNull(onInteract, "onInteract");
            return this;
        }

        public Builder onMove(final BlockMoveReceiver onMove) {
            this.onMove = Objects.requireNonNull(onMove, "onMove");
            return this;
        }

        public Builder onExplode(final BlockExplodeReceiver onExplode) {
            this.onExplode = Objects.requireNonNull(onExplode, "onExplode");
            return this;
        }

        public @NotNull CustomBlockBehavior build() {
            return new CustomBlockBehavior(this.onPlace, this.onBreak, this.onInteract, this.onMove, this.onExplode);
        }
    }
}
