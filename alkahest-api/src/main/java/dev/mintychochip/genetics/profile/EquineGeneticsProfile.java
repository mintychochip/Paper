package dev.mintychochip.genetics.profile;

import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.dto.PhenotypeTrait;
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
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.bukkit.entity.EntityType;

/** Shared horse, donkey, and mule quantitative family profile. */
public final class EquineGeneticsProfile implements GeneticsProfile {

    public static final LocusDefinition COLOR = LocusDefinition.autosomal("equine.color", 1, 1, DominanceMode.COMPLETE);
    public static final LocusDefinition MARKINGS = LocusDefinition.autosomal("equine.markings", 1, 2, DominanceMode.COMPLETE);
    public static final LocusDefinition SPEED = LocusDefinition.autosomal("equine.speed", 2, 1, DominanceMode.COMPLETE);
    public static final LocusDefinition JUMP = LocusDefinition.autosomal("equine.jump", 2, 2, DominanceMode.COMPLETE);
    public static final LocusDefinition HEALTH = LocusDefinition.autosomal("equine.health", 2, 3, DominanceMode.COMPLETE);

    public static final double MIN_SPEED = 0.1125D;
    public static final double MAX_SPEED = 0.3375D;
    public static final double MIN_JUMP = 0.4D;
    public static final double MAX_JUMP = 1.0D;
    public static final double MIN_HEALTH = 15.0D;
    public static final double MAX_HEALTH = 30.0D;

    public static final List<String> COLORS = List.of("WHITE", "CREAMY", "CHESTNUT", "BROWN", "BLACK", "GRAY", "DARK_BROWN");
    public static final List<String> MARKING_LABELS = List.of("NONE", "WHITE", "WHITE_FIELD", "WHITE_DOTS", "BLACK_DOTS");
    public static final LocusCatalog CATALOG = LocusCatalog.of(List.of(COLOR, MARKINGS, SPEED, JUMP, HEALTH));

    public static final EquineGeneticsProfile FAMILY = new EquineGeneticsProfile("equine");
    public static final EquineGeneticsProfile INSTANCE = new EquineGeneticsProfile("horse");
    public static final EquineGeneticsProfile HORSE = INSTANCE;
    public static final EquineGeneticsProfile DONKEY = new EquineGeneticsProfile("donkey");
    public static final EquineGeneticsProfile MULE = new EquineGeneticsProfile("mule");

    private final String id;

