package dev.mintychochip.genetics;

import dev.mintychochip.genetics.model.Allele;
import dev.mintychochip.genetics.model.GeneCopy;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.LocusDefinition;
import dev.mintychochip.genetics.model.Sex;
import dev.mintychochip.genetics.profile.GeneticsProfile;
import dev.mintychochip.genetics.profile.SheepGeneticsProfile;
import dev.mintychochip.genetics.profile.LlamaGeneticsProfile;
import dev.mintychochip.genetics.profile.PandaGeneticsProfile;
import dev.mintychochip.genetics.profile.VillagerGeneticsProfile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import org.bukkit.craftbukkit.entity.CraftEntityType;
import org.bukkit.entity.EntityType;

public final class FounderCaptures {
    private static final String SEQUENCE = "ATGAAACCC";
    private static final Map<EntityType, FounderCapture> ADAPTERS = adapters();

    private FounderCaptures() {
    }

    public static Genome capture(final AgeableMob entity, final GeneticsProfile profile,
                                  final Sex sex, final RandomGenerator random) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(sex, "sex");
        Objects.requireNonNull(random, "random");
        final EntityType type = CraftEntityType.minecraftToBukkit(entity.getType());
        final FounderCapture adapter = ADAPTERS.get(type);
        return adapter == null ? profile.founder(sex, random) : adapter.capture(entity, profile, sex, random);
    }

    private static Map<EntityType, FounderCapture> adapters() {
        final Map<EntityType, FounderCapture> map = new HashMap<>();
        map.put(EntityType.AXOLOTL, FounderCaptures::captureAxolotl);
        map.put(EntityType.SHEEP, FounderCaptures::captureSheep);
        map.put(EntityType.HORSE, FounderCaptures::captureHorse);
        map.put(EntityType.PANDA, FounderCaptures::capturePanda);
        map.put(EntityType.LLAMA, FounderCaptures::captureLlama);
        map.put(EntityType.VILLAGER, FounderCaptures::captureVillager);
        for (final EntityType type : new EntityType[] {
            EntityType.CAT, EntityType.CHICKEN, EntityType.COW, EntityType.MOOSHROOM,
            EntityType.FOX, EntityType.FROG, EntityType.PIG, EntityType.RABBIT, EntityType.WOLF
        }) {
            map.put(type, FounderCaptures::captureVariant);
        }
        return Map.copyOf(map);
    }

    private static Genome captureAxolotl(final AgeableMob entity, final GeneticsProfile profile,
                                         final Sex sex, final RandomGenerator random) {
        if (!(entity instanceof Axolotl axolotl)) {
            return profile.founder(sex, random);
        }
        return single(profile, sex, axolotl.getVariant().name());
    }

    private static Genome captureSheep(final AgeableMob entity, final GeneticsProfile profile,
                                       final Sex sex, final RandomGenerator random) {
        if (!(entity instanceof Sheep sheep)) {
            return profile.founder(sex, random);
        }
        final DyeColor color = sheep.getColor();
        final Map<String, String> labels = new HashMap<>();
        switch (color) {
            case BLACK, BROWN, RED, YELLOW -> {
                labels.put(SheepGeneticsProfile.BASE.id().key(), color.name());
                labels.put(SheepGeneticsProfile.DILUTION.id().key(), "FULL");
                labels.put(SheepGeneticsProfile.ALBINISM.id().key(), "PIGMENTED");
            }
            case GRAY, LIGHT_GRAY, PINK, ORANGE -> {
                final String base = color == DyeColor.GRAY ? "BLACK"
                    : color == DyeColor.LIGHT_GRAY ? "BROWN"
                    : color == DyeColor.PINK ? "RED" : "YELLOW";
                labels.put(SheepGeneticsProfile.BASE.id().key(), base);
                labels.put(SheepGeneticsProfile.DILUTION.id().key(), "DILUTE");
                labels.put(SheepGeneticsProfile.ALBINISM.id().key(), "PIGMENTED");
            }
            default -> {
                labels.put(SheepGeneticsProfile.BASE.id().key(), color.name());
                labels.put(SheepGeneticsProfile.DILUTION.id().key(), "FULL");
                labels.put(SheepGeneticsProfile.ALBINISM.id().key(), "PIGMENTED");
            }
        }
        return fromLabels(profile, sex, labels);
    }

    private static Genome captureHorse(final AgeableMob entity, final GeneticsProfile profile,
                                       final Sex sex, final RandomGenerator random) {
        if (!(entity instanceof Horse horse)
            || horse.getAttribute(Attributes.MOVEMENT_SPEED) == null
            || horse.getAttribute(Attributes.JUMP_STRENGTH) == null
            || horse.getAttribute(Attributes.MAX_HEALTH) == null) {
            return profile.founder(sex, random);
        }
        final Map<String, String> labels = new HashMap<>();
        labels.put("equine.color", horse.getVariant().name());
        labels.put("equine.markings", horse.getMarkings().name());
        labels.put("equine.speed", Double.toString(horse.getAttribute(Attributes.MOVEMENT_SPEED).getValue()));
        labels.put("equine.jump", Double.toString(horse.getAttribute(Attributes.JUMP_STRENGTH).getValue()));
        labels.put("equine.health", Double.toString(horse.getAttribute(Attributes.MAX_HEALTH).getValue()));
        return fromLabels(profile, sex, labels);
    }

    private static Genome captureVariant(final AgeableMob entity, final GeneticsProfile profile,
                                         final Sex sex, final RandomGenerator random) {
        try {
            final Object variant = invoke(entity, "getVariant");
            return single(profile, sex, semanticLabel(variant));
        } catch (final RuntimeException ex) {
            return profile.founder(sex, random);
        }
    }

    private static Genome capturePanda(final AgeableMob entity, final GeneticsProfile profile,
                                       final Sex sex, final RandomGenerator random) {
        try {
            return fromLabels(profile, sex, Map.of(
                PandaGeneticsProfile.MAIN.id().key(), semanticLabel(invoke(entity, "getMainGene")),
                PandaGeneticsProfile.HIDDEN.id().key(), semanticLabel(invoke(entity, "getHiddenGene"))
            ));
        } catch (final RuntimeException ex) {
            return profile.founder(sex, random);
        }
    }

    private static Genome captureLlama(final AgeableMob entity, final GeneticsProfile profile,
                                       final Sex sex, final RandomGenerator random) {
        try {
            final int strength = ((Number) invoke(entity, "getStrength")).intValue();
            if (strength < LlamaGeneticsProfile.MIN_STRENGTH || strength > LlamaGeneticsProfile.MAX_STRENGTH) {
                return profile.founder(sex, random);
            }
            return fromLabels(profile, sex, Map.of(
                LlamaGeneticsProfile.COLOR.id().key(), semanticLabel(invoke(entity, "getVariant")),
                LlamaGeneticsProfile.STRENGTH.id().key(), Integer.toString(strength)
            ));
        } catch (final RuntimeException ex) {
            return profile.founder(sex, random);
        }
    }

    private static Genome captureVillager(final AgeableMob entity, final GeneticsProfile profile,
                                          final Sex sex, final RandomGenerator random) {
        try {
            final Object data = invoke(entity, "getVillagerData");
            final Object type = invoke(data, "type");
            return single(profile, sex, semanticLabel(type));
        } catch (final RuntimeException ex) {
            return profile.founder(sex, random);
        }
    }

    private static Object invoke(final Object target, final String name) {
        try {
            final Method method = target.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (final ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Unable to read " + name, ex);
        }
    }

    private static String semanticLabel(final Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Missing variant");
        }
        Object unwrapped = value;
        try {
            unwrapped = invoke(value, "value");
        } catch (final RuntimeException ignored) {
            // Plain enums/values do not have Holder.value().
        }
        try {
            return String.valueOf(invoke(unwrapped, "getSerializedName")).toUpperCase(java.util.Locale.ROOT);
        } catch (final RuntimeException ignored) {
            if (unwrapped instanceof Enum<?> enumeration) {
                return enumeration.name();
            }
            return unwrapped.toString().toUpperCase(java.util.Locale.ROOT);
        }
    }

    private static Genome single(final GeneticsProfile profile, final Sex sex, final String label) {
        final LocusDefinition locus = profile.catalog().all().iterator().next();
        return fromLabels(profile, sex, Map.of(locus.id().key(), label));
    }

    private static Genome fromLabels(final GeneticsProfile profile, final Sex sex,
                                     final Map<String, String> labels) {
        final Genome.Builder builder = Genome.builder(sex);
        for (final LocusDefinition locus : profile.catalog().all()) {
            final String label = labels.get(locus.id().key());
            if (label == null) {
                throw new IllegalArgumentException("Missing captured locus: " + locus.id());
            }
            final Allele allele = Allele.of(SEQUENCE, label);
            builder.put(locus, GeneCopy.diploid(allele, allele));
        }
        return builder.build();
    }
}
