package dev.mintychochip.genetics;

import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.dto.PhenotypeVariantResolver;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.profile.EquineGeneticsProfile;
import dev.mintychochip.genetics.profile.VariantLabelSets;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Panda;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.bukkit.DyeColor;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Frog;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.jspecify.annotations.Nullable;

/**
 * Pushes decoded genetics phenotypes onto Minecraft registry variants on live entities.
 *
 * <p>Genotype / {@link PhenotypeSnapshot} remain source of truth; this only sets
 * client-visible Bukkit variants.</p>
 */
public final class PhenotypeApplier {

    private PhenotypeApplier() {
    }

    /**
     * Decode genome with the server catalog and apply matching registry variants.
     *
     * @return true if at least one visual property was changed
     */
    public static boolean apply(final Animal animal, final Genome genome) {
        final PhenotypeSnapshot phenotype = AnimalGenetics.phenotypeOf(animal, genome);
        return apply(animal, phenotype);
    }
    public static boolean apply(final AgeableMob ageable, final Genome genome) {
        final PhenotypeSnapshot phenotype = AnimalGenetics.phenotypeOf(ageable, genome);
        return apply(ageable, phenotype);
    }

    /**
     * Apply a pre-decoded phenotype to any ageable NMS mob.
     */
    public static boolean apply(final AgeableMob ageable, final PhenotypeSnapshot phenotype) {
        if (ageable == null || phenotype == null) {
            return false;
        }
        final Entity bukkit = ageable.getBukkitEntity();
        return bukkit == null ? false : apply(bukkit, bukkit.getType(), phenotype);
    }

    /**
     * Preserve the Animal entry point used by existing genetics hooks.
     */
    public static boolean apply(final Animal animal, final PhenotypeSnapshot phenotype) {
        return apply((AgeableMob) animal, phenotype);
    }

    /**
     * Apply a phenotype to a Bukkit entity when its already-resolved type is available.
     * This is also the NMS-free adapter seam used by tests and tools.
     */
    public static boolean apply(
        final Entity bukkit,
        final EntityType type,
        final PhenotypeSnapshot phenotype
    ) {
        if (bukkit == null || type == null || phenotype == null) {
            return false;
        }

        if (applySpecial(bukkit, type, phenotype)) {
            return true;
        }
        final String traitKey = variantTraitKey(type);
        if (traitKey != null) {
            final String label = phenotype.getOrNull(traitKey);
            if (label != null && applyLabel(bukkit, type, label)) {
                return true;
            }
        }

        // Legacy generic coat decoding remains available for old NBT/genomes.
        final Optional<NamespacedKey> legacyKey = PhenotypeVariantResolver.resolve(type, phenotype);
        return legacyKey.isPresent() && applyVariantKey(bukkit, type, legacyKey.get());
    }

    private static boolean applySpecial(
        final Entity bukkit,
        final EntityType type,
        final PhenotypeSnapshot phenotype
    ) {
        if (type == EntityType.HORSE || type == EntityType.DONKEY || type == EntityType.MULE) {
            return applyEquine(bukkit, phenotype);
        }
        if (type == EntityType.LLAMA) {
            return applyLlama(bukkit, phenotype);
        }
        if (type == EntityType.PANDA) {
            return applyPanda(bukkit, phenotype);
        }
        return false;
    }

    private static boolean applyEquine(final Entity bukkit, final PhenotypeSnapshot phenotype) {
        boolean changed = false;
        if (bukkit instanceof Attributable attributable) {
            changed |= applyAttribute(attributable, Attribute.MOVEMENT_SPEED, phenotype.getOrNull("equine.speed"),
                EquineGeneticsProfile.MIN_SPEED, EquineGeneticsProfile.MAX_SPEED);
            changed |= applyAttribute(attributable, Attribute.JUMP_STRENGTH, phenotype.getOrNull("equine.jump"),
                EquineGeneticsProfile.MIN_JUMP, EquineGeneticsProfile.MAX_JUMP);
            changed |= applyAttribute(attributable, Attribute.MAX_HEALTH, phenotype.getOrNull("equine.health"),
                EquineGeneticsProfile.MIN_HEALTH, EquineGeneticsProfile.MAX_HEALTH);
        }
        if (bukkit instanceof Horse horse) {
            final String color = phenotype.getOrNull("equine.color");
            if (color != null) {
                try {
                    horse.setColor(Horse.Color.valueOf(color));
                    changed = true;
                } catch (final IllegalArgumentException ignored) {
                }
            }
            final String markings = phenotype.getOrNull("equine.markings");
            if (markings != null) {
                try {
                    horse.setStyle(Horse.Style.valueOf(
                        "WHITE_FIELD".equals(markings) ? "WHITEFIELD" : markings
                    ));
                    changed = true;
                } catch (final IllegalArgumentException ignored) {
                }
            }
        }
        return changed;
    }

