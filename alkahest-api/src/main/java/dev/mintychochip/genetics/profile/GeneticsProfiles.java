package dev.mintychochip.genetics.profile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.EntityType;

/**
 * Explicit registry mapping every breedable entity type to its genetics profile.
 * The generic profile remains the fallback for unknown / unprofiled types.
 */
public final class GeneticsProfiles {

    private GeneticsProfiles() {
    }

    private static final Map<EntityType, GeneticsProfile> EXPLICIT = buildRegistry();

    private static Map<EntityType, GeneticsProfile> buildRegistry() {
        final Map<EntityType, GeneticsProfile> map = new LinkedHashMap<>();
        map.put(EntityType.SHEEP, SheepGeneticsProfile.INSTANCE);
        map.put(EntityType.AXOLOTL, VariantGeneticsProfile.of("axolotl", "axolotl.variant",
            VariantLabelSets.AXOLOTL));
        map.put(EntityType.CAT, VariantGeneticsProfile.of("cat", "cat.variant",
            VariantLabelSets.CAT));
        map.put(EntityType.CHICKEN, VariantGeneticsProfile.of("chicken", "chicken.variant",
            VariantLabelSets.CHICKEN));
        map.put(EntityType.COW, VariantGeneticsProfile.of("cow", "cow.variant",
            VariantLabelSets.COW));
        map.put(EntityType.MOOSHROOM, VariantGeneticsProfile.of("mooshroom", "mooshroom.variant",
            VariantLabelSets.MOOSHROOM));
        map.put(EntityType.FOX, VariantGeneticsProfile.of("fox", "fox.variant",
            VariantLabelSets.FOX));
        map.put(EntityType.FROG, VariantGeneticsProfile.of("frog", "frog.variant",
            VariantLabelSets.FROG));
        map.put(EntityType.PIG, VariantGeneticsProfile.of("pig", "pig.variant",
            VariantLabelSets.PIG));
        map.put(EntityType.RABBIT, VariantGeneticsProfile.of("rabbit", "rabbit.variant",
            VariantLabelSets.RABBIT));
        map.put(EntityType.WOLF, VariantGeneticsProfile.of("wolf", "wolf.variant",
            VariantLabelSets.WOLF));

        map.put(EntityType.ARMADILLO, EmptyGeneticsProfile.of("armadillo"));
        map.put(EntityType.BEE, EmptyGeneticsProfile.of("bee"));
        map.put(EntityType.CAMEL, EmptyGeneticsProfile.of("camel"));
        map.put(EntityType.GOAT, EmptyGeneticsProfile.of("goat"));
        map.put(EntityType.HOGLIN, EmptyGeneticsProfile.of("hoglin"));
        map.put(EntityType.NAUTILUS, EmptyGeneticsProfile.of("nautilus"));
        map.put(EntityType.OCELOT, EmptyGeneticsProfile.of("ocelot"));
        map.put(EntityType.SNIFFER, EmptyGeneticsProfile.of("sniffer"));
        map.put(EntityType.STRIDER, EmptyGeneticsProfile.of("strider"));
        map.put(EntityType.TURTLE, EmptyGeneticsProfile.of("turtle"));

        map.put(EntityType.PANDA, PandaGeneticsProfile.INSTANCE);
        map.put(EntityType.HORSE, EquineGeneticsProfile.HORSE);
        map.put(EntityType.DONKEY, EquineGeneticsProfile.DONKEY);
        map.put(EntityType.MULE, EquineGeneticsProfile.MULE);
        map.put(EntityType.LLAMA, LlamaGeneticsProfile.INSTANCE);
        map.put(EntityType.VILLAGER, VillagerGeneticsProfile.INSTANCE);
        map.put(EntityType.HAPPY_GHAST, EmptyGeneticsProfile.of("happy_ghast"));

        return Collections.unmodifiableMap(map);
    }

    public static GeneticsProfile generic() {
        return GenericGeneticsProfile.INSTANCE;
    }

    public static boolean isExplicit(final EntityType entityType) {
        return EXPLICIT.containsKey(Objects.requireNonNull(entityType, "entityType"));
    }

    public static GeneticsProfile forEntityType(final EntityType entityType) {
        Objects.requireNonNull(entityType, "entityType");
        return EXPLICIT.getOrDefault(entityType, GenericGeneticsProfile.INSTANCE);
    }

    /**
     * Resolves a permitted parent/child entity combination to its family and
     * child profile. Mules are offspring-only; they cannot be parents.
     */
    public static Optional<BreedPlan> resolveBreed(
        final EntityType parentA,
        final EntityType parentB,
        final EntityType child
    ) {
        Objects.requireNonNull(parentA, "parentA");
        Objects.requireNonNull(parentB, "parentB");
        Objects.requireNonNull(child, "child");

        if (child == EntityType.MULE) {
            if (isHorsePair(parentA, parentB)) {
                return Optional.of(new BreedPlan(EquineGeneticsProfile.FAMILY, EquineGeneticsProfile.MULE));
            }
            return Optional.empty();
        }
        if (isEquine(parentA) || isEquine(parentB) || isEquine(child)) {
            if (parentA.equals(parentB) && parentA.equals(child)
                && (parentA == EntityType.HORSE || parentA == EntityType.DONKEY)) {
                return Optional.of(new BreedPlan(
                    EquineGeneticsProfile.FAMILY,
                    EquineGeneticsProfile.forEntityType(child)
                ));
            }
            return Optional.empty();
        }
        if (parentA == EntityType.VILLAGER && parentB == EntityType.VILLAGER && child == EntityType.VILLAGER) {
            return Optional.of(new BreedPlan(VillagerGeneticsProfile.INSTANCE, VillagerGeneticsProfile.INSTANCE));
        }
        if (parentA != parentB || parentA != child || !isExplicit(parentA) || parentA == EntityType.HAPPY_GHAST) {
            return Optional.empty();
        }
        return Optional.of(new BreedPlan(forEntityType(parentA), forEntityType(child)));
    }

    private static boolean isHorsePair(final EntityType parentA, final EntityType parentB) {
        return (parentA == EntityType.HORSE && parentB == EntityType.DONKEY)
            || (parentA == EntityType.DONKEY && parentB == EntityType.HORSE);
    }

    private static boolean isEquine(final EntityType entityType) {
        return entityType == EntityType.HORSE || entityType == EntityType.DONKEY || entityType == EntityType.MULE;
    }
}