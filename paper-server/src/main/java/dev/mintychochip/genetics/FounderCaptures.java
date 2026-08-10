package dev.mintychochip.genetics;

import dev.mintychochip.genetics.model.Allele;
import dev.mintychochip.genetics.model.GeneCopy;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.LocusDefinition;
import dev.mintychochip.genetics.model.Sex;
import dev.mintychochip.genetics.profile.GeneticsProfile;
import dev.mintychochip.genetics.profile.EquineGeneticsProfile;
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
import org.bukkit.entity.Cat;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Frog;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;

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
        map.put(EntityType.HORSE, FounderCaptures::captureEquine);
        map.put(EntityType.DONKEY, FounderCaptures::captureEquine);
        map.put(EntityType.MULE, FounderCaptures::captureEquine);
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

    private static Genome captureEquine(final AgeableMob entity, final GeneticsProfile profile,
                                        final Sex sex, final RandomGenerator random) {
        final net.minecraft.world.entity.ai.attributes.AttributeInstance speed =
            entity.getAttribute(Attributes.MOVEMENT_SPEED);
        final net.minecraft.world.entity.ai.attributes.AttributeInstance jump =
            entity.getAttribute(Attributes.JUMP_STRENGTH);
        final net.minecraft.world.entity.ai.attributes.AttributeInstance health =
            entity.getAttribute(Attributes.MAX_HEALTH);
        if (speed == null || jump == null || health == null) {
            return profile.founder(sex, random);
        }
        final double speedValue = speed.getValue();
        final double jumpValue = jump.getValue();
        final double healthValue = health.getValue();
        if (!validRange(speedValue, EquineGeneticsProfile.MIN_SPEED, EquineGeneticsProfile.MAX_SPEED)
            || !validRange(jumpValue, EquineGeneticsProfile.MIN_JUMP, EquineGeneticsProfile.MAX_JUMP)
            || !validRange(healthValue, EquineGeneticsProfile.MIN_HEALTH, EquineGeneticsProfile.MAX_HEALTH)) {
            return profile.founder(sex, random);
        }
        final String color = entity instanceof Horse horse
            ? horse.getVariant().name() : EquineGeneticsProfile.COLORS.get(0);
        final String markings = entity instanceof Horse horse
            ? horse.getMarkings().name() : EquineGeneticsProfile.MARKING_LABELS.get(0);
        return fromLabels(profile, sex, Map.of(
            EquineGeneticsProfile.COLOR.id().key(), color,
            EquineGeneticsProfile.MARKINGS.id().key(), markings,
            EquineGeneticsProfile.SPEED.id().key(), Double.toString(speedValue),
            EquineGeneticsProfile.JUMP.id().key(), Double.toString(jumpValue),
            EquineGeneticsProfile.HEALTH.id().key(), Double.toString(healthValue)
        ));
    }

    private static boolean validRange(final double value, final double min, final double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }

    private static Genome captureVariant(final AgeableMob entity, final GeneticsProfile profile,
                                         final Sex sex, final RandomGenerator random) {
        try {
            final org.bukkit.entity.Entity bukkit = entity.getBukkitEntity();
            if (bukkit == null) {
                return profile.founder(sex, random);
            }
            final String label;
            if (bukkit.getType() == EntityType.CAT) {
                label = labelOf(((Cat) bukkit).getCatType());
            } else if (bukkit.getType() == EntityType.CHICKEN) {
                label = labelOf(((Chicken) bukkit).getVariant());
            } else if (bukkit.getType() == EntityType.COW) {
                label = labelOf(((Cow) bukkit).getVariant());
            } else if (bukkit.getType() == EntityType.MOOSHROOM) {
                label = labelOf(((MushroomCow) bukkit).getVariant());
            } else if (bukkit.getType() == EntityType.FOX) {
                label = labelOf(((Fox) bukkit).getFoxType());
            } else if (bukkit.getType() == EntityType.FROG) {
                label = labelOf(((Frog) bukkit).getVariant());
            } else if (bukkit.getType() == EntityType.PIG) {
                label = labelOf(((Pig) bukkit).getVariant());
            } else if (bukkit.getType() == EntityType.RABBIT) {
                label = labelOf(((Rabbit) bukkit).getRabbitType());
            } else if (bukkit.getType() == EntityType.WOLF) {
                label = labelOf(((Wolf) bukkit).getVariant());
            } else {
                label = null;
            }
            return label == null ? profile.founder(sex, random) : single(profile, sex, label);
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
            final org.bukkit.entity.Entity bukkit = entity.getBukkitEntity();
            if (!(bukkit instanceof Villager villager)) {
                return profile.founder(sex, random);
            }
            return single(profile, sex, labelOf(villager.getVillagerType()));
        } catch (final RuntimeException ex) {
            return profile.founder(sex, random);
        }
    }

    private static String labelOf(final Object value) {
        if (value instanceof org.bukkit.Keyed keyed) {
            return keyed.getKey().getKey().toUpperCase(java.util.Locale.ROOT);
        }
        if (value instanceof Enum<?> enumeration) {
            return enumeration.name();
        }
        throw new IllegalArgumentException("Unsupported registry value: " + value);
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
