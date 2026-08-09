package dev.mintychochip.genetics.profile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            List.of("LUCY", "WILD", "GOLD", "CYAN", "BLUE")));
        map.put(EntityType.CAT, VariantGeneticsProfile.of("cat", "cat.variant",
            List.of("TABBY", "BLACK", "RED", "SIAMESE", "CALICO", "BRITISH_SHORTHAIR", "PERSIAN", "RAGDOLL", "WHITE")));
        map.put(EntityType.CHICKEN, VariantGeneticsProfile.of("chicken", "chicken.variant",
            List.of("WHITE", "BROWN")));
        map.put(EntityType.COW, VariantGeneticsProfile.of("cow", "cow.variant",
            List.of("NORMAL")));
        map.put(EntityType.MOOSHROOM, VariantGeneticsProfile.of("mooshroom", "mooshroom.variant",
            List.of("RED", "BROWN")));
        map.put(EntityType.FOX, VariantGeneticsProfile.of("fox", "fox.variant",
            List.of("RED", "SNOW")));
        map.put(EntityType.FROG, VariantGeneticsProfile.of("frog", "frog.variant",
            List.of("TEMPERATE", "WARM", "COLD")));
        map.put(EntityType.PIG, VariantGeneticsProfile.of("pig", "pig.variant",
            List.of("MEATY", "NORMAL")));
        map.put(EntityType.RABBIT, VariantGeneticsProfile.of("rabbit", "rabbit.variant",
            List.of("BROWN", "WHITE", "BLACK", "GOLD", "SALT_AND_PEPPER", "KILLER")));
        map.put(EntityType.WOLF, VariantGeneticsProfile.of("wolf", "wolf.variant",
            List.of("PALE", "WOODS", "ASHEN", "BLACK", "CHESTNUT", "RUSTY", "SNOWY")));

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

        // Task 3 replaces these placeholders with custom profiles.
        map.put(EntityType.PANDA, EmptyGeneticsProfile.of("panda"));
        map.put(EntityType.HORSE, EmptyGeneticsProfile.of("horse"));
        map.put(EntityType.DONKEY, EmptyGeneticsProfile.of("donkey"));
        map.put(EntityType.MULE, EmptyGeneticsProfile.of("mule"));
        map.put(EntityType.LLAMA, EmptyGeneticsProfile.of("llama"));
        map.put(EntityType.VILLAGER, EmptyGeneticsProfile.of("villager"));
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
}