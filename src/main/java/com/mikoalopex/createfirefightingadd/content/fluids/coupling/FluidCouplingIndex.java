package com.mikoalopex.createfirefightingadd.content.fluids.coupling;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Indexes loaded endpoints for nearby docking queries. */
final class FluidCouplingIndex {
    private static final Map<Level, FluidCouplingIndex> WORLDS = new WeakHashMap<>();
    private final Map<UUID, FluidCouplingBlockEntity> endpoints = new HashMap<>();
    private final Map<BlockPos, Set<FluidCouplingBlockEntity>> cells = new HashMap<>();
    private final Map<FluidCouplingBlockEntity, BlockPos> positions = new IdentityHashMap<>();

    static FluidCouplingIndex of(Level level) { return WORLDS.computeIfAbsent(level, key -> new FluidCouplingIndex()); }
    static void clear() { WORLDS.clear(); }
    FluidCouplingBlockEntity find(UUID id) { return id == null ? null : endpoints.get(id); }
    private BlockPos cell(Vec3 p) { return new BlockPos((int)Math.floor(p.x / 4), (int)Math.floor(p.y / 4), (int)Math.floor(p.z / 4)); }

    void update(FluidCouplingBlockEntity be) {
        endpoints.put(be.endpointId(), be);
        BlockPos next = cell(be.worldAnchor()), old = positions.put(be, next);
        if (next.equals(old)) return;
        if (old != null) removeCell(be, old);
        cells.computeIfAbsent(next, key -> new HashSet<>()).add(be);
        wake(be);
    }

    void remove(FluidCouplingBlockEntity be) {
        endpoints.remove(be.endpointId(), be);
        BlockPos cell = positions.remove(be);
        if (cell != null) removeCell(be, cell);
    }

    private void removeCell(FluidCouplingBlockEntity be, BlockPos pos) {
        Set<FluidCouplingBlockEntity> entries = cells.get(pos);
        if (entries == null) return;
        entries.remove(be);
        if (entries.isEmpty()) cells.remove(pos);
    }

    void wake(FluidCouplingBlockEntity be) { nearby(be).forEach(FluidCouplingBlockEntity::wakeSearch); }

    List<FluidCouplingBlockEntity> nearby(FluidCouplingBlockEntity be) {
        List<FluidCouplingBlockEntity> result = new ArrayList<>();
        BlockPos cell = cell(be.worldAnchor());
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            var entries = cells.get(cell.offset(x, y, z));
            if (entries == null) continue;
            for (var other : entries)
                if (other != be && !other.isRemoved() && other.settings().type().accepts(be.settings().type())
                        && inSearchRange(be.settings().type(), be.worldAnchor(), other.worldAnchor())) result.add(other);
        }
        result.sort(Comparator.comparingDouble((FluidCouplingBlockEntity other) -> other.worldAnchor().distanceToSqr(be.worldAnchor()))
            .thenComparing(FluidCouplingBlockEntity::endpointId));
        return result;
    }

    static boolean inside(Vec3 a, Vec3 b, double radius) {
        return Math.abs(a.x - b.x) <= radius && Math.abs(a.y - b.y) <= radius && Math.abs(a.z - b.z) <= radius;
    }

    static boolean inSearchRange(FluidCouplingInterfaces type, Vec3 a, Vec3 b) {
        return type.locksConstraint() ? a.distanceToSqr(b) <= 9 : inside(a, b, 1.5);
    }
}
