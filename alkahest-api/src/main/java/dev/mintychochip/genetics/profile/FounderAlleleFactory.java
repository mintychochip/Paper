package dev.mintychochip.genetics.profile;

import dev.mintychochip.genetics.model.Allele;
import dev.mintychochip.genetics.model.LocusDefinition;
import java.util.random.RandomGenerator;

@FunctionalInterface
public interface FounderAlleleFactory {

    Allele create(LocusDefinition locus, RandomGenerator random);
}
