package dev.mintychochip.genetics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.DyeColor;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fox;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Cat;
import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.dto.PhenotypeTrait;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

@Normal
class VariantPhenotypeApplierTest {

    @Test
    void applierContainsEveryBreedableVariantBranch() throws Exception {
        final String source = readProjectFile(
            "src/main/java/dev/mintychochip/genetics/PhenotypeApplier.java",
            "paper-server/src/main/java/dev/mintychochip/genetics/PhenotypeApplier.java"
        );

        assertTrue(source.contains("apply(final AgeableMob"), "common AgeableMob overload must exist");
        assertTrue(source.contains("VariantLabelSets."), "variant branches must consume canonical label sets");
        for (final String species : List.of(
            "axolotl", "cat", "chicken", "cow", "mooshroom", "fox", "frog", "pig", "rabbit", "wolf"
        )) {
            assertTrue(source.contains("\"" + species + ".variant\""), species + " variant trait branch must exist");
        }
        assertTrue(source.contains("\"sheep.color\""), "sheep color branch must remain");
        assertTrue(source.contains("\"villager.type\""), "villager type trait branch must exist");
        assertTrue(source.contains("setVillagerType("), "villager type setter branch must exist");
        assertTrue(source.contains("setVariant("), "variant setter branches must exist");
        assertTrue(source.contains("setCatType("), "cat setter branch must exist");
        assertTrue(source.contains("setFoxType("), "fox setter branch must exist");
        assertTrue(source.contains("setRabbitType("), "rabbit setter branch must exist");
        assertTrue(source.contains("setColor("), "sheep setter branch must exist");
    }

    @Test
    void axolotlLabelAppliesAndUnknownLabelIsRejected() {
        final Axolotl axolotl = mock(Axolotl.class);

        assertTrue(PhenotypeApplier.apply(axolotl, EntityType.AXOLOTL, snapshot("axolotl.variant", "LUCY")));
        verify(axolotl).setVariant(Axolotl.Variant.LUCY);

        assertFalse(PhenotypeApplier.apply(axolotl, EntityType.AXOLOTL, snapshot("axolotl.variant", "NOT_A_VARIANT")));
    }
    @Test
    void villagerTypeLabelAppliesAndUnknownLabelIsRejected() {
        final Villager villager = mock(Villager.class);

        assertTrue(PhenotypeApplier.apply(villager, EntityType.VILLAGER, snapshot("villager.type", "PLAINS")));
        verify(villager).setVillagerType(Villager.Type.PLAINS);

        assertFalse(PhenotypeApplier.apply(
            villager, EntityType.VILLAGER, snapshot("villager.type", "NOT_A_TYPE")
        ));
        assertFalse(PhenotypeApplier.apply(villager, EntityType.VILLAGER, new PhenotypeSnapshot(List.of())));
    }


    @Test
    void enumBackedVariantSettersApplyCanonicalLabels() {
        final Fox fox = mock(Fox.class);
        assertTrue(PhenotypeApplier.apply(
            fox, EntityType.FOX, snapshot("fox.variant", "SNOW")
        ));
        verify(fox).setFoxType(Fox.Type.SNOW);

        final MushroomCow mooshroom = mock(MushroomCow.class);
        assertTrue(PhenotypeApplier.apply(
            mooshroom, EntityType.MOOSHROOM, snapshot("mooshroom.variant", "BROWN")
        ));
        verify(mooshroom).setVariant(MushroomCow.Variant.BROWN);

        final Rabbit rabbit = mock(Rabbit.class);
        assertTrue(PhenotypeApplier.apply(
            rabbit, EntityType.RABBIT, snapshot("rabbit.variant", "THE_KILLER_BUNNY")
        ));
        verify(rabbit).setRabbitType(Rabbit.Type.THE_KILLER_BUNNY);
    }

    @Test
    void sheepColorSetterRemainsSupported() {
        final Sheep sheep = mock(Sheep.class);
        assertTrue(PhenotypeApplier.apply(
            sheep, EntityType.SHEEP, snapshot("sheep.color", "PINK")
        ));
        verify(sheep).setColor(DyeColor.PINK);
        assertFalse(PhenotypeApplier.apply(sheep, EntityType.SHEEP, new PhenotypeSnapshot(List.of())));
    }

    @Test
    void registryBackedUnknownAndMissingLabelsReturnFalse() {
        for (final EntityType type : List.of(
            EntityType.CAT,
            EntityType.CHICKEN,
            EntityType.COW,
            EntityType.FROG,
            EntityType.PIG,
            EntityType.WOLF
        )) {
            final Entity entity = mock(Entity.class);
            assertFalse(PhenotypeApplier.apply(
                entity, type, snapshot(type.name().toLowerCase() + ".variant", "NOT_A_VARIANT")
            ));
            assertFalse(PhenotypeApplier.apply(entity, type, new PhenotypeSnapshot(List.of())));
        }
    }


    private static PhenotypeSnapshot snapshot(final String key, final String value) {
        return new PhenotypeSnapshot(List.of(new PhenotypeTrait(key, value)));
    }

    private static String readProjectFile(final String... relativeCandidates) throws Exception {
        Path cwd = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            for (final String relative : relativeCandidates) {
                final Path candidate = cwd.resolve(relative);
                if (Files.isRegularFile(candidate)) {
                    return Files.readString(candidate);
                }
            }
            final Path parent = cwd.getParent();
            if (parent == null) {
                break;
            }
            cwd = parent;
        }
        throw new java.nio.file.NoSuchFileException(String.join(" | ", relativeCandidates));
    }
}