    private EquineGeneticsProfile(final String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public static EquineGeneticsProfile forEntityType(final EntityType entityType) {
        final EntityType type = Objects.requireNonNull(entityType, "entityType");
        if (type == EntityType.HORSE) {
            return HORSE;
        }
        if (type == EntityType.DONKEY) {
            return DONKEY;
        }
        if (type == EntityType.MULE) {
            return MULE;
        }
        throw new IllegalArgumentException("Not an equine entity type: " + type);
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
        return new GenomeGenerator(CATALOG, random, (locus, source) -> founderAllele(locus, source)).generate(sex);
    }

    @Override
    public PhenotypeSnapshot phenotype(final Genome genome) {
        return new PhenotypeSnapshot(List.of(
            trait(genome, COLOR),
            trait(genome, MARKINGS),
            trait(genome, SPEED),
            trait(genome, JUMP),
            trait(genome, HEALTH)
        ));
    }

    @Override
    public Optional<Genome> breed(
        final Genome parentA,
        final Genome parentB,
        final RandomGenerator random,
        final BreedContext context
    ) {
        final Optional<Genome> base = GeneticsProfile.super.breed(parentA, parentB, random, context);
        if (base.isEmpty()) {
            return base;
        }
        final Genome inherited = base.orElseThrow();
        final Genome.Builder child = Genome.builder(inherited.sex());
        for (final LocusDefinition locus : CATALOG.all()) {
            if (isNumeric(locus)) {
                final double value = createOffspringAttribute(
                    numeric(parentA, locus), numeric(parentB, locus), min(locus), max(locus), random
                );
                final Allele allele = Allele.of("ATGAAACCC", Double.toString(value));
                child.put(locus, GeneCopy.diploid(allele, allele));
            } else {
                child.put(locus, inherited.get(locus.id()).orElseThrow(() ->
                    new IllegalArgumentException("Missing inherited equine locus: " + locus.id())));
            }
        }
        return Optional.of(child.build());
    }

    /** Exact NMS-free copy of vanilla AbstractHorse#createOffspringAttribute. */
    public static double createOffspringAttribute(
        double parentAValue,
        double parentBValue,
        final double attributeRangeMin,
        final double attributeRangeMax,
        final RandomGenerator random
    ) {
        Objects.requireNonNull(random, "random");
        if (attributeRangeMax <= attributeRangeMin) {
            throw new IllegalArgumentException("Incorrect range for an attribute");
        }
        parentAValue = clamp(parentAValue, attributeRangeMin, attributeRangeMax);
        parentBValue = clamp(parentBValue, attributeRangeMin, attributeRangeMax);
        final double margin = 0.15D * (attributeRangeMax - attributeRangeMin);
        final double range = Math.abs(parentAValue - parentBValue) + margin * 2.0D;
        final double average = (parentAValue + parentBValue) / 2.0D;
        final double babyQuality = (random.nextDouble() + random.nextDouble() + random.nextDouble()) / 3.0D - 0.5D;
        final double newValue = average + range * babyQuality;
        if (newValue > attributeRangeMax) {
            return attributeRangeMax - (newValue - attributeRangeMax);
        }
        if (newValue < attributeRangeMin) {
            return attributeRangeMin + (attributeRangeMin - newValue);
        }
        return newValue;
    }

    private static boolean isNumeric(final LocusDefinition locus) {
        return locus == SPEED || locus == JUMP || locus == HEALTH;
    }

    private static double min(final LocusDefinition locus) {
        return locus == SPEED ? MIN_SPEED : locus == JUMP ? MIN_JUMP : MIN_HEALTH;
    }

    private static double max(final LocusDefinition locus) {
        return locus == SPEED ? MAX_SPEED : locus == JUMP ? MAX_JUMP : MAX_HEALTH;
    }

    private static double numeric(final Genome genome, final LocusDefinition locus) {
        final GeneCopy copy = genome.get(locus.id()).orElseThrow(() ->
            new IllegalArgumentException("Missing equine numeric locus: " + locus.id()));
        try {
            return Double.parseDouble(copy.alleleA().label());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Equine numeric allele must be a number: " + locus.id(), ex);
        }
    }

    private static PhenotypeTrait trait(final Genome genome, final LocusDefinition locus) {
        final GeneCopy copy = genome.get(locus.id()).orElseThrow(() ->
            new IllegalArgumentException("Missing equine locus: " + locus.id()));
        final String label = copy.alleleA().label();
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Equine alleles require semantic labels: " + locus.id());
        }
        return new PhenotypeTrait(locus.phenotypeKey(), label);
    }

    private static Allele founderAllele(final LocusDefinition locus, final RandomGenerator random) {
        final String label;
        if (locus == COLOR) {
            label = COLORS.get(random.nextInt(COLORS.size()));
        } else if (locus == MARKINGS) {
            label = MARKING_LABELS.get(random.nextInt(MARKING_LABELS.size()));
        } else if (locus == SPEED) {
            label = Double.toString(MIN_SPEED + random.nextDouble() * (MAX_SPEED - MIN_SPEED));
        } else if (locus == JUMP) {
            label = Double.toString(MIN_JUMP + random.nextDouble() * (MAX_JUMP - MIN_JUMP));
        } else if (locus == HEALTH) {
            label = Double.toString(MIN_HEALTH + random.nextDouble() * (MAX_HEALTH - MIN_HEALTH));
        } else {
            throw new IllegalArgumentException("Unknown equine locus: " + locus.id());
        }
        return Allele.of("ATGAAACCC", label);
    }

    private static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }
}
