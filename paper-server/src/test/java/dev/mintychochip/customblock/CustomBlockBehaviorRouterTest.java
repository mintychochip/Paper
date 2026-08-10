package dev.mintychochip.customblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.behavior.ActorView;
import dev.mintychochip.behavior.BlockDataView;
import dev.mintychochip.behavior.BlockView;
import dev.mintychochip.behavior.Decision;
import dev.mintychochip.behavior.Position;
import dev.mintychochip.behavior.ValueOverride;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.VanillaMaterial;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

@Normal
class CustomBlockBehaviorRouterTest {

    @Test
    void breakReceiverOverridesDropsWithoutMutatingTheEvent() {
        final AtomicInteger calls = new AtomicInteger();
        final CustomBlockDefinition definition = CustomBlockDefinition.builder("mintychochip:router_block")
            .host(PacketHostSpec.defaults())
            .behavior(CustomBlockBehavior.builder()
                .onBreak(context -> {
                    calls.incrementAndGet();
                    return BreakResult.builder()
                        .decision(Decision.ALLOW)
                        .drops(DropPlan.none())
                        .experience(ValueOverride.value(0))
                        .build();
                })
                .build())
            .build();

        final BreakResult result = CustomBlockBehaviorRouter.breakPlan(definition, sampleBreakContext());

        assertEquals(1, calls.get());
        assertEquals(DropPlan.Kind.NONE, result.drops().kind());
        assertEquals(0, ((ValueOverride.Value<Integer>) result.experience()).value());
    }

    @Test
    void interactionReceiverCanDenyEventBeforeDefaultUse() {
        CustomBlocks.reset();
        final MemoryCustomBlockLookup lookup = new MemoryCustomBlockLookup();
        CustomBlocks.setLookup(lookup);
        try {
            final CustomBlockDefinition definition = CustomBlockDefinition.builder("mintychochip:interact_block")
                .host(PacketHostSpec.defaults())
                .behavior(CustomBlockBehavior.builder()
                    .onInteract(context -> InteractionResult.builder().decision(Decision.DENY).build())
                    .build())
                .build();
            CustomBlocks.register(definition);

            final World world = org.mockito.Mockito.mock(World.class);
            org.mockito.Mockito.when(world.getName()).thenReturn("world");
            final Block block = org.mockito.Mockito.mock(Block.class);
            final Location location = new Location(world, 2, 64, 2);
            org.mockito.Mockito.when(block.getLocation()).thenReturn(location);
            org.mockito.Mockito.when(block.getWorld()).thenReturn(world);
            org.mockito.Mockito.when(block.getType()).thenReturn(VanillaMaterial.GLASS);
            lookup.put(block, definition);

            final Player player = org.mockito.Mockito.mock(Player.class);
            final PlayerInteractEvent event = new PlayerInteractEvent(
                player,
                Action.RIGHT_CLICK_BLOCK,
                null,
                block,
                BlockFace.UP
            );

            assertTrue(CustomBlockLifecycle.handleInteract(event));
            assertTrue(event.isCancelled());
        } finally {
            CustomBlocks.reset();
        }
    }

    @Test
    void pistonMoveReceiverCanAllowCarrierMovement() {
        CustomBlocks.reset();
        final MemoryCustomBlockLookup lookup = new MemoryCustomBlockLookup();
        CustomBlocks.setLookup(lookup);
        try {
            final CustomBlockDefinition definition = CustomBlockDefinition.builder("mintychochip:move_block")
                .host(PacketHostSpec.defaults())
                .behavior(CustomBlockBehavior.builder()
                    .onMove(context -> MovementResult.builder().decision(Decision.ALLOW).build())
                    .build())
                .build();
            CustomBlocks.register(definition);
            final Block block = customBlock(lookup, definition, 4, 64, 4);
            final BlockPistonExtendEvent event = org.mockito.Mockito.mock(BlockPistonExtendEvent.class);
            org.mockito.Mockito.when(event.getBlocks()).thenReturn(List.of(block));
            org.mockito.Mockito.when(event.getDirection()).thenReturn(BlockFace.EAST);

            new CustomBlockListener().onPistonExtend(event);

            org.mockito.Mockito.verify(event, org.mockito.Mockito.never()).setCancelled(true);
        } finally {
            CustomBlocks.reset();
        }
    }

    @Test
    void explosionReceiverCanDenyDestruction() {
        CustomBlocks.reset();
        final MemoryCustomBlockLookup lookup = new MemoryCustomBlockLookup();
        CustomBlocks.setLookup(lookup);
        try {
            final CustomBlockDefinition definition = CustomBlockDefinition.builder("mintychochip:explode_block")
                .host(PacketHostSpec.defaults())
                .behavior(CustomBlockBehavior.builder()
                    .onExplode(context -> ExplosionResult.builder().decision(Decision.DENY).build())
                    .build())
                .build();
            CustomBlocks.register(definition);
            final Block block = customBlock(lookup, definition, 8, 64, 8);
            final List<Block> blocks = new ArrayList<>(List.of(block));
            final BlockExplodeEvent event = org.mockito.Mockito.mock(BlockExplodeEvent.class);
            org.mockito.Mockito.when(event.blockList()).thenReturn(blocks);

            org.mockito.Mockito.when(event.getBlock()).thenReturn(block);
            new CustomBlockListener().onBlockExplode(event);

            assertFalse(blocks.contains(block));
        } finally {
            CustomBlocks.reset();
        }
    }

    private static Block customBlock(
        final MemoryCustomBlockLookup lookup,
        final CustomBlockDefinition definition,
        final int x,
        final int y,
        final int z
    ) {
        final World world = org.mockito.Mockito.mock(World.class);
        org.mockito.Mockito.when(world.getName()).thenReturn("world");
        final Location location = new Location(world, x, y, z);
        final Block block = org.mockito.Mockito.mock(Block.class);
        org.mockito.Mockito.when(block.getLocation()).thenReturn(location);
        org.mockito.Mockito.when(block.getWorld()).thenReturn(world);
        org.mockito.Mockito.when(block.getType()).thenReturn(VanillaMaterial.GLASS);
        final BlockData data = org.mockito.Mockito.mock(BlockData.class);
        org.mockito.Mockito.when(data.getMaterial()).thenReturn(VanillaMaterial.GLASS);
        org.mockito.Mockito.when(data.getAsString()).thenReturn("minecraft:glass");
        org.mockito.Mockito.when(block.getBlockData()).thenReturn(data);
        lookup.put(block, definition);
        return block;
    }

    private static BlockBreakContext sampleBreakContext() {
        final BlockView block = new BlockView(
            new Position("minecraft:overworld", 0.0, 64.0, 0.0),
            VanillaMaterial.GLASS,
            new BlockDataView(VanillaMaterial.GLASS, "minecraft:glass"),
            Optional.empty()
        );
        final ActorView actor = new ActorView(
            UUID.fromString("00000000-0000-0000-0000-000000000004"),
            new Position("minecraft:overworld", 0.0, 64.0, 0.0),
            Optional.of("tester"),
            true
        );
        return new BlockBreakContext(block, Optional.of(actor), Optional.empty(), false, true);
    }
}
