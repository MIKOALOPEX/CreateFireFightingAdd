package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.createmod.catnip.data.WorldAttached;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/** World-space broad phase shared by latches. No sublevel enumeration or chunk loading is needed. */
public final class RopeLatchIndex {
    public static final double RADIUS = 0.625;
    private static final WorldAttached<RopeLatchIndex> WORLDS = new WorldAttached<>(ignored -> new RopeLatchIndex());
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Map<Long, LinkedHashSet<Entry>> cells = new HashMap<>();
    private long requestedUntil = Long.MIN_VALUE;
    private long budgetTick = Long.MIN_VALUE;
    private int maintenanceBudget;
    private long searchTick = Long.MIN_VALUE;
    private int pointBudget;
    private int candidateBudget;
    private int captureBudget;

    public record Hit(ServerRopeStrand rope, int point) {}

    public static void request(ServerLevel level) {
        WORLDS.get(level).requestedUntil = level.getGameTime() + 12;
    }

    public static void observe(ServerLevel level, Object object, BoundingBox3d bounds) {
        if (!(object instanceof ServerRopeStrand rope)) return;
        RopeLatchIndex index = WORLDS.get(level);
        long now = level.getGameTime();
        if (now > index.requestedUntil) return;
        Entry previous = index.entries.get(rope.getUUID());
        if (previous != null && now - previous.updated < 10) return;
        AABB box = new AABB(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
        if (!Double.isFinite(box.getSize())) return;
        int x0 = cell(box.minX), y0 = cell(box.minY), z0 = cell(box.minZ);
        int x1 = cell(box.maxX), y1 = cell(box.maxY), z1 = cell(box.maxZ);
        long count = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
        // Pathological bounds must not turn one rope into an unbounded index update.
        if (count <= 0 || count > 256) return;
        if (index.budgetTick != now) {
            index.budgetTick = now;
            index.maintenanceBudget = 1024;
        }
        int cost = (int) count + (previous == null ? 0 : previous.keys.size());
        if (index.maintenanceBudget < cost) return;
        index.maintenanceBudget -= cost;
        if (previous != null) index.remove(previous);
        Entry entry = new Entry(rope, box, now, previous == null ? 1 : previous.cursor);
        index.entries.put(rope.getUUID(), entry);
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            long key = SectionPos.asLong(x, y, z);
            entry.keys.add(key);
            index.cells.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).addLast(entry);
        }
    }

    public static void forget(ServerLevel level, Object object) {
        if (!(object instanceof ServerRopeStrand rope)) return;
        RopeLatchIndex index = WORLDS.get(level);
        Entry entry = index.entries.get(rope.getUUID());
        if (entry != null && entry.rope.get() == rope) index.remove(entry);
    }

    static Hit search(ServerLevel level, RopeCaptureArea area) {
        RopeLatchIndex index = WORLDS.get(level);
        if (index.searchTick != level.getGameTime()) {
            index.searchTick = level.getGameTime();
            index.pointBudget = 2048;
            index.candidateBudget = 256;
            index.captureBudget = 2;
        }
        if (index.pointBudget <= 0 || index.candidateBudget <= 0 || index.captureBudget <= 0) return null;
        AABB query = area.bounds();
        Set<UUID> visited = new HashSet<>();
        int candidates = 0, points = 0;
        for (int x = cell(query.minX); x <= cell(query.maxX); x++)
            for (int y = cell(query.minY); y <= cell(query.maxY); y++)
                for (int z = cell(query.minZ); z <= cell(query.maxZ); z++) {
                    LinkedHashSet<Entry> bucket = index.cells.get(SectionPos.asLong(x, y, z));
                    if (bucket == null) continue;
                    int visits = Math.min(32, bucket.size());
                    for (int n = 0; n < visits; n++) {
                        Entry entry = bucket.removeFirst();
                        bucket.addLast(entry);
                        if (++candidates > 32 || points >= 256 || --index.candidateBudget < 0) return null;
                        ServerRopeStrand rope = entry.rope.get();
                        if (rope == null || !visited.add(rope.getUUID()) || !rope.isActive()
                                || level.getGameTime() - entry.updated > 40 || !entry.bounds.intersects(query)
                                || !PinnedRopeHandle.canCapture(level, rope)) continue;
                        int size = rope.getPoints().size();
                        for (int i = 0; i < Math.min(32, size - 2) && points < 256; i++, points++) {
                            if (--index.pointBudget < 0) return null;
                            if (entry.cursor < 1 || entry.cursor >= size - 1) entry.cursor = 1;
                            int node = entry.cursor++;
                            var p = rope.getPoints().get(node);
                            if (area.contains(p)) {
                                index.captureBudget--;
                                return new Hit(rope, node);
                            }
                        }
                    }
                }
        return null;
    }

    private void remove(Entry entry) {
        entries.remove(entry.id, entry);
        for (long key : entry.keys) {
            LinkedHashSet<Entry> bucket = cells.get(key);
            if (bucket == null) continue;
            bucket.remove(entry);
            if (bucket.isEmpty()) cells.remove(key);
        }
    }

    private static int cell(double position) { return Math.floorDiv((int) Math.floor(position), 16); }

    private static final class Entry {
        final UUID id;
        final WeakReference<ServerRopeStrand> rope;
        final AABB bounds;
        final long updated;
        final Set<Long> keys = new HashSet<>();
        int cursor;
        Entry(ServerRopeStrand rope, AABB bounds, long updated, int cursor) {
            this.id = rope.getUUID();
            this.rope = new WeakReference<>(rope);
            this.bounds = bounds;
            this.updated = updated;
            this.cursor = cursor;
        }
    }
}
