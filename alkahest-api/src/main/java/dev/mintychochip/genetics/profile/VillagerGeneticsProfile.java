package dev.mintychochip.genetics.profile;

import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.dto.PhenotypeTrait;
import dev.mintychochip.genetics.dna.DnaSequence;
import dev.mintychochip.genetics.dna.MutationSettings;
import dev.mintychochip.genetics.engine.RecombinationSettings;
import dev.mintychochip.genetics.model.Allele;
import dev.mintychochip.genetics.model.DominanceMode;
import dev.mintychochip.genetics.model.GeneCopy;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.GenomeGenerator;
import dev.mintychochip.genetics.model.LocusCatalog;
import dev.mintychochip.genetics.model.LocusDefinition;
import dev.mintychochip.genetics.model.Sex;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Villager type inheritance; profession remains vanilla and is not encoded. */
public final class VillagerGeneticsProfile implements GeneticsProfile {

    public static final LocusDefinition TYPE = LocusDefinition.autosomal("villager.type", 1, 1, DominanceMode.COMPLETE);
    public static final List<String> TYPES = List.of("DESERT", "JUNGLE", "PLAINS", "SAVANNA", "SNOW", "SWAMP", "TAIGA");
    public static final LocusCatalog CATALOG = LocusCatalog.of(List.of(TYPE));
    public static final VillagerGeneticsProfile INSTANCE = new VillagerGeneticsProfile();

    private VillagerGeneticsProfile() {
    }

    public LocusDefinition typeLocus() {
        return TYPE;
    }

    @Override
    public String id() {
        return "villager";
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
        return new GenomeGenerator(CATALOG, random, VillagerGeneticsProfile::founderAllele).generate(sex);
    }

    @Override
    public PhenotypeSnapshot phenotype(final Genome genome) {
        final GeneCopy copy = genome.get(TYPE.id()).orElseThrow(() -> new IllegalArgumentException("Missing villager.type locus"));
        final String type = requireType(copy.alleleA().label());
        return new PhenotypeSnapshot(List.of(new PhenotypeTrait(TYPE.phenotypeKey(), type)));
    }

    @Override
    public Optional<Genome> breed(
        final Genome parentA,
        final Genome parentB,
        final RandomGenerator random,
        final BreedContext context
    ) {
        final double roll = random.nextDouble();
        final String selected;
        if (roll < 0.50D) {
            selected = context.environmentalVariant().map(VillagerGeneticsProfile::requireType)
                .orElseThrow(() -> new IllegalArgumentException("Villager breeding requires environmental type"));
        } else if (roll < 0.75D) {
            selected = parentType(parentA);
        } else {
            selected = parentType(parentB);
        }
        final Allele allele = Allele.of(DnaSequence.of("ATGAAACCC"), selected);
        return Optional.of(Genome.builder(Sex.FEMALE).put(TYPE, GeneCopy.diploid(allele, allele)).build());
    }

    private static Allele founderAllele(final LocusDefinition locus, final RandomGenerator random) {
        return Allele.of(DnaSequence.functionalOrf(random, 3), TYPES.get(random.nextInt(TYPES.size())));
    }

    private static String parentType(final Genome parent) {
        final GeneCopy copy = parent.get(TYPE.id()).orElseThrow(() -> new IllegalArgumentException("Missing villager.type locus"));
        return requireType(copy.alleleA().label());
    }
    private static String requireType(final String value) {
        final String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || !TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unknown villager type: " + value);
        }
        return normalized;
    }
}
