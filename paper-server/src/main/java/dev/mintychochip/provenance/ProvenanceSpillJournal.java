package dev.mintychochip.provenance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
/**
 * Append-only spill journal for critical provenance writes when the memory queue
 * is full (or during recovery). One JSON object per line with a {@code k}
 * discriminator so records can be rebuilt without the DB.
 *
 * <p>Safe to call from the game thread: each append is a short file append only
 * (no JDBC). Recovery is two-phase: {@link #seizePending()} moves spill aside for
 * apply, and {@link #ackSeized()} deletes it only after a successful DB commit.
 */
public final class ProvenanceSpillJournal {

    public sealed interface SpillRecord {
        long sequence();

        record Lineage(long sequence, @NotNull LineageNode node) implements SpillRecord {
            public Lineage(@NotNull LineageNode node) {
                this(0L, node);
            }
        }

        record Live(long sequence, @NotNull LiveRecord record) implements SpillRecord {
            public Live(@NotNull LiveRecord record) {
                this(0L, record);
            }
        }

        record Collision(
            long sequence,
            @NotNull CollisionRecord record,
            @Nullable UUID auditEventId,
            @Nullable ProvenanceEvent auditEvent
        ) implements SpillRecord {
            public Collision(@NotNull CollisionRecord record) {
                this(0L, record, null, null);
            }

            public Collision(long sequence, @NotNull CollisionRecord record) {
                this(sequence, record, null, null);
            }

            public Collision {
                if ((auditEventId == null) != (auditEvent == null)) {
                    throw new IllegalArgumentException("collision audit payload must be paired");
                }
            }
        }

        record Audit(long sequence, @NotNull UUID eventId, @NotNull ProvenanceEvent event) implements SpillRecord {
            public Audit(@NotNull ProvenanceEvent event) {
                this(0L, UUID.randomUUID(), event);
            }

            public Audit(@NotNull UUID eventId, @NotNull ProvenanceEvent event) {
                this(0L, eventId, event);
            }
        }
    }
    private final @NotNull Path path;
    private final @NotNull Path replayPath;
    private final @NotNull AtomicLong incompleteTailCount = new AtomicLong();

    public ProvenanceSpillJournal(final @NotNull Path path) {
        this.path = Objects.requireNonNull(path, "path");
        this.replayPath = path.resolveSibling(path.getFileName().toString() + ".replay");
    }

    public @NotNull Path path() {
        return this.path;
    }

    public @NotNull Path replayPath() {
        return this.replayPath;
    }

    public long incompleteTailCount() {
        return this.incompleteTailCount.get();
    }

    public synchronized void appendLineage(final @NotNull LineageNode node) throws IOException {
        appendLineage(0L, node);
    }

    public synchronized void appendLineage(final long sequence, final @NotNull LineageNode node) throws IOException {
        Objects.requireNonNull(node, "node");
        final JsonObject o = new JsonObject();
        o.addProperty("v", 2);
        o.addProperty("seq", sequence);
        o.addProperty("k", "lineage");
        o.addProperty("id", node.id().toString());
        o.addProperty("item", node.itemId());
        o.addProperty("source", node.source().name());
        o.add("parents", uuidArray(node.parents()));
        o.addProperty("born", node.bornEpochMs());
        if (node.bornHolder() != null) {
            o.addProperty("holder", node.bornHolder());
        }
        o.addProperty("dead", node.dead());
        if (node.dead()) {
            o.addProperty("death_reason", (node.deathReason() != null ? node.deathReason() : ProvenanceReason.DESTROYED).name());
            o.addProperty("death_epoch", node.deathEpochMs());
        }
        appendLine(o);
    }

    public synchronized void appendLive(final @NotNull LiveRecord record) throws IOException {
        appendLive(0L, record);
    }

    public synchronized void appendLive(final long sequence, final @NotNull LiveRecord record) throws IOException {
        Objects.requireNonNull(record, "record");
        final JsonObject o = new JsonObject();
        o.addProperty("v", 2);
        o.addProperty("seq", sequence);
        o.addProperty("k", "live");
        o.addProperty("id", record.id().toString());
        o.addProperty("item", record.itemId());
        o.addProperty("location", record.locationDisplay());
        o.addProperty("count", record.count());
        o.addProperty("epoch", record.epochMs());
        o.addProperty("dead", record.dead());
        appendLine(o);
    }

