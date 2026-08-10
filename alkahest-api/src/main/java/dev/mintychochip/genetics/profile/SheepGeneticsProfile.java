package dev.mintychochip.genetics.profile;

import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.dna.MutationSettings;
import dev.mintychochip.genetics.engine.RecombinationSettings;
import dev.mintychochip.genetics.model.Allele;
import dev.mintychochip.genetics.model.DominanceMode;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.GenomeGenerator;
import dev.mintychochip.genetics.model.LocusCatalog;
import dev.mintychochip.genetics.model.LocusDefinition;
import dev.mintychochip.genetics.model.Sex;
import java.util.List;
import java.util.random.RandomGenerator;

public final class SheepGeneticsProfile implements GeneticsProfile {

    public static final LocusDefinition BASE = LocusDefinition.autosomal(
        "sheep.base",
        1,
        1,
        DominanceMode.COMPLETE
    );
    public static final LocusDefinition DILUTION = LocusDefinition.autosomal(
        "sheep.dilution",
        2,
        1,
        DominanceMode.COMPLETE
    );
    public static final LocusDefinition ALBINISM = LocusDefinition.autosomal(
        "sheep.albinism",
        3,
        1,
        DominanceMode.COMPLETE
    );
    public static final String COLOR_KEY = "sheep.color";
    static final List<String> BASE_ORDER = List.of(
        "BLACK", "BROWN", "RED", "YELLOW",
        "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE",
        "LIME", "PINK", "GRAY", "LIGHT_GRAY",
        "CYAN", "PURPLE", "BLUE", "GREEN"
    );

    public static final SheepGeneticsProfile INSTANCE = new SheepGeneticsProfile();

    private static final LocusCatalog CATALOG = LocusCatalog.of(List.of(BASE, DILUTION, ALBINISM));
    private final SheepPhenotypeDecoder phenotype = new SheepPhenotypeDecoder();

    private SheepGeneticsProfile() {
    }

    @Override
    public String id() {
        return "sheep";
    }

    @Override
    public LocusCatalog catalog() {
        return CATALOG;
    }

    @Override
    public MutationSettings mutation() {
        return MutationSettings.NONE;
    }

    @Override
    public RecombinationSettings recombination() {
        return RecombinationSettings.DEFAULT;
    }

    @Override
    public Genome founder(final Sex sex, final RandomGenerator random) {
        return new GenomeGenerator(CATALOG, random, SheepGeneticsProfile::founderAllele).generate(sex);
    }

    @Override
    public PhenotypeSnapshot phenotype(final Genome genome) {
        return this.phenotype.decode(genome);
    }

    private static Allele founderAllele(final LocusDefinition locus, final RandomGenerator random) {
        final String label = switch (locus.id().key()) {
            case "sheep.base" -> BASE_ORDER.get(random.nextInt(BASE_ORDER.size()));
            case "sheep.dilution" -> random.nextDouble() < 0.75D ? "FULL" : "DILUTE";
            case "sheep.albinism" -> random.nextDouble() < 0.90D ? "PIGMENTED" : "ALBINO";
            default -> throw new IllegalArgumentException("Unknown sheep locus: " + locus.id());
        };
        return Allele.of("ATGAAACCC", label);
    }
}
