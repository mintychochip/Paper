package dev.mintychochip.genetics.model;

import dev.mintychochip.genetics.dna.DnaSequence;
import dev.mintychochip.genetics.profile.FounderAlleleFactory;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Builds a random founder genome for a catalog (wild spawn / first attach).
 */
public final class GenomeGenerator {

    private final LocusCatalog catalog;
    private final RandomGenerator random;
    private final FounderAlleleFactory founderAlleles;

    public GenomeGenerator(final LocusCatalog catalog, final RandomGenerator random) {
        this(catalog, random, 3);
    }

    public GenomeGenerator(
        final LocusCatalog catalog,
        final RandomGenerator random,
        final int codonCount
    ) {
        this(
            catalog,
            random,
            (locus, source) -> defaultFounderAllele(locus, source, codonCount),
            codonCount
        );
    }

    public GenomeGenerator(
        final LocusCatalog catalog,
        final RandomGenerator random,
        final FounderAlleleFactory founderAlleles
    ) {
        this(catalog, random, founderAlleles, 3);
    }

    private GenomeGenerator(
        final LocusCatalog catalog,
        final RandomGenerator random,
        final FounderAlleleFactory founderAlleles,
        final int codonCount
    ) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.random = Objects.requireNonNull(random, "random");
        this.founderAlleles = Objects.requireNonNull(founderAlleles, "founderAlleles");
        if (codonCount < 1) {
            throw new IllegalArgumentException("codonCount must be >= 1");
        }
    }

    public Genome generate(final Sex sex) {
        final Genome.Builder builder = Genome.builder(sex);
        for (final LocusDefinition locus : this.catalog.all()) {
            final GeneCopy copy = switch (locus.inheritance()) {
                case X_LINKED -> sex == Sex.MALE
                    ? GeneCopy.hemizygous(this.randomAllele(locus))
                    : GeneCopy.diploid(this.randomAllele(locus), this.randomAllele(locus));
                case Y_LINKED -> sex == Sex.MALE
                    ? GeneCopy.hemizygous(this.randomAllele(locus))
                    : null;
                case MATERNAL, AUTOSOMAL -> GeneCopy.diploid(this.randomAllele(locus), this.randomAllele(locus));
            };
            if (copy != null) {
                builder.put(locus.id(), copy);
            }
        }
        return builder.build();
    }

    private Allele randomAllele(final LocusDefinition locus) {
        return this.founderAlleles.create(locus, this.random);
    }

    private static Allele defaultFounderAllele(
        final LocusDefinition locus,
        final RandomGenerator random,
        final int codonCount
    ) {
        // Coat-style codominant X locus: 50/50 O vs o labels for discovery play.
        if (locus.dominance() == DominanceMode.CODOMINANT
            && locus.inheritance() == InheritanceMode.X_LINKED) {
            if (random.nextBoolean()) {
                return Allele.of(DnaSequence.functionalOrf(random, codonCount), "O");
            }
            return Allele.of(DnaSequence.functionalOrf(random, codonCount), "o");
        }
        return Allele.of(DnaSequence.functionalOrf(random, codonCount));
    }
}