    public synchronized void appendCollision(final @NotNull CollisionRecord record) throws IOException {
        appendCollision(0L, record, null, null);
    }

    public synchronized void appendCollision(
        final long sequence,
        final @NotNull CollisionRecord record
    ) throws IOException {
        appendCollision(sequence, record, null, null);
    }

    public synchronized void appendCollision(
        final long sequence,
        final @NotNull CollisionRecord record,
        final @Nullable UUID auditEventId,
        final @Nullable ProvenanceEvent auditEvent
    ) throws IOException {
        Objects.requireNonNull(record, "record");
        if ((auditEventId == null) != (auditEvent == null)) {
            throw new IllegalArgumentException("collision audit payload must be paired");
        }
        final JsonObject o = new JsonObject();
        o.addProperty("v", 2);
        o.addProperty("seq", sequence);
        o.addProperty("k", "collision");
        o.addProperty("id", record.id().toString());
        o.addProperty("kind", record.kind().name());
        o.addProperty("existing", record.existingLocation().display());
        o.addProperty("observed", record.observedLocation().display());
        o.addProperty("epoch", record.epochMs());
        if (auditEventId != null) {
            final JsonObject audit = new JsonObject();
            audit.addProperty("event_id", auditEventId.toString());
            appendAuditFields(audit, auditEvent);
            o.add("audit", audit);
        }
        appendLine(o);
    }

    public synchronized void appendAudit(final @NotNull ProvenanceEvent event) throws IOException {
        appendAudit(0L, UUID.randomUUID(), event);
    }

    public synchronized void appendAudit(final @NotNull UUID eventId, final @NotNull ProvenanceEvent event) throws IOException {
        appendAudit(0L, eventId, event);
    }

    public synchronized void appendAudit(
        final long sequence,
        final @NotNull UUID eventId,
        final @NotNull ProvenanceEvent event
    ) throws IOException {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(event, "event");
        final JsonObject o = new JsonObject();
        o.addProperty("v", 2);
        o.addProperty("seq", sequence);
        o.addProperty("k", "audit");
        o.addProperty("event_id", eventId.toString());
        appendAuditFields(o, event);
        appendLine(o);
    }
    private static void appendAuditFields(
        final @NotNull JsonObject object,
        final @NotNull ProvenanceEvent event
    ) {
        object.addProperty("t", event.epochMs());
        object.addProperty("type", event.type().name());
        object.addProperty("id", event.id().toString());
        if (event.itemId() != null) object.addProperty("item", event.itemId());
        if (event.source() != null) object.addProperty("source", event.source().name());
        if (event.reason() != null) object.addProperty("reason", event.reason().name());
        if (!event.related().isEmpty()) object.add("related", uuidArray(event.related()));
        if (event.holder() != null) object.addProperty("holder", event.holder());
        if (event.detail() != null) object.addProperty("detail", event.detail());
    }

    public synchronized @NotNull List<SpillRecord> readAll() throws IOException {
        if (!Files.isRegularFile(this.path)) {
            return List.of();
        }
        return parseFile(this.path);
    }

    /**
     * Seize spill contents for replay without acknowledging.
     *
     * <p>If a prior {@code .replay} file exists (e.g. crash mid-apply), that file is
     * returned first and left intact — never deleted before successful apply.
     * Otherwise the active spill is moved to {@code .replay} so concurrent
     * {@link #appendLineage append*} calls can open a new spill file.
     *
     * <p>Callers must {@link #ackSeized()} only after the returned records are
     * successfully applied to the durable store.
     */
    public synchronized @NotNull List<SpillRecord> seizePending() throws IOException {
        if (Files.isRegularFile(this.replayPath)) {
            return parseFile(this.replayPath);
        }
        if (!Files.isRegularFile(this.path)) {
            return List.of();
        }
        Files.move(this.path, this.replayPath);
        return parseFile(this.replayPath);
    }

    /**
     * Acknowledge successful apply of the seized {@code .replay} batch by deleting it.
     * Safe no-op if nothing is seized.
     */
    public synchronized void ackSeized() throws IOException {
        Files.deleteIfExists(this.replayPath);
    }

