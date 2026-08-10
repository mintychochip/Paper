package dev.mintychochip.genetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.Sex;
import dev.mintychochip.genetics.profile.SheepGeneticsProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityTypes;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@Normal
class GeneticsBukkitCommandTest {

    private final GeneticsBukkitCommand command = new GeneticsBukkitCommand();

    @AfterEach
    void clearCache() {
        AnimalGenetics.clearCache();
    }

    @Test
    void permissionIsRequiredForNonOperators() {
        final CommandSender sender = mock(CommandSender.class);
        when(sender.isOp()).thenReturn(false);
        when(sender.hasPermission(GeneticsBukkitCommand.PERMISSION)).thenReturn(false);
        final List<Component> messages = capture(sender);

        assertTrue(command.execute(sender, "genetics", new String[] {"profile", "sheep"}));
        assertEquals("No permission.", text(messages).trim());
    }

    @Test
    void usageListsBothReadOnlySubcommands() {
        final CommandSender sender = permittedSender();
        final List<Component> messages = capture(sender);

        command.execute(sender, "genetics", new String[0]);

        final String output = text(messages);
        assertTrue(output.contains("/genetics inspect [selector]"));
        assertTrue(output.contains("/genetics profile <entity-type>"));
    }

    @Test
    void consoleInspectRequiresSelector() {
        final CommandSender sender = permittedSender();
        final List<Component> messages = capture(sender);

        command.execute(sender, "genetics", new String[] {"inspect"});

        assertTrue(text(messages).contains("Console must specify an entity selector."));
    }

    @Test
    void profileCommandPrintsSheepMetadata() {
        final CommandSender sender = permittedSender();
        final List<Component> messages = capture(sender);

        command.execute(sender, "genetics", new String[] {"profile", "sheep"});

        final String output = text(messages);
        assertTrue(output.contains("profile: sheep"));
        assertTrue(output.contains("sheep.base"));
        assertTrue(output.contains("sheep.dilution"));
        assertTrue(output.contains("sheep.albinism"));
        assertTrue(output.contains("BLACK"));
    }

    @Test
    void genericProfilePrintsLegacyLociWithoutInventedLabels() {
        final CommandSender sender = permittedSender();
        final List<Component> messages = capture(sender);

        command.execute(sender, "genetics", new String[] {"profile", "generic"});

        final String output = text(messages);
        assertTrue(output.contains("coat"));
        assertTrue(output.contains("vitality"));
        assertTrue(output.contains("mt-vigor"));
        assertTrue(output.contains("labels: not enumerated"));
    }

    @Test
    void nonAgeableCrosshairTargetIsRejected() {
        final Player player = mock(Player.class);
        when(player.isOp()).thenReturn(true);
        final CraftEntity target = mock(CraftEntity.class);
        final net.minecraft.world.entity.Entity raw = mock(net.minecraft.world.entity.Entity.class);
        doReturn(raw).when(target).getHandle();
        doReturn(target).when(player).getTargetEntity(32, false);
        final List<Component> messages = capture(player);

        command.execute(player, "genetics", new String[] {"inspect"});

        assertTrue(text(messages).contains("requires an ageable mob"));
    }

    @Test
    void inspectPrintsStoredGenotypeAndPhenotype() {
        final UUID id = UUID.randomUUID();
        final AgeableMob ageable = mock(AgeableMob.class);
        when(ageable.getUUID()).thenReturn(id);
        doReturn(EntityTypes.SHEEP).when(ageable).getType();
        final CraftEntity target = mock(CraftEntity.class);
        doReturn(ageable).when(target).getHandle();
        final Player player = mock(Player.class);
        when(player.isOp()).thenReturn(true);
        doReturn(target).when(player).getTargetEntity(32, false);
        final Genome genome = SheepGeneticsProfile.INSTANCE.founder(Sex.FEMALE, new Random(7L));
        AnimalGenetics.setGenome(id, genome);
        final List<Component> messages = capture(player);

        command.execute(player, "genetics", new String[] {"inspect"});

        final String output = text(messages);
        assertTrue(output.contains("profile: sheep"));
        assertTrue(output.contains("sheep.base"));
        assertTrue(output.contains("dna=ATGAAACCC"));
        assertTrue(output.contains("sheep.color"));
    }

    @Test
    void profileTabCompletionListsEntityTypes() {
        final CommandSender sender = permittedSender();

        final List<String> completions = command.tabComplete(sender, "genetics", new String[] {"profile", "shee"});

        assertEquals(List.of("sheep"), completions);
    }

    private static CommandSender permittedSender() {
        final CommandSender sender = mock(CommandSender.class);
        when(sender.isOp()).thenReturn(true);
        return sender;
    }

    private static List<Component> capture(final CommandSender sender) {
        final List<Component> messages = new ArrayList<>();
        doAnswer(invocation -> {
            messages.add(invocation.getArgument(0, Component.class));
            return null;
        }).when(sender).sendMessage(any(Component.class));
        return messages;
    }

    private static String text(final List<Component> messages) {
        return messages.stream()
            .map(PlainTextComponentSerializer.plainText()::serialize)
            .reduce("", (left, right) -> left + "\n" + right);
    }
}
