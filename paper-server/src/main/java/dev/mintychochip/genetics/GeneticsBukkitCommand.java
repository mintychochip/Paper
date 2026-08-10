package dev.mintychochip.genetics;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;
import dev.mintychochip.genetics.profile.GeneticsProfiles;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;
import static net.kyori.adventure.text.format.TextDecoration.BOLD;

import dev.mintychochip.genetics.model.Allele;
import dev.mintychochip.genetics.model.GeneCopy;
import dev.mintychochip.genetics.model.LocusDefinition;
import dev.mintychochip.genetics.profile.GeneticsProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.minecraft.world.entity.AgeableMob;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Read-only genetics diagnostics for operators. */
public final class GeneticsBukkitCommand extends Command {

    public static final String PERMISSION = "mintychochip.genetics";
    private static final int TARGET_DISTANCE = 32;
    private static final List<String> SUBCOMMANDS = List.of("inspect", "profile");

    public GeneticsBukkitCommand() {
        super(
            "genetics",
            "Inspect mintychochip animal genetics",
            "/genetics <inspect [selector]|profile <entity-type>>",
            List.of("genotype", "genes")
        );
        this.setPermission(PERMISSION);
    }

    @Override
    public boolean execute(
        @NotNull final CommandSender sender,
        @NotNull final String commandLabel,
        @NotNull final String[] args
    ) {
        if (!sender.isOp() && !sender.hasPermission(PERMISSION)) {
            sender.sendMessage(text("No permission.", RED));
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "inspect" -> inspect(sender, Arrays.copyOfRange(args, 1, args.length));
            case "profile" -> profile(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> {
                sender.sendMessage(text("Unknown subcommand: ", RED).append(text(args[0], YELLOW)));
                usage(sender);
            }
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(
        @NotNull final CommandSender sender,
        @NotNull final String alias,
        @NotNull final String[] args
    ) {
        if (!sender.isOp() && !sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && "profile".equalsIgnoreCase(args[0])) {
            final List<String> types = new ArrayList<>();
            for (final EntityType type : EntityType.values()) {
                types.add(type.name().toLowerCase(Locale.ROOT));
            }
            return filter(types, args[1]);
        }
        return Collections.emptyList();
    }

    private static void usage(final CommandSender sender) {
        sender.sendMessage(text("Genetics commands", GOLD, BOLD));
        sender.sendMessage(text("  /genetics inspect [selector]", AQUA)
            .append(text("  read one ageable genome without creating it", GRAY)));
        sender.sendMessage(text("  /genetics profile <entity-type>", AQUA)
            .append(text("  show registered locus metadata", GRAY)));
    }

    private static void inspect(final CommandSender sender, final String[] args) {
        if (args.length > 1) {
            sender.sendMessage(text("Usage: /genetics inspect [selector]", GRAY));
            return;
        }
        final Entity target;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(text("Console must specify an entity selector.", RED));
                return;
            }
            target = player.getTargetEntity(TARGET_DISTANCE, false);
            if (target == null) {
                sender.sendMessage(text("No entity is within your crosshair.", YELLOW));
                return;
            }
        } else {
            final List<Entity> matches;
            try {
                matches = Bukkit.selectEntities(sender, args[0]);
            } catch (final IllegalArgumentException ex) {
                sender.sendMessage(text("Invalid entity selector: ", RED)
                    .append(text(ex.getMessage() == null ? args[0] : ex.getMessage(), YELLOW)));
                return;
            }
            if (matches.isEmpty()) {
                sender.sendMessage(text("Selector matched no entities.", YELLOW));
                return;
            }
            if (matches.size() != 1) {
                sender.sendMessage(text("Selector matched " + matches.size()
                    + " entities; add limit=1.", YELLOW));
                return;
            }
            target = matches.getFirst();
        }

        final AgeableMob ageable = asAgeable(target);
        if (ageable == null) {
            sender.sendMessage(text("Genetics inspection requires an ageable mob.", RED));
            return;
        }
        final Optional<GeneticsInspection.Snapshot> inspected;
        try {
            inspected = GeneticsInspection.inspect(ageable);
        } catch (final RuntimeException ex) {
            sender.sendMessage(text("Unable to decode this genome: ", RED)
                .append(text(ex.getMessage() == null ? "invalid data" : ex.getMessage(), YELLOW)));
            return;
        }
        if (inspected.isEmpty()) {
            sender.sendMessage(text("No genome attached; inspection did not create one.", YELLOW));
            return;
        }
        printInspection(sender, inspected.orElseThrow());
    }

    private static AgeableMob asAgeable(final Entity target) {
        if (!(target instanceof CraftEntity craft)) {
            return null;
        }
        return craft.getHandle() instanceof AgeableMob ageable ? ageable : null;
    }

    private static void printInspection(
        final CommandSender sender,
        final GeneticsInspection.Snapshot snapshot
    ) {
        final GeneticsProfile profile = snapshot.profile();
        sender.sendMessage(text("Genetics inspection", GOLD, BOLD));
        sender.sendMessage(keyValue("entity", snapshot.entityType().name(), AQUA));
        sender.sendMessage(keyValue("uuid", snapshot.entityId().toString(), WHITE));
        sender.sendMessage(keyValue("profile", profile.id(), AQUA));
        sender.sendMessage(keyValue("sex", snapshot.genome().sex().name(), AQUA));
        sender.sendMessage(text("loci", GOLD));
        if (profile.catalog().size() == 0) {
            sender.sendMessage(text("  no loci", GRAY));
        } else {
            for (final LocusDefinition locus : profile.catalog().all()) {
                printLocus(sender, profile, snapshot, locus);
            }
        }
        sender.sendMessage(text("phenotype", GOLD));
        if (snapshot.phenotype().traits().isEmpty()) {
            sender.sendMessage(text("  none", GRAY));
        } else {
            snapshot.phenotype().traits().forEach(trait -> sender.sendMessage(
                text("  " + trait.key() + " = ", GRAY).append(text(trait.value(), GREEN))
            ));
        }
    }

    private static void printLocus(
        final CommandSender sender,
        final GeneticsProfile profile,
        final GeneticsInspection.Snapshot snapshot,
        final LocusDefinition locus
    ) {
        final GeneticsCatalogDescriptions.Description description =
            GeneticsCatalogDescriptions.describe(profile, locus);
        sender.sendMessage(text("  " + locus.id().key(), AQUA));
        sender.sendMessage(text("    inheritance: " + locus.inheritance()
            + "  dominance: " + locus.dominance()
            + "  chromosome: " + locus.chromosome()
            + "  position: " + locus.position(), GRAY));
        if (!description.labels().isEmpty()) {
            sender.sendMessage(text("    labels: " + String.join(", ", description.labels()), GRAY));
        } else {
            sender.sendMessage(text("    labels: not enumerated", GRAY));
        }
        if (description.range() != null) {
            sender.sendMessage(text("    range: " + description.range(), GRAY));
        }
        final GeneCopy copy = snapshot.genome().getOrNull(locus.id());
        if (copy == null) {
            sender.sendMessage(text("    genotype: missing", RED));
            return;
        }
        sender.sendMessage(alleleLine("    allele A: ", copy.alleleA()));
        sender.sendMessage(copy.alleleB() == null
            ? text("    allele B: hemizygous", GRAY)
            : alleleLine("    allele B: ", copy.alleleB()));
    }

    private static Component alleleLine(final String prefix, final Allele allele) {
        return text(prefix, GRAY)
            .append(text(allele.displayKey(), YELLOW))
            .append(text("  dna=", DARK_GRAY))
            .append(text(allele.sequence().asString(), WHITE));
    }

    private static void profile(final CommandSender sender, final String[] args) {
        if (args.length != 1) {
            sender.sendMessage(text("Usage: /genetics profile <entity-type>", GRAY));
            return;
        }
        final String typeName = args[0].toLowerCase(Locale.ROOT).startsWith("minecraft:")
            ? args[0].substring("minecraft:".length()) : args[0];
        final EntityType type = EntityType.fromName(typeName);
        if (type == null && !typeName.equals("generic")) {
            sender.sendMessage(text("Unknown entity type: ", RED).append(text(args[0], YELLOW)));
            return;
        }
        final GeneticsProfile profile = type == null
            ? GeneticsProfiles.generic() : AnimalGenetics.profileFor(type);
        sender.sendMessage(text("Genetics profile", GOLD, BOLD));
        sender.sendMessage(keyValue("entity", type == null ? "GENERIC" : type.name(), AQUA));
        sender.sendMessage(keyValue("profile", profile.id(), AQUA));
        sender.sendMessage(keyValue("mutation", mutationSummary(profile), GRAY));
        sender.sendMessage(keyValue("recombination", recombinationSummary(profile), GRAY));
        if (profile.catalog().size() == 0) {
            sender.sendMessage(text("loci: none", GRAY));
            return;
        }
        sender.sendMessage(text("loci", GOLD));
        for (final LocusDefinition locus : profile.catalog().all()) {
            final GeneticsCatalogDescriptions.Description description =
                GeneticsCatalogDescriptions.describe(profile, locus);
            sender.sendMessage(text("  " + locus.id().key(), AQUA));
            sender.sendMessage(text("    phenotype: " + locus.phenotypeKey()
                + "  inheritance: " + locus.inheritance()
                + "  dominance: " + locus.dominance(), GRAY));
            sender.sendMessage(text(description.labels().isEmpty()
                ? "    labels: not enumerated"
                : "    labels: " + String.join(", ", description.labels()), GRAY));
            if (description.range() != null) {
                sender.sendMessage(text("    range: " + description.range(), GRAY));
            }
        }
    }

    private static String mutationSummary(final GeneticsProfile profile) {
        final var mutation = profile.mutation();
        return mutation.isEnabled()
            ? "enabled substitution=" + mutation.substitutionRate()
                + " insertion=" + mutation.insertionRate()
                + " deletion=" + mutation.deletionRate()
            : "disabled";
    }

    private static String recombinationSummary(final GeneticsProfile profile) {
        final var recombination = profile.recombination();
        return "none=" + recombination.noCrossoverChance()
            + " single=" + recombination.singleCrossoverChance()
            + " double=" + recombination.doubleCrossoverChance();
    }

    private static Component keyValue(
        final String key,
        final String value,
        final net.kyori.adventure.text.format.NamedTextColor valueColor
    ) {
        return text("  " + key + ": ", GRAY).append(text(value, valueColor));
    }

    private static List<String> filter(final List<String> options, final String prefix) {
        final String normalized = prefix.toLowerCase(Locale.ROOT);
        final List<String> matches = new ArrayList<>();
        for (final String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
