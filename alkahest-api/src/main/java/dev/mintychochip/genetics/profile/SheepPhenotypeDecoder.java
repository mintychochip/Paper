package dev.mintychochip.genetics.profile;

import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.dto.PhenotypeTrait;
import dev.mintychochip.genetics.model.Allele;
import dev.mintychochip.genetics.model.GeneCopy;
import dev.mintychochip.genetics.model.Genome;
import java.util.List;

final class SheepPhenotypeDecoder {

    PhenotypeSnapshot decode(final Genome genome) {
        final GeneCopy baseCopy = requireCopy(genome, SheepGeneticsProfile.BASE);
        final GeneCopy dilutionCopy = requireCopy(genome, SheepGeneticsProfile.DILUTION);
        final GeneCopy albinismCopy = requireCopy(genome, SheepGeneticsProfile.ALBINISM);

        final String base = resolveBase(baseCopy);
        final String dilution = resolveRecessive(dilutionCopy, "DILUTE", "FULL");
        final String albinism = resolveRecessive(albinismCopy, "ALBINO", "PIGMENTED");
        final String color = resolveColor(base, dilution, albinism);

        return new PhenotypeSnapshot(List.of(
            new PhenotypeTrait(SheepGeneticsProfile.BASE.phenotypeKey(), base),
            new PhenotypeTrait(SheepGeneticsProfile.DILUTION.phenotypeKey(), dilution),
            new PhenotypeTrait(SheepGeneticsProfile.ALBINISM.phenotypeKey(), albinism),
            new PhenotypeTrait(SheepGeneticsProfile.COLOR_KEY, color)
        ));
    }

    private static GeneCopy requireCopy(final Genome genome, final dev.mintychochip.genetics.model.LocusDefinition locus) {
        return genome.get(locus.id()).orElseThrow(() -> new IllegalArgumentException(
            "Missing sheep locus: " + locus.id()
        ));
    }

    private static String resolveBase(final GeneCopy copy) {
        final String a = key(copy.alleleA());
        final String b = key(copy.alleleB());
        for (final String candidate : SheepGeneticsProfile.BASE_ORDER) {
            if (candidate.equals(a) || candidate.equals(b)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown sheep base allele: " + a + "/" + b);
    }

    private static String resolveRecessive(
        final GeneCopy copy,
        final String recessive,
        final String dominant
    ) {
        final String a = key(copy.alleleA());
        final String b = key(copy.alleleB());
        if ((!a.equals(recessive) && !a.equals(dominant)) || (!b.equals(recessive) && !b.equals(dominant))) {
            throw new IllegalArgumentException("Unknown sheep allele pair: " + a + "/" + b);
        }
        return a.equals(recessive) && b.equals(recessive) ? recessive : dominant;
    }

    private static String resolveColor(final String base, final String dilution, final String albinism) {
        if (albinism.equals("ALBINO")) {
            return "WHITE";
        }
        if (!dilution.equals("DILUTE")) {
            return base;
        }
        return switch (base) {
            case "BLACK" -> "GRAY";
            case "BROWN" -> "LIGHT_GRAY";
            case "RED" -> "PINK";
            case "YELLOW" -> "ORANGE";
            default -> base;
        };
    }

    private static String key(final Allele allele) {
        final String label = allele.label();
        if (label == null || label.isEmpty()) {
            throw new IllegalArgumentException("Sheep alleles require semantic labels");
        }
        return label;
    }
}
