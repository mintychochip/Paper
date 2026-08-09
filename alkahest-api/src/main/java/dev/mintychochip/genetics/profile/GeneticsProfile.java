package dev.mintychochip.genetics.profile;

import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.dna.MutationSettings;
import dev.mintychochip.genetics.engine.RecombinationSettings;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.LocusCatalog;
import dev.mintychochip.genetics.model.Sex;
import java.util.random.RandomGenerator;

public interface GeneticsProfile {

    String id();

    LocusCatalog catalog();

    MutationSettings mutation();

    RecombinationSettings recombination();

    Genome founder(Sex sex, RandomGenerator random);

    PhenotypeSnapshot phenotype(Genome genome);
}