    public synchronized void truncate() throws IOException {
        if (Files.exists(this.path)) {
            Files.delete(this.path);
        }
        Files.deleteIfExists(this.replayPath);
    }

    private @NotNull List<SpillRecord> parseFile(final @NotNull Path file) throws IOException {
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        final List<String> nonBlank = new ArrayList<>(lines.size());
        for (final String line : lines) {
            if (line != null && !line.isBlank()) {
                nonBlank.add(line.trim());
            }
        }
        final List<SpillRecord> out = new ArrayList<>(nonBlank.size());
        for (int i = 0; i < nonBlank.size(); i++) {
            final String line = nonBlank.get(i);
            final boolean last = i == nonBlank.size() - 1;
            try {
                out.add(parseLine(line));
            } catch (final IOException ex) {
                // Truncated write from crash: drop incomplete trailing line, keep prior records.
                if (last) {
                    this.incompleteTailCount.incrementAndGet();
                    break;
                }
                throw ex;
            }
        }
        return List.copyOf(out);
    }

    public long sizeBytes() {
        return sizeOf(this.path) + sizeOf(this.replayPath);
    }

    private static long sizeOf(final @NotNull Path file) {
        try {
            return Files.isRegularFile(file) ? Files.size(file) : 0L;
        } catch (final IOException ignored) {
            return 0L;
        }
    }

    private void appendLine(final JsonObject object) throws IOException {
        final Path parent = this.path.getParent();
        if (parent != null) Files.createDirectories(parent);
        final byte[] bytes = (object.toString() + '\n').getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(
            this.path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND
        )) {
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static @NotNull SpillRecord parseLine(final String line) throws IOException {
        final JsonObject o;
        try {
            o = JsonParser.parseString(line).getAsJsonObject();
        } catch (final RuntimeException ex) {
            throw new IOException("malformed spill line: " + line, ex);
        }
        final String kind;
        final int version;
        final long sequence;
        try {
            kind = stringOrNull(o, "k");
            if (kind == null) throw new IOException("spill line missing k: " + line);
            version = o.has("v") ? o.get("v").getAsInt() : 1;
            if (version != 1 && version != 2) {
                throw new IOException("unsupported spill version: " + version);
            }
            sequence = version == 2 ? o.get("seq").getAsLong() : 0L;
        } catch (final IOException ex) {
            throw ex;
        } catch (final RuntimeException ex) {
            throw new IOException("malformed spill header: " + line, ex);
        }
        try {
            return switch (kind) {
                case "lineage" -> new SpillRecord.Lineage(sequence, parseLineage(o));
                case "live" -> new SpillRecord.Live(sequence, parseLive(o));
                case "collision" -> {
                    final CollisionRecord collision = parseCollision(o);
                    if (!o.has("audit")) {
                        yield new SpillRecord.Collision(sequence, collision);
                    }
                    final JsonObject audit = o.getAsJsonObject("audit");
                    yield new SpillRecord.Collision(
                        sequence,
                        collision,
                        UUID.fromString(requireString(audit, "event_id")),
                        parseAudit(audit)
                    );
                }
                case "audit" -> new SpillRecord.Audit(
                    sequence,
                    o.has("event_id") ? UUID.fromString(requireString(o, "event_id")) : legacyAuditId(line),
                    parseAudit(o)
                );
                default -> throw new IOException("unknown spill kind: " + kind);
            };
        } catch (final IOException ex) {
            throw ex;
        } catch (final RuntimeException ex) {
            throw new IOException("cannot rebuild spill record: " + line, ex);
        }
    }

    private static @NotNull LineageNode parseLineage(final JsonObject o) {
        final LineageNode node = new LineageNode(
            UUID.fromString(requireString(o, "id")),
            requireString(o, "item"),
            ProvenanceSource.valueOf(requireString(o, "source")),
            parseUuidList(o.get("parents")),
            o.get("born").getAsLong(),
            stringOrNull(o, "holder")
        );
        if (o.has("dead") && o.get("dead").getAsBoolean()) {
            node.markDead(
                parseDeathReason(stringOrNull(o, "death_reason")),
                o.has("death_epoch") ? o.get("death_epoch").getAsLong() : 0L
            );
        }
        return node;
    }

    private static @NotNull LiveRecord parseLive(final JsonObject o) {
        return new LiveRecord(
            UUID.fromString(requireString(o, "id")),
            requireString(o, "item"),
            requireString(o, "location"),
            o.get("count").getAsInt(),
            o.get("epoch").getAsLong(),
            o.has("dead") && o.get("dead").getAsBoolean()
        );
    }

    private static @NotNull CollisionRecord parseCollision(final JsonObject o) {
        return new CollisionRecord(
            UUID.fromString(requireString(o, "id")),
            ProvenanceCollisionKind.valueOf(requireString(o, "kind")),
            parseLocation(requireString(o, "existing")),
            parseLocation(requireString(o, "observed")),
            o.get("epoch").getAsLong()
        );
    }

    private static @NotNull ProvenanceEvent parseAudit(final JsonObject o) {
        final String sourceRaw = stringOrNull(o, "source");
        final String reasonRaw = stringOrNull(o, "reason");
        return new ProvenanceEvent(
            o.get("t").getAsLong(),
            ProvenanceEventType.valueOf(requireString(o, "type")),
            UUID.fromString(requireString(o, "id")),
            stringOrNull(o, "item"),
            sourceRaw != null ? ProvenanceSource.valueOf(sourceRaw) : null,
            reasonRaw != null ? ProvenanceReason.valueOf(reasonRaw) : null,
            parseUuidList(o.get("related")),
            stringOrNull(o, "holder"),
            stringOrNull(o, "detail")
        );
    }


    private static @NotNull UUID legacyAuditId(final String line) {
        return UUID.nameUUIDFromBytes(
            ("mintychochip:provenance:v1:audit:" + line).getBytes(StandardCharsets.UTF_8)
        );
    }
    private static @NotNull JsonArray uuidArray(final List<UUID> ids) {
        final JsonArray arr = new JsonArray(ids.size());
        for (final UUID id : ids) {
            arr.add(new JsonPrimitive(id.toString()));
        }
        return arr;
    }

    private static @NotNull List<UUID> parseUuidList(final @Nullable JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return List.of();
        }
        if (el.isJsonArray()) {
            final List<UUID> out = new ArrayList<>();
            for (final JsonElement part : el.getAsJsonArray()) {
                try {
                    out.add(UUID.fromString(part.getAsString()));
                } catch (final IllegalArgumentException ignored) {
                    // skip bad related/parent id
                }
            }
            return List.copyOf(out);
        }
        // Comma-separated fallback (matches repository encoding style).
        final String raw = el.getAsString();
        if (raw.isEmpty()) {
            return List.of();
        }
        final List<UUID> out = new ArrayList<>();
        for (final String part : raw.split(",")) {
            try {
                out.add(UUID.fromString(part.trim()));
            } catch (final IllegalArgumentException ignored) {
                // skip
            }
        }
        return List.copyOf(out);
    }

