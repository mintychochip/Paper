package dev.mintychochip.customblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.mintychochip.behavior.ActorView;
import dev.mintychochip.behavior.BlockDataView;
import dev.mintychochip.behavior.BlockView;
import dev.mintychochip.behavior.Decision;
import dev.mintychochip.behavior.Position;
import dev.mintychochip.behavior.ValueOverride;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.VanillaMaterial;
import org.junit.jupiter.api.Test;

class CustomBlockBehaviorTest {

    @Test
    void defaultBehaviorUsesExplicitDefaultBranches() {
        final PlacementResult result = CustomBlockBehavior.defaults()
            .onPlace()
            .receive(samplePlaceContext());

        assertEquals(Decision.DEFAULT, result.decision());
        assertEquals(ConsumePlan.Kind.DEFAULT, result.consumeItem().kind());
        assertEquals(CleanupPlan.Kind.DEFAULT, result.cleanup().kind());
        assertInstanceOf(ValueOverride.UseDefault.class, result.blockData());
    }

    @Test
    void receiverResultPreservesExplicitDropSuppression() {
        final CustomBlockBehavior behavior = CustomBlockBehavior.builder()
            .onBreak(context -> BreakResult.builder()
                .decision(Decision.ALLOW)
                .drops(DropPlan.none())
                .experience(ValueOverride.value(0))
                .build())
            .build();

        final BreakResult result = behavior.onBreak().receive(sampleBreakContext());

        assertEquals(Decision.ALLOW, result.decision());
        assertEquals(DropPlan.Kind.NONE, result.drops().kind());
        assertEquals(0, ((ValueOverride.Value<Integer>) result.experience()).value());
        assertEquals(CleanupPlan.Kind.DEFAULT, result.cleanup().kind());
    }

    @Test
    void invalidPlansAndReceiversAreRejected() {
        assertThrows(NullPointerException.class, () -> CustomBlockBehavior.builder().onBreak(null));
        assertThrows(
            IllegalArgumentException.class,
            () -> BreakResult.builder().experience(ValueOverride.value(-1)).build()
        );
        assertThrows(IllegalArgumentException.class, () -> CleanupPlan.explicit(false, false));
        assertEquals(DropPlan.Kind.EXPLICIT, DropPlan.explicit(java.util.List.of()).kind());
    }

    private static BlockPlaceContext samplePlaceContext() {
        return new BlockPlaceContext(sampleBlock(), Optional.of(sampleActor()), Optional.empty(), true);
    }

    private static BlockBreakContext sampleBreakContext() {
        return new BlockBreakContext(sampleBlock(), Optional.of(sampleActor()), Optional.empty(), false, true);
    }

    private static BlockView sampleBlock() {
        return new BlockView(
            new Position("minecraft:overworld", 0.0, 64.0, 0.0),
            VanillaMaterial.GLASS,
            new BlockDataView(VanillaMaterial.GLASS, "minecraft:glass"),
            Optional.empty()
        );
    }

    private static ActorView sampleActor() {
        return new ActorView(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            new Position("minecraft:overworld", 0.0, 64.0, 0.0),
            Optional.of("tester"),
            true
        );
    }
}
