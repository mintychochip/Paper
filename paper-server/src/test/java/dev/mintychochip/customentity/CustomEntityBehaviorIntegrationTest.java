package dev.mintychochip.customentity;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("Normal")
public class CustomEntityBehaviorIntegrationTest {

    @Test
    public void customEntitySpawnUsesReceiverDecisionBeforeCarrierFallback() {
        final World world = mock(World.class);
        final Location location = mock(Location.class);
        when(location.getWorld()).thenReturn(world);
        final CustomEntityDefinition definition = CustomEntityDefinition.builder("mintychochip:denied_entity")
            .blockModel(Material.GLOWSTONE)
            .behavior(CustomEntityBehavior.builder()
                .onSpawn(context -> EntitySpawnResult.deny())
                .build())
            .build();

        assertThrows(IllegalStateException.class, () -> CustomEntities.spawn(location, definition));

    }
    @Test
    public void customEntityApplyDenialLeavesCarrierUntouched() {
        final CustomEntityDefinition definition = CustomEntityDefinition.builder("mintychochip:denied_apply")
            .blockModel(Material.GLOWSTONE)
            .behavior(CustomEntityBehavior.builder()
                .onApply(context -> EntityApplyResult.deny())
                .build())
            .build();
        final BlockDisplay display = mock(BlockDisplay.class);
        final BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.GLOWSTONE);
        when(blockData.getAsString()).thenReturn("minecraft:glowstone");

        assertThrows(
            IllegalStateException.class,
            () -> CustomEntityLifecycle.apply(display, definition, blockData)
        );
        verify(display, never()).setBlock(org.mockito.ArgumentMatchers.any());
        verify(display, never()).setTransformation(org.mockito.ArgumentMatchers.any());
    }
}
