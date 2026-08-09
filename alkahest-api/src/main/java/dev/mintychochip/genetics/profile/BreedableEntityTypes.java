package dev.mintychochip.genetics.profile;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.entity.EntityType;

/**
 * The closed set of entity types that participate in a confirmed breeding or
 * offspring-producing path and therefore require an explicit genetics profile.
 */
public final class BreedableEntityTypes {

    private BreedableEntityTypes() {
    }

    private static final Set<EntityType> PARENT_BREEDING =
        Collections.unmodifiableSet(new LinkedHashSet<>(List.of(
            EntityType.ARMADILLO,
            EntityType.AXOLOTL,
            EntityType.BEE,
            EntityType.CAMEL,
            EntityType.CHICKEN,
            EntityType.CAT,
            EntityType.COW,
            EntityType.MOOSHROOM,
            EntityType.DONKEY,
            EntityType.HORSE,
            EntityType.LLAMA,
            EntityType.OCELOT,
            EntityType.FOX,
            EntityType.FROG,
            EntityType.GOAT,
            EntityType.HOGLIN,
            EntityType.NAUTILUS,
            EntityType.PANDA,
            EntityType.PIG,
            EntityType.RABBIT,
            EntityType.SHEEP,
            EntityType.SNIFFER,
            EntityType.STRIDER,
            EntityType.TURTLE,
            EntityType.WOLF,
            EntityType.VILLAGER
        )));

    private static final Set<EntityType> EXPLICIT = explicit();

    private static Set<EntityType> explicit() {
        final LinkedHashSet<EntityType> set = new LinkedHashSet<>(PARENT_BREEDING);
        set.add(EntityType.MULE);
        set.add(EntityType.HAPPY_GHAST);
        return Collections.unmodifiableSet(set);
    }

    /**
     * Standard breedable parents that reproduce through a parent-mating path.
     */
    public static Set<EntityType> parentBreedingTypes() {
        return PARENT_BREEDING;
    }

    /**
     * Every type that must resolve to an explicit profile: the parent set plus
     * offspring-only {@code MULE} and the no-parent {@code HAPPY_GHAST}.
     */
    public static Set<EntityType> explicitTypes() {
        return EXPLICIT;
    }
}