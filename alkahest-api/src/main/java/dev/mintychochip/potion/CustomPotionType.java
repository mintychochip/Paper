package dev.mintychochip.potion;

import java.util.List;
import java.util.Set;
import org.bukkit.FeatureFlag;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

/** Concrete catalog-backed potion value created by {@link PotionTypeCatalog}. */
@NullMarked
public final class CustomPotionType implements PotionType {

    private final NamespacedKey key;
    private final List<PotionEffect> effects;
    private final boolean upgradeable;
    private final boolean extendable;
    private final int maxLevel;
    private final Set<FeatureFlag> requiredFeatures;

    CustomPotionType(
        final NamespacedKey key,
        final List<PotionEffect> effects,
        final boolean upgradeable,
        final boolean extendable,
        final int maxLevel,
        final Set<FeatureFlag> requiredFeatures
    ) {
        this.key = key;
        this.effects = List.copyOf(effects);
        this.upgradeable = upgradeable;
        this.extendable = extendable;
        this.maxLevel = maxLevel;
        this.requiredFeatures = Set.copyOf(requiredFeatures);
    }

    @Override
    public @Nullable PotionEffectType getEffectType() {
        return this.effects.isEmpty() ? null : this.effects.get(0).getType();
    }

    @Override
    public @NotNull List<PotionEffect> getPotionEffects() {
        return this.effects;
    }

    @Override
    public boolean isInstant() {
        return this.effects.stream().anyMatch(effect -> effect.getType().isInstant());
    }

    @Override
    public boolean isUpgradeable() {
        return this.upgradeable;
    }

    @Override
    public boolean isExtendable() {
        return this.extendable;
    }

    @Override
    public int getMaxLevel() {
        return this.maxLevel;
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return this.key;
    }

    @Override
    public @NotNull Set<FeatureFlag> requiredFeatures() {
        return this.requiredFeatures;
    }

    @Override
    public boolean isVanilla() {
        return false;
    }

    @Override
    public boolean isCustom() {
        return true;
    }
}
