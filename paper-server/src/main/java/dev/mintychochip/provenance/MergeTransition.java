package dev.mintychochip.provenance;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Immutable stack state captured immediately before a vanilla merge. */
public record MergeTransition(
    @NotNull UUID targetId,
    @NotNull UUID sourceId,
    @NotNull String itemId,
    int targetCountBefore,
    int sourceCountBefore,
    @NotNull StackLocation targetLocation,
    @NotNull StackLocation sourceLocation
) {

    public MergeTransition {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(targetLocation, "targetLocation");
        Objects.requireNonNull(sourceLocation, "sourceLocation");
        if (targetCountBefore <= 0 || sourceCountBefore <= 0) {
            throw new IllegalArgumentException("merge input counts must be positive");
        }
    }

    public static @NotNull Optional<MergeTransition> capture(
        final @NotNull ItemStack target,
        final @NotNull ItemStack source,
        final @NotNull StackLocation targetLocation,
        final @NotNull StackLocation sourceLocation
    ) {
        if (target.isEmpty() || source.isEmpty()) {
            return Optional.empty();
        }
        final String targetItemId = ItemProvenance.itemId(target);
        if (!targetItemId.equals(ItemProvenance.itemId(source))) {
            return Optional.empty();
        }
        final Optional<UUID> targetId = StackStamp.readId(target);
        final Optional<UUID> sourceId = StackStamp.readId(source);
        if (targetId.isEmpty() || sourceId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new MergeTransition(
            targetId.get(),
            sourceId.get(),
            targetItemId,
            target.getCount(),
            source.getCount(),
            targetLocation,
            sourceLocation
        ));
    }

    public boolean matches(final @NotNull ItemStack targetAfter, final @NotNull ItemStack sourceAfter) {
        final int targetGrowth = targetAfter.getCount() - this.targetCountBefore;
        final int sourceShrink = this.sourceCountBefore - sourceAfter.getCount();
        return !targetAfter.isEmpty()
            && targetGrowth > 0
            && targetGrowth == sourceShrink
            && ItemProvenance.itemId(targetAfter).equals(this.itemId)
            && (sourceAfter.isEmpty() || ItemProvenance.itemId(sourceAfter).equals(this.itemId))
            && StackStamp.readId(targetAfter).filter(this.targetId::equals).isPresent()
            && (sourceAfter.isEmpty() || StackStamp.readId(sourceAfter).filter(this.sourceId::equals).isPresent());
    }

    public int amountMoved(final @NotNull ItemStack targetAfter, final @NotNull ItemStack sourceAfter) {
        return this.matches(targetAfter, sourceAfter) ? targetAfter.getCount() - this.targetCountBefore : 0;
    }
}
