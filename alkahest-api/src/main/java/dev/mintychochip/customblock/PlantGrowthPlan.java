package dev.mintychochip.customblock;

import dev.mintychochip.behavior.BlockDataView;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable, tagged world operation returned by a custom plant receiver.
 *
 * <p>Structure coordinates are relative to the plant origin. The server validates the selected
 * carrier and world bounds before applying any explicit plan.</p>
 */
public final class PlantGrowthPlan {

    public static final int MAX_STRUCTURE_CHANGES = 4096;
    public static final int MAX_RELATIVE_OFFSET = 32;

    public enum Kind {
        DEFAULT,
        NONE,
        STATE,
        STRUCTURE
    }

    private final Kind kind;
    private final Optional<BlockDataView> state;
    private final List<PlantBlockChange> changes;

    private PlantGrowthPlan(
        final Kind kind,
        final Optional<BlockDataView> state,
        final List<PlantBlockChange> changes
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.state = Objects.requireNonNull(state, "state");
        this.changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
    }

    public static @NotNull PlantGrowthPlan defaultPlan() {
        return new PlantGrowthPlan(Kind.DEFAULT, Optional.empty(), List.of());
    }

    public static @NotNull PlantGrowthPlan none() {
        return new PlantGrowthPlan(Kind.NONE, Optional.empty(), List.of());
    }

    public static @NotNull PlantGrowthPlan state(@NotNull final BlockDataView state) {
        return new PlantGrowthPlan(Kind.STATE, Optional.of(Objects.requireNonNull(state, "state")), List.of());
    }

    public static @NotNull PlantGrowthPlan structure(
        @NotNull final List<PlantBlockChange> changes
    ) {
        Objects.requireNonNull(changes, "changes");
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("structure must contain at least one change");
        }
        if (changes.size() > MAX_STRUCTURE_CHANGES) {
            throw new IllegalArgumentException("structure exceeds " + MAX_STRUCTURE_CHANGES + " changes");
        }

        final Set<Offset> offsets = new HashSet<>(changes.size());
        for (final PlantBlockChange change : changes) {
            Objects.requireNonNull(change, "changes must not contain null");
            if (Math.abs(change.x()) > MAX_RELATIVE_OFFSET
                || Math.abs(change.y()) > MAX_RELATIVE_OFFSET
                || Math.abs(change.z()) > MAX_RELATIVE_OFFSET) {
                throw new IllegalArgumentException("structure offset exceeds " + MAX_RELATIVE_OFFSET);
            }
            if (!offsets.add(new Offset(change.x(), change.y(), change.z()))) {
                throw new IllegalArgumentException("structure contains duplicate offset");
            }
        }
        return new PlantGrowthPlan(Kind.STRUCTURE, Optional.empty(), changes);
    }

    public @NotNull Kind kind() {
        return this.kind;
    }

    public @NotNull Optional<BlockDataView> state() {
        return this.state;
    }

    public @NotNull List<PlantBlockChange> changes() {
        return this.changes;
    }

    private record Offset(int x, int y, int z) {
    }
}
