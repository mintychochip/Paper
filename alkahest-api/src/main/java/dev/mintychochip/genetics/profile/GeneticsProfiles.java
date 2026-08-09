package dev.mintychochip.genetics.profile;

import java.util.Objects;
import org.bukkit.entity.EntityType;

public final class GeneticsProfiles {

    private GeneticsProfiles() {
    }

    public static GeneticsProfile generic() {
        return GenericGeneticsProfile.INSTANCE;
    }

    public static GeneticsProfile forEntityType(final EntityType entityType) {
        Objects.requireNonNull(entityType, "entityType");
        return GenericGeneticsProfile.INSTANCE;
    }
}
