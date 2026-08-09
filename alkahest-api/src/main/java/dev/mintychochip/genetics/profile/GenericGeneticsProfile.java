package dev.mintychochip.genetics.profile;

import dev.mintychochip.genetics.catalog.DefaultGeneticsCatalog;
import dev.mintychochip.genetics.dto.PhenotypeDecoder;
import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.dna.MutationSettings;
import dev.mintychochip.genetics.engine.RecombinationSettings;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.GenomeGenerator;
import dev.mintychochip.genetics.model.LocusCatalog;
import dev.mintychochip.genetics.model.Sex;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class GenericGeneticsProfile implements GeneticsProfile {

    public static final GenericGeneticsProfile INSTANCE = new GenericGeneticsProfile();

    private final LocusCatalog catalog = DefaultGeneticsCatalog.get();
    private final PhenotypeDecoder phenotype = new PhenotypeDecoder(this.catalog);

    private GenericGeneticsProfile() {
    }

    @Override
    public String id() {
        return "generic";
    }

    @Override
    public LocusCatalog catalog() {
        return this.catalog;
    }

    @Override
    public MutationSettings mutation() {
        return MutationSettings.DEFAULT;
    }

    @Override
    public RecombinationSettings recombination() {
        return RecombinationSettings.DEFAULT;
    }

    @Override
    public Genome founder(final Sex sex, final RandomGenerator random) {
        return new GenomeGenerator(this.catalog, Objects.requireNonNull(random, "random")).generate(sex);
    }

    @Override
    public PhenotypeSnapshot phenotype(final Genome genome) {
        return this.phenotype.decode(genome);
    }
}
