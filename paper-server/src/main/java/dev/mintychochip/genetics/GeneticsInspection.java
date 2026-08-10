package dev.mintychochip.genetics;

import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.profile.GeneticsProfile;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.AgeableMob;
import org.bukkit.craftbukkit.entity.CraftEntityType;
import org.bukkit.entity.EntityType;

/** Read-only view of a cached ageable genome for administrative tools. */
final class GeneticsInspection {

    private GeneticsInspection() {
    }

    static Optional<Snapshot> inspect(final AgeableMob ageable) {
        Objects.requireNonNull(ageable, "ageable");
        final UUID entityId = ageable.getUUID();
        final Genome genome = AnimalGenetics.getGenome(entityId);
        if (genome == null) {
            return Optional.empty();
        }
        final GeneticsProfile profile = AnimalGenetics.profile(ageable);
        final PhenotypeSnapshot phenotype = profile.phenotype(genome);
        return Optional.of(new Snapshot(
            entityId,
            CraftEntityType.minecraftToBukkit(ageable.getType()),
            profile,
            genome,
            phenotype
        ));
    }

    record Snapshot(
        UUID entityId,
        EntityType entityType,
        GeneticsProfile profile,
        Genome genome,
        PhenotypeSnapshot phenotype
    ) {
    }
}
