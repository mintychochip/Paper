package dev.mintychochip.customentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.mintychochip.behavior.BlockDataView;
import dev.mintychochip.behavior.Decision;
import dev.mintychochip.behavior.EntityView;
import dev.mintychochip.behavior.Position;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.VanillaMaterial;
import org.junit.jupiter.api.Test;

class CustomEntityBehaviorTest {

    @Test
    void entityDefinitionOwnsSpawnAndApplyReceivers() {
        final CustomEntityBehavior behavior = CustomEntityBehavior.builder()
            .onSpawn(context -> EntitySpawnResult.allow())
            .onApply(context -> EntityApplyResult.defaults())
            .build();
        final CustomEntityDefinition definition = definitionBuilder().behavior(behavior).build();

        assertSame(behavior, definition.behavior());
        assertEquals(
            Decision.ALLOW,
            definition.behavior().onSpawn().receive(sampleSpawnContext()).decision()
        );
        assertEquals(
            Decision.DEFAULT,
            definition.behavior().onApply().receive(sampleApplyContext()).decision()
        );
    }

    private static CustomEntityDefinition.Builder definitionBuilder() {
        return CustomEntityDefinition.builder("mintychochip:behavior_entity")
            .blockModel(VanillaMaterial.GLOWSTONE);
    }

    private static EntitySpawnContext sampleSpawnContext() {
        return new EntitySpawnContext(
            new Position("minecraft:overworld", 0.0, 64.0, 0.0),
            Optional.empty()
        );
    }

    private static EntityApplyContext sampleApplyContext() {
        return new EntityApplyContext(
            sampleEntity(),
            Optional.empty(),
            Optional.of(new BlockDataView(VanillaMaterial.GLOWSTONE, "minecraft:glowstone"))
        );
    }

    private static EntityView sampleEntity() {
        return new EntityView(
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            NamespacedKey.minecraft("block_display"),
            new Position("minecraft:overworld", 0.0, 64.0, 0.0),
            Optional.of(new NamespacedKey("mintychochip", "behavior_entity"))
        );
    }
}