    private static @NotNull StackLocation parseLocation(final String raw) {
        if (raw == null) {
            return StackLocation.unknown();
        }
        if (raw.startsWith("player:") && raw.indexOf(':', 7) > 7) {
            final int sep = raw.indexOf(':', 7);
            try {
                return StackLocation.playerSlot(
                    UUID.fromString(raw.substring(7, sep)),
                    Integer.parseInt(raw.substring(sep + 1))
                );
            } catch (final IllegalArgumentException ignored) {
                // fall through
            }
        }
        if (raw.startsWith("item_entity:")) {
            try {
                return StackLocation.itemEntity(UUID.fromString(raw.substring("item_entity:".length())));
            } catch (final IllegalArgumentException ignored) {
                // fall through
            }
        }
        if (raw.equals("unknown")) {
            return StackLocation.unknown();
        }
        return StackLocation.labeled(raw);
    }

    private static @NotNull ProvenanceReason parseDeathReason(final @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return ProvenanceReason.DESTROYED;
        }
        try {
            return ProvenanceReason.valueOf(raw);
        } catch (final IllegalArgumentException ignored) {
            return ProvenanceReason.DESTROYED;
        }
    }

    private static @NotNull String requireString(final JsonObject o, final String key) {
        final String v = stringOrNull(o, key);
        if (v == null) {
            throw new IllegalArgumentException("missing field: " + key);
        }
        return v;
    }

    private static @Nullable String stringOrNull(final JsonObject o, final String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        return o.get(key).getAsString();
    }
}
