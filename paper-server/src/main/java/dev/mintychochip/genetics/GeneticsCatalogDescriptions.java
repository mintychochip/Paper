package dev.mintychochip.genetics;

import dev.mintychochip.genetics.model.LocusDefinition;
import dev.mintychochip.genetics.profile.EquineGeneticsProfile;
import dev.mintychochip.genetics.profile.GeneticsProfile;
import dev.mintychochip.genetics.profile.LlamaGeneticsProfile;
import dev.mintychochip.genetics.profile.PandaGeneticsProfile;
import dev.mintychochip.genetics.profile.SheepGeneticsProfile;
import dev.mintychochip.genetics.profile.VariantLabelSets;
import dev.mintychochip.genetics.profile.VillagerGeneticsProfile;
import java.util.List;
import java.util.Objects;

/** Optional, truthful display metadata for profile loci. */
final class GeneticsCatalogDescriptions {

    private GeneticsCatalogDescriptions() {
    }

    record Description(List<String> labels, String range) {
        Description {
            labels = List.copyOf(Objects.requireNonNull(labels, "labels"));
        }
    }

    static Description describe(final GeneticsProfile profile, final LocusDefinition locus) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(locus, "locus");
        final String profileId = profile.id();
        final String locusKey = locus.id().key();
        if (isVariantProfile(profileId) && locusKey.equals(profileId + ".variant")) {
            return new Description(VariantLabelSets.labelsFor(profileId), null);
        }
        return switch (locusKey) {
            case "sheep.base" -> new Description(SheepGeneticsProfile.BASE_ORDER, null);
            case "sheep.dilution" -> new Description(SheepGeneticsProfile.DILUTION_LABELS, null);
            case "sheep.albinism" -> new Description(SheepGeneticsProfile.ALBINISM_LABELS, null);
            case "equine.color" -> new Description(EquineGeneticsProfile.COLORS, null);
            case "equine.markings" -> new Description(EquineGeneticsProfile.MARKING_LABELS, null);
            case "equine.speed" -> new Description(List.of(), "0.1125 - 0.3375");
            case "equine.jump" -> new Description(List.of(), "0.4 - 1.0");
            case "equine.health" -> new Description(List.of(), "15.0 - 30.0");
            case "llama.color" -> new Description(LlamaGeneticsProfile.COLORS, null);
            case "llama.strength" -> new Description(List.of(), "1 - 5");
            case "panda.main", "panda.hidden" -> new Description(PandaGeneticsProfile.LABELS, null);
            case "villager.type" -> new Description(VillagerGeneticsProfile.TYPES, null);
            default -> new Description(List.of(), null);
        };
    }

    private static boolean isVariantProfile(final String profileId) {
        return switch (profileId) {
            case "axolotl", "cat", "chicken", "cow", "mooshroom",
                "fox", "frog", "pig", "rabbit", "wolf" -> true;
            default -> false;
        };
    }
}
