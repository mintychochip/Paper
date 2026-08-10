package dev.mintychochip.genetics.profile;

import java.util.List;
import java.util.Map;

/**
 * Canonical labels emitted by the vanilla breedable variant profiles.
 *
 * <p>Labels intentionally match Bukkit enum names or registry paths after
 * lower-casing, so server adapters can apply every emitted value. List order is
 * canonical because variant phenotype resolution uses declared dominance order.</p>
 */
public final class VariantLabelSets {

    public static final List<String> AXOLOTL = List.of("LUCY", "WILD", "GOLD", "CYAN", "BLUE");
    public static final List<String> CAT = List.of(
        "ALL_BLACK", "BLACK", "BRITISH_SHORTHAIR", "CALICO", "JELLIE", "PERSIAN",
        "RAGDOLL", "RED", "SIAMESE", "TABBY", "WHITE"
    );
    public static final List<String> CHICKEN = List.of("COLD", "TEMPERATE", "WARM");
    public static final List<String> COW = List.of("COLD", "TEMPERATE", "WARM");
    public static final List<String> MOOSHROOM = List.of("RED", "BROWN");
    public static final List<String> FOX = List.of("RED", "SNOW");
    public static final List<String> FROG = List.of("COLD", "TEMPERATE", "WARM");
    public static final List<String> PIG = List.of("COLD", "TEMPERATE", "WARM");
    public static final List<String> RABBIT = List.of(
        "BROWN", "WHITE", "BLACK", "BLACK_AND_WHITE", "GOLD", "SALT_AND_PEPPER", "THE_KILLER_BUNNY"
    );
    public static final List<String> WOLF = List.of(
        "ASHEN", "BLACK", "CHESTNUT", "PALE", "RUSTY", "SNOWY", "SPOTTED", "STRIPED", "WOODS"
    );

    private static final Map<String, List<String>> BY_SPECIES = Map.of(
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
     * @return immutable labels in canonical dominance order
     * @throws IllegalArgumentException when no variant family exists
     */
    public static List<String> labelsFor(final String species) {
        final List<String> labels = BY_SPECIES.get(species);
        if (labels == null) {
            throw new IllegalArgumentException("Unknown variant species: " + species);
        }
        return labels;
    }
}
