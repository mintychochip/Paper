package dev.mintychochip.genetics.profile;

import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.dna.MutationSettings;
import dev.mintychochip.genetics.engine.RecombinationSettings;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.GenomeGenerator;
import dev.mintychochip.genetics.model.LocusCatalog;
import dev.mintychochip.genetics.model.Sex;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Explicit profile for a breedable entity whose vanilla breeding path does not
 * inherit a distinct visible trait. Carries a stable profile ID, an empty locus
 * catalog, valid founder generation, and an empty phenotype snapshot so the
 * entity participates in the shared genome, meiosis, and codec lifecycle.
 */
public final class EmptyGeneticsProfile implements GeneticsProfile {

    private static final LocusCatalog CATALOG = LocusCatalog.of(List.of());

    private final String id;

    private EmptyGeneticsProfile(final String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public static EmptyGeneticsProfile of(final String id) {
        return new EmptyGeneticsProfile(id);
    }

    @Override
    public String id() {
        return this.id;
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
        Objects.requireNonNull(random, "random");
        return new GenomeGenerator(CATALOG, random).generate(sex);
    }

    @Override
    public PhenotypeSnapshot phenotype(final Genome genome) {
        return new PhenotypeSnapshot(List.of());
    }
}