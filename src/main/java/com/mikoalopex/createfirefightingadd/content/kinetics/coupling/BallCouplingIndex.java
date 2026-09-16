package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Per-world loaded endpoint index. Moving endpoints update their spatial bucket as they tick. */
final class BallCouplingIndex {
    private static final Map<Level, BallCouplingIndex> WORLDS = new WeakHashMap<>();
    private final Map<BlockPos, Set<BallCouplingBlockEntity>> cells = new HashMap<>();
    private final Map<UUID, BallCouplingBlockEntity> endpoints = new HashMap<>();
    private final Map<BallCouplingBlockEntity, BlockPos> positions = new IdentityHashMap<>();
    static BallCouplingIndex of(Level world) { return WORLDS.computeIfAbsent(world, l -> new BallCouplingIndex()); }
    static void clear(net.minecraft.server.MinecraftServer server) { WORLDS.keySet().removeIf(l -> l.getServer() == server); }
    private static BlockPos cell(Vec3 p) { return new BlockPos((int)Math.floor(p.x/4), (int)Math.floor(p.y/4), (int)Math.floor(p.z/4)); }

    void update(BallCouplingBlockEntity be) {
        endpoints.put(be.endpointId(), be);
        BlockPos current = cell(be.worldAnchor());
        BlockPos previous = positions.put(be, current);
        if (current.equals(previous)) return;
        removeCell(be, previous);
        cells.computeIfAbsent(current, p -> new HashSet<>()).add(be);
        if (be.partnerId() == null) wakeNearby(be);
    }

    void wakeNearby(BallCouplingBlockEntity top) {
        BlockPos center = cell(top.worldAnchor());
        for (int x=-1;x<=1;x++) for (int y=-1;y<=1;y++) for (int z=-1;z<=1;z++) {
            Set<BallCouplingBlockEntity> entries = cells.get(center.offset(x,y,z));
            if (entries != null) for (BallCouplingBlockEntity be : entries)
                if (be.searches()) be.wakeSearch();
        }
    }

    void remove(BallCouplingBlockEntity be) {
        removeCell(be, positions.remove(be));
        endpoints.remove(be.endpointId(), be);
    }

    private void removeCell(BallCouplingBlockEntity be, BlockPos cell) {
        Set<BallCouplingBlockEntity> entries = cells.get(cell);
        if (entries != null) { entries.remove(be); if (entries.isEmpty()) cells.remove(cell); }
    }

    BallCouplingBlockEntity find(UUID id) { return endpoints.get(id); }

    List<BallCouplingBlockEntity> nearby(BallCouplingBlockEntity base) {
        List<BallCouplingBlockEntity> result = new ArrayList<>();
        BlockPos center = cell(base.worldAnchor());
        for (int x=-1;x<=1;x++) for (int y=-1;y<=1;y++) for (int z=-1;z<=1;z++) {
            Set<BallCouplingBlockEntity> entries = cells.get(center.offset(x,y,z));
            if (entries != null) for (BallCouplingBlockEntity be : entries)
                if (be != base && base.interfaceMode().accepts(be.interfaceMode()) && !be.isRemoved()
                        && be.partnerId() == null && inCaptureRange(base.worldAnchor(), be.worldAnchor()))
                    result.add(be);
        }
        return result;
    }

    private static boolean inCaptureRange(Vec3 a, Vec3 b) {
        return Math.abs(a.x - b.x) <= 1.5 && Math.abs(a.y - b.y) <= 1.5 && Math.abs(a.z - b.z) <= 1.5;
    }
}
