package dev.mintychochip.customblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.mintychochip.behavior.ActorView;
import dev.mintychochip.behavior.BlockDataView;
import dev.mintychochip.behavior.BlockView;
import dev.mintychochip.behavior.Decision;
import dev.mintychochip.behavior.Position;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.VanillaMaterial;
import org.junit.jupiter.api.Test;

class CustomBlockDefinitionBehaviorTest {

    @Test
    void definitionOwnsAnImmutableBehaviorBundle() {
        final CustomBlockBehavior behavior = CustomBlockBehavior.builder().build();
        final CustomBlockDefinition definition = definitionBuilder().behavior(behavior).build();

        assertSame(behavior, definition.behavior());
    }

    @Test
    void omittedBehaviorUsesMetadataFallback() {
        final CustomBlockDefinition definition = definitionBuilder().build();

        assertEquals(
            Decision.DEFAULT,
            definition.behavior().onBreak().receive(sampleBreakContext()).decision()
        );
    }

    private static CustomBlockDefinition.Builder definitionBuilder() {
        return CustomBlockDefinition.builder("mintychochip:behavior_test")
            .host(PacketHostSpec.defaults());
    }

    private static BlockBreakContext sampleBreakContext() {
        final BlockView block = new BlockView(
            new Position("minecraft:overworld", 0.0, 64.0, 0.0),
            VanillaMaterial.GLASS,
            new BlockDataView(VanillaMaterial.GLASS, "minecraft:glass"),
            Optional.empty()
        );
        final ActorView actor = new ActorView(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            new Position("minecraft:overworld", 0.0, 64.0, 0.0),
            Optional.of("tester"),
            true
        );
        return new BlockBreakContext(block, Optional.of(actor), Optional.empty(), false, true);
    }
}
