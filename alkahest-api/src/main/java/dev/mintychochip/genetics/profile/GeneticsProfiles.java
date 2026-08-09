package dev.mintychochip.genetics.profile;

import java.util.Collections;
import java.util.LinkedHashMap;
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
        // Task 2 replaces these placeholders with variant profiles and adapters.
        map.put(EntityType.AXOLOTL, EmptyGeneticsProfile.of("axolotl"));
        map.put(EntityType.CAT, EmptyGeneticsProfile.of("cat"));
        map.put(EntityType.CHICKEN, EmptyGeneticsProfile.of("chicken"));
        map.put(EntityType.COW, EmptyGeneticsProfile.of("cow"));
        map.put(EntityType.MOOSHROOM, EmptyGeneticsProfile.of("mooshroom"));
        map.put(EntityType.FOX, EmptyGeneticsProfile.of("fox"));
        map.put(EntityType.FROG, EmptyGeneticsProfile.of("frog"));
        map.put(EntityType.PIG, EmptyGeneticsProfile.of("pig"));
        map.put(EntityType.RABBIT, EmptyGeneticsProfile.of("rabbit"));
        map.put(EntityType.WOLF, EmptyGeneticsProfile.of("wolf"));

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