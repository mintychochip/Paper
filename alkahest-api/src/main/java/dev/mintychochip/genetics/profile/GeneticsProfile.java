package dev.mintychochip.genetics.profile;

import dev.mintychochip.genetics.engine.BreedingEngine;
import dev.mintychochip.genetics.engine.BreedingResult;
import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.dna.MutationSettings;
import dev.mintychochip.genetics.engine.RecombinationSettings;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.LocusCatalog;
import dev.mintychochip.genetics.model.Sex;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

public interface GeneticsProfile {

    String id();

    LocusCatalog catalog();

    MutationSettings mutation();

    RecombinationSettings recombination();

    Genome founder(Sex sex, RandomGenerator random);

    PhenotypeSnapshot phenotype(Genome genome);

    default Optional<Genome> breed(
        final Genome parentA,
        final Genome parentB,
        final RandomGenerator random,
        final BreedContext context
    ) {
        Objects.requireNonNull(context, "context");
        return new BreedingEngine(
            this.catalog(),
            this.recombination(),
            this.mutation(),
            Objects.requireNonNull(random, "random")
        ).cross(parentA, parentB).map(BreedingResult::child);
    }
}
