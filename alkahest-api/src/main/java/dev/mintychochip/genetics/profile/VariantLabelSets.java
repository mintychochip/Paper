package dev.mintychochip.genetics.profile;

import java.util.Map;
import java.util.Set;

/**
 * Canonical labels emitted by the vanilla breedable variant profiles.
 *
 * <p>Labels intentionally match Bukkit enum names or registry paths after
 * lower-casing, so server adapters can apply every emitted value.</p>
 */
public final class VariantLabelSets {

    public static final Set<String> AXOLOTL = Set.of("LUCY", "WILD", "GOLD", "CYAN", "BLUE");
    public static final Set<String> CAT = Set.of(
        "ALL_BLACK", "BLACK", "BRITISH_SHORTHAIR", "CALICO", "JELLIE", "PERSIAN",
        "RAGDOLL", "RED", "SIAMESE", "TABBY", "WHITE"
    );
    public static final Set<String> CHICKEN = Set.of("COLD", "TEMPERATE", "WARM");
    public static final Set<String> COW = Set.of("COLD", "TEMPERATE", "WARM");
    public static final Set<String> MOOSHROOM = Set.of("RED", "BROWN");
    public static final Set<String> FOX = Set.of("RED", "SNOW");
    public static final Set<String> FROG = Set.of("COLD", "TEMPERATE", "WARM");
    public static final Set<String> PIG = Set.of("COLD", "TEMPERATE", "WARM");
    public static final Set<String> RABBIT = Set.of(
        "BROWN", "WHITE", "BLACK", "BLACK_AND_WHITE", "GOLD", "SALT_AND_PEPPER", "THE_KILLER_BUNNY"
    );
    public static final Set<String> WOLF = Set.of(
        "ASHEN", "BLACK", "CHESTNUT", "PALE", "RUSTY", "SNOWY", "SPOTTED", "STRIPED", "WOODS"
    );

    private static final Map<String, Set<String>> BY_SPECIES = Map.of(
        "axolotl", AXOLOTL,
        "cat", CAT,
        "chicken", CHICKEN,
        "cow", COW,
        "mooshroom", MOOSHROOM,
        "fox", FOX,
        "frog", FROG,
        "pig", PIG,
        "rabbit", RABBIT,
        "wolf", WOLF
    );

    private VariantLabelSets() {
    }

    /**
     * Returns the immutable canonical labels for a species profile id.
     *
     * @param species lowercase profile id
     * @return immutable labels
     * @throws IllegalArgumentException when no variant family exists
     */
    public static Set<String> labelsFor(final String species) {
        final Set<String> labels = BY_SPECIES.get(species);
        if (labels == null) {
            throw new IllegalArgumentException("Unknown variant species: " + species);
        }
        return labels;
    }
}