    private static boolean applyAttribute(
        final Attributable attributable,
        final Attribute attribute,
        final @Nullable String label,
        final double min,
        final double max
    ) {
        if (label == null) {
            return false;
        }
        try {
            final double value = Double.parseDouble(label);
            if (!Double.isFinite(value) || value < min || value > max) {
                return false;
            }
            final AttributeInstance instance = attributable.getAttribute(attribute);
            if (instance == null) {
                return false;
            }
            instance.setBaseValue(value);
            return true;
        } catch (final RuntimeException ignored) {
            return false;
        }
    }

    private static boolean applyLlama(final Entity bukkit, final PhenotypeSnapshot phenotype) {
        if (!(bukkit instanceof Llama llama)) {
            return false;
        }
        boolean changed = false;
        final String color = phenotype.getOrNull("llama.color");
        if (color != null) {
            try {
                llama.setColor(Llama.Color.valueOf(color));
                changed = true;
            } catch (final IllegalArgumentException ignored) {
            }
        }
        final String strength = phenotype.getOrNull("llama.strength");
        if (strength != null) {
            try {
                final int value = Integer.parseInt(strength);
                if (value >= 1 && value <= 5) {
                    llama.setStrength(value);
                    changed = true;
                }
            } catch (final RuntimeException ignored) {
            }
        }
        return changed;
    }

    private static boolean applyPanda(final Entity bukkit, final PhenotypeSnapshot phenotype) {
        if (!(bukkit instanceof Panda panda)) {
            return false;
        }
        boolean changed = false;
        final String main = phenotype.getOrNull("panda.main");
        if (main != null) {
            try {
                panda.setMainGene(Panda.Gene.valueOf(main));
                changed = true;
            } catch (final IllegalArgumentException ignored) {
            }
        }
        final String hidden = phenotype.getOrNull("panda.hidden");
        if (hidden != null) {
            try {
                panda.setHiddenGene(Panda.Gene.valueOf(hidden));
                changed = true;
            } catch (final IllegalArgumentException ignored) {
            }
        }
        return changed;
    }

    private static @Nullable String variantTraitKey(final EntityType type) {
        if (type == EntityType.SHEEP) {
            return "sheep.color";
        }
        if (type == EntityType.AXOLOTL) {
            return "axolotl.variant";
        }
        if (type == EntityType.CAT) {
            return "cat.variant";
        }
        if (type == EntityType.CHICKEN) {
            return "chicken.variant";
        }
        if (type == EntityType.COW) {
            return "cow.variant";
        }
        if (type == EntityType.MOOSHROOM) {
            return "mooshroom.variant";
        }
        if (type == EntityType.FOX) {
            return "fox.variant";
        }
        if (type == EntityType.FROG) {
            return "frog.variant";
        }
        if (type == EntityType.PIG) {
            return "pig.variant";
        }
        if (type == EntityType.RABBIT) {
            return "rabbit.variant";
        }
        if (type == EntityType.WOLF) {
            return "wolf.variant";
        }
        if (type == EntityType.VILLAGER) {
            return "villager.type";
        }
        return null;
    }

    private static boolean applyLabel(final Entity bukkit, final EntityType type, final String label) {
        if (type == EntityType.SHEEP) {
            return applySheepLabel(bukkit, label);
        }
        if (type == EntityType.AXOLOTL) {
            return applyAxolotl(bukkit, label);
        }
        if (type == EntityType.CAT) {
            return applyCatLabel(bukkit, label);
        }
        if (type == EntityType.CHICKEN) {
            return applyChickenLabel(bukkit, label);
        }
        if (type == EntityType.COW) {
            return applyCowLabel(bukkit, label);
        }
        if (type == EntityType.MOOSHROOM) {
            return applyMushroomCow(bukkit, label);
        }
        if (type == EntityType.FOX) {
            return applyFox(bukkit, label);
        }
        if (type == EntityType.FROG) {
            return applyFrogLabel(bukkit, label);
        }
        if (type == EntityType.PIG) {
            return applyPigLabel(bukkit, label);
        }
        if (type == EntityType.RABBIT) {
            return applyRabbit(bukkit, label);
        }
        if (type == EntityType.WOLF) {
            return applyWolfLabel(bukkit, label);
        }
        if (type == EntityType.VILLAGER) {
            return applyVillagerLabel(bukkit, label);
        }
        return false;
    }


    private static boolean applyVillagerLabel(final Entity bukkit, final @Nullable String label) {
        if (!(bukkit instanceof Villager villager) || label == null) {
            return false;
        }
        return safelyApplyRegistry(() -> {
            final Villager.Type type = registry(RegistryKey.VILLAGER_TYPE).get(minecraftLabel(label));
            if (type == null) {
                return false;
            }
            villager.setVillagerType(type);
            return true;
        });
    }

    private static boolean applySheepLabel(final Entity bukkit, final @Nullable String label) {
        if (!(bukkit instanceof Sheep sheep) || label == null) {
            return false;
        }
        try {
            sheep.setColor(DyeColor.valueOf(label));
            return true;
        } catch (final IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean applyAxolotl(final Entity bukkit, final String label) {
        if (!(bukkit instanceof Axolotl axolotl) || !VariantLabelSets.AXOLOTL.contains(label)) {
            return false;
        }
        try {
            axolotl.setVariant(Axolotl.Variant.valueOf(label));
            return true;
        } catch (final IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean applyMushroomCow(final Entity bukkit, final String label) {
        if (!(bukkit instanceof MushroomCow mushroomCow) || !VariantLabelSets.MOOSHROOM.contains(label)) {
            return false;
        }
        try {
            mushroomCow.setVariant(MushroomCow.Variant.valueOf(label));
            return true;
        } catch (final IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean applyFox(final Entity bukkit, final String label) {
        if (!(bukkit instanceof Fox fox) || !VariantLabelSets.FOX.contains(label)) {
            return false;
        }
        try {
            fox.setFoxType(Fox.Type.valueOf(label));
            return true;
        } catch (final IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean applyRabbit(final Entity bukkit, final String label) {
        if (!(bukkit instanceof Rabbit rabbit) || !VariantLabelSets.RABBIT.contains(label)) {
            return false;
        }
        try {
            rabbit.setRabbitType(Rabbit.Type.valueOf(label));
            return true;
        } catch (final IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean applyCatLabel(final Entity bukkit, final String label) {
        return VariantLabelSets.CAT.contains(label) && safelyApplyRegistry(
            () -> applyCat(bukkit, minecraftLabel(label))
        );
    }

    private static boolean applyChickenLabel(final Entity bukkit, final String label) {
        if (!(bukkit instanceof Chicken chicken) || !VariantLabelSets.CHICKEN.contains(label)) {
            return false;
        }
        return safelyApplyRegistry(() -> {
            final Chicken.Variant variant = registry(RegistryKey.CHICKEN_VARIANT).get(minecraftLabel(label));
            if (variant == null) {
                return false;
            }
            chicken.setVariant(variant);
            return true;
        });
    }

    private static boolean applyCowLabel(final Entity bukkit, final String label) {
        return VariantLabelSets.COW.contains(label) && safelyApplyRegistry(
            () -> applyCow(bukkit, minecraftLabel(label))
        );
    }

    private static boolean applyFrogLabel(final Entity bukkit, final String label) {
        if (!(bukkit instanceof Frog frog) || !VariantLabelSets.FROG.contains(label)) {
            return false;
        }
        return safelyApplyRegistry(() -> {
            final Frog.Variant variant = registry(RegistryKey.FROG_VARIANT).get(minecraftLabel(label));
            if (variant == null) {
                return false;
            }
            frog.setVariant(variant);
            return true;
        });
    }

    private static boolean applyPigLabel(final Entity bukkit, final String label) {
        if (!(bukkit instanceof Pig pig) || !VariantLabelSets.PIG.contains(label)) {
            return false;
        }
        return safelyApplyRegistry(() -> {
            final Pig.Variant variant = registry(RegistryKey.PIG_VARIANT).get(minecraftLabel(label));
            if (variant == null) {
                return false;
            }
            pig.setVariant(variant);
            return true;
        });
    }

    private static boolean applyWolfLabel(final Entity bukkit, final String label) {
        return VariantLabelSets.WOLF.contains(label) && safelyApplyRegistry(
            () -> applyWolf(bukkit, minecraftLabel(label))
        );
    }

    private static boolean safelyApplyRegistry(final RegistryOperation operation) {
        try {
            return operation.apply();
        } catch (final RuntimeException ignored) {
            return false;
        }
    }

    @FunctionalInterface
    private interface RegistryOperation {
        boolean apply();
    }

    private static NamespacedKey minecraftLabel(final String label) {
        return NamespacedKey.minecraft(label.toLowerCase(Locale.ROOT));
    }

    /**
     * Apply an explicit registry key (e.g. from {@link dev.mintychochip.genetics.dto.BreedGenetics#childVariant()}).
     */
    public static boolean applyVariantKey(
        final Entity bukkit,
        final EntityType type,
        final @Nullable NamespacedKey key
    ) {
        if (key == null) {
            return false;
        }
        if (type == EntityType.CAT) {
            return applyCat(bukkit, key);
        }
        if (type == EntityType.WOLF) {
            return applyWolf(bukkit, key);
        }
        if (type == EntityType.COW) {
            return applyCow(bukkit, key);
        }
        if (type == EntityType.CHICKEN) {
            return applyChicken(bukkit, key);
        }
        if (type == EntityType.FROG) {
            return applyFrog(bukkit, key);
        }
        if (type == EntityType.PIG) {
            return applyPig(bukkit, key);
        }
        return false;
    }

    private static boolean applyCat(final Entity bukkit, final NamespacedKey key) {
        if (!(bukkit instanceof Cat cat)) {
            return false;
        }
        final Cat.Type type = registry(RegistryKey.CAT_VARIANT).get(key);
        if (type == null) {
            return false;
        }
        cat.setCatType(type);
        return true;
    }

    private static boolean applyWolf(final Entity bukkit, final NamespacedKey key) {
        if (!(bukkit instanceof Wolf wolf)) {
            return false;
        }
        final Wolf.Variant variant = registry(RegistryKey.WOLF_VARIANT).get(key);
        if (variant == null) {
            return false;
        }
        wolf.setVariant(variant);
        return true;
    }

    private static boolean applyCow(final Entity bukkit, final NamespacedKey key) {
        if (!(bukkit instanceof Cow cow)) {
            return false;
        }
        final Cow.Variant variant = registry(RegistryKey.COW_VARIANT).get(key);
        if (variant == null) {
            return false;
        }
        cow.setVariant(variant);
        return true;
    }

    private static boolean applyChicken(final Entity bukkit, final NamespacedKey key) {
        if (!(bukkit instanceof Chicken chicken)) {
            return false;
        }
        final Chicken.Variant variant = registry(RegistryKey.CHICKEN_VARIANT).get(key);
        if (variant == null) {
            return false;
        }
        chicken.setVariant(variant);
        return true;
    }

    private static boolean applyFrog(final Entity bukkit, final NamespacedKey key) {
        if (!(bukkit instanceof Frog frog)) {
            return false;
        }
        final Frog.Variant variant = registry(RegistryKey.FROG_VARIANT).get(key);
        if (variant == null) {
            return false;
        }
        frog.setVariant(variant);
        return true;
    }

    private static boolean applyPig(final Entity bukkit, final NamespacedKey key) {
        if (!(bukkit instanceof Pig pig)) {
            return false;
        }
        final Pig.Variant variant = registry(RegistryKey.PIG_VARIANT).get(key);
        if (variant == null) {
            return false;
        }
        pig.setVariant(variant);
        return true;
    }

    private static <T extends Keyed> Registry<T> registry(final RegistryKey<T> key) {
        return RegistryAccess.registryAccess().getRegistry(key);
    }
}
