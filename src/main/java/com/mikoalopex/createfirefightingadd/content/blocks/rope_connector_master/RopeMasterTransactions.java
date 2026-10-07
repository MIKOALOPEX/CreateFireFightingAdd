package com.mikoalopex.createfirefightingadd.content.blocks.rope_connector_master;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.api.rope.ManagedRopeAdapter;
import com.mikoalopex.createfirefightingadd.api.rope.ManagedRopeAdapter.Link;
import com.mikoalopex.createfirefightingadd.api.rope.ManagedRopeAdapter.Protection;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseConnectorMode;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Processes rope transactions on the server thread; endpoint revisions reject stale requests from either end. */
final class RopeMasterTransactions {
    static final String CLAIM = "createfirefightingadd:rope_manager";
    private static final String STATE = "createfirefightingadd:rope_operation";
    private static final Map<ServerLevel, Budget> BUDGETS = new WeakHashMap<>();
    private static final int ACTIONS_PER_TICK = 4;
    private static final int CHUNKS_PER_SEARCH = 128;
    private static final int ENTITIES_PER_SEARCH = 1024;
    private static final int COOLDOWN_TICKS = 100;

    private RopeMasterTransactions() {}

    record Request(BlockPos endpoint, BlockEntity expected, ManagedRopeAdapter adapter, UUID rope, List<UUID> existing, long revision, long expires,
                   FireHoseConnectorMode mode) {}
    private static final class Budget { long tick = Long.MIN_VALUE; int actions; }

    static boolean takeBudget(ServerLevel level) {
        Budget budget = BUDGETS.computeIfAbsent(level, ignored -> new Budget());
        if (budget.tick != level.getGameTime()) { budget.tick = level.getGameTime(); budget.actions = 0; }
        return budget.actions++ < ACTIONS_PER_TICK;
    }

    static Request capture(RopeMasterBlockEntity master) {
        ServerLevel level = (ServerLevel) master.getLevel();
        BlockEntity endpoint = loaded(level, master.endpointPos());
        if (endpoint == null) { master.fail("no_endpoint"); return null; }
        if (cooling(endpoint, level.getGameTime())) { master.highlight(false); return null; }
        for (ManagedRopeAdapter adapter : RopeMasterCompatibility.adapters()) {
            if (!adapter.supports(endpoint)) continue;
            List<UUID> links = adapter.connections(endpoint);
            UUID rope = master.hasSelection() ? links.contains(master.managedRope()) ? master.managedRope() : null
                : links.isEmpty() ? null : links.getFirst();
            return new Request(endpoint.getBlockPos(), endpoint, adapter, rope, List.copyOf(links), revision(endpoint),
                level.getGameTime() + 100, master.mode());
        }
        master.fail("unsupported");
        return null;
    }

    static void execute(RopeMasterBlockEntity master, Request request) {
        ServerLevel level = (ServerLevel) master.getLevel();
        BlockEntity first = loaded(level, request.endpoint());
        if (level.getGameTime() > request.expires() || !request.endpoint().equals(master.endpointPos())
                || first != request.expected() || first.isRemoved() || revision(first) != request.revision()
                || cooling(first, level.getGameTime())) { master.highlight(false); return; }
        ManagedRopeAdapter adapter = request.adapter();
        if (!adapter.supports(first)) { master.fail("unsupported"); return; }
        if (request.rope() != null) {
            Link link = adapter.inspect(level, request.rope());
            if (link == null || link.other(first.getBlockPos()) == null) { master.fail("failed"); return; }
            if (link.protection() != Protection.NONE) { master.fail(reason(link.protection())); return; }
            BlockEntity second = loaded(level, link.other(first.getBlockPos()));
            if (second == null || cooling(second, level.getGameTime())) { master.highlight(false); return; }
            // Reserve both endpoint revisions before any native callbacks can re-enter redstone handling.
            stamp(level, first, second);
            try {
                if (!adapter.remove(level, link)) { master.fail("failed"); return; }
            } catch (RuntimeException error) {
                CreateFireFightingAdd.LOGGER.error(
                    "Rope removal failed at {} for {}; no refund was issued", master.getBlockPos(), link.id(), error);
                master.fail("failed");
                return;
            }
            rememberBoth(level, first, second, null, link.id());
            master.select(null);
            master.remember(second.getBlockPos());
            master.refund(link.material());
            master.success();
            return;
        }
        if (request.mode() == FireHoseConnectorMode.IDLE) { master.highlight(false); return; }
        // A delayed connect request never becomes a disconnect or adds a second smart link implicitly.
        if (!adapter.connections(first).equals(request.existing()) || !adapter.hasCapacity(first)) {
            master.highlight(false);
            return;
        }
        ItemStack available = master.inventory.getItem(0);
        ManagedRopeAdapter materialAdapter = RopeMasterCompatibility.adapters().stream()
            .filter(a -> a.accepts(available) && a.supports(first)).findFirst().orElse(null);
        if (materialAdapter == null) { master.fail("no_material"); return; }
        adapter = materialAdapter;
        BlockEntity second = loaded(level, master.cachedPartner());
        if (!eligible(level, adapter, first, second)) second = null;
        if (second == null && request.mode() == FireHoseConnectorMode.FREE) second = find(level, adapter, first);
        if (second == null) { master.fail("no_target"); return; }
        // No inventory or network work is deferred beyond this point.
        stamp(level, first, second);
        ItemStack reserved = master.inventory.removeItem(0, 1);
        if (reserved.isEmpty()) { master.fail("no_material"); return; }
        Link created;
        try { created = adapter.create(level, first, second, reserved); }
        catch (RuntimeException error) {
            // An adapter exception may have happened after creation. Never refund an unproven failure.
            CreateFireFightingAdd.LOGGER.error(
                "Rope creation failed at {} using {}; material was not refunded to avoid duplication",
                master.getBlockPos(), adapter.id(), error);
            master.fail("failed");
            return;
        }
        if (created == null) { master.refund(reserved); master.fail("failed"); return; }
        rememberBoth(level, first, second, created.id(), null);
        master.select(created.id());
        master.remember(second.getBlockPos());
        master.success();
    }

    private static String reason(Protection protection) {
        return switch (protection) { case WINCH -> "winch"; case HOOK -> "hook"; default -> "unsupported"; };
    }

    private static boolean eligible(ServerLevel level, ManagedRopeAdapter adapter, BlockEntity first, BlockEntity second) {
        if (second == null || first == second || !adapter.supports(second) || !adapter.hasCapacity(second)
                || cooling(second, level.getGameTime())) return false;
        double range = adapter.range();
        if (!Double.isFinite(range) || range <= 0 || SableStructureCompat.distanceSquared(level,
                first.getBlockPos(), second.getBlockPos()) >= range * range) return false;
        for (UUID id : adapter.connections(second)) {
            Link link = adapter.inspect(level, id);
            if (link == null || link.protection() != Protection.NONE || first.getBlockPos().equals(link.other(second.getBlockPos())))
                return false;
        }
        return true;
    }

    private static BlockEntity find(ServerLevel level, ManagedRopeAdapter adapter, BlockEntity first) {
        // Native creation still enforces its own range; broadphase work has a separate hard spatial bound.
        double radius = Math.min(64, adapter.range());
        if (!Double.isFinite(radius) || radius <= 0) return null;
        Vec3 world = SableStructureCompat.projectToWorld(level, first.getBlockPos().getCenter());
        List<SearchSpace> spaces = new ArrayList<>();
        spaces.add(new SearchSpace(level, world));
        for (var projection : SableStructureCompat.projectProcessingPositions(level, List.of(world), radius)) {
            if (spaces.size() >= 17) break;
            spaces.add(new SearchSpace(projection.level(), projection.positions().getFirst()));
        }
        List<Candidate> candidates = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<Long> chunks = new HashSet<>();
        int checked = 0;
        int localRadius = (int) Math.ceil(radius / 16) + 1;
        outer: for (int ring = 0; ring <= localRadius; ring++) {
            for (SearchSpace space : spaces) {
                BlockPos center = BlockPos.containing(space.center());
                for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int x = (center.getX() >> 4) + dx, z = (center.getZ() >> 4) + dz;
                    if (!chunks.add(net.minecraft.world.level.ChunkPos.asLong(x, z))) continue;
                    if (chunks.size() > CHUNKS_PER_SEARCH) break outer;
                    var chunk = space.level().getChunkSource().getChunkNow(x, z);
                    if (chunk == null) continue;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (++checked > ENTITIES_PER_SEARCH) break outer;
                        if (visited.add(be.getBlockPos()) && eligible(level, adapter, first, be))
                            candidates.add(new Candidate(be, managed(level, be),
                                SableStructureCompat.distanceSquared(level, first.getBlockPos(), be.getBlockPos())));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparing(Candidate::managed).thenComparingDouble(Candidate::distance)
            .thenComparingLong(candidate -> candidate.endpoint().getBlockPos().asLong()));
        return candidates.isEmpty() ? null : candidates.getFirst().endpoint();
    }

    private record SearchSpace(Level level, Vec3 center) {}
    private record Candidate(BlockEntity endpoint, boolean managed, double distance) {}

    private static boolean managed(ServerLevel level, BlockEntity endpoint) {
        CompoundTag claim = endpoint.getPersistentData().getCompound(CLAIM);
        if (!claim.hasUUID("Manager")) {
            // Also handles endpoints placed after their neighbouring manager initialized.
            for (Direction direction : Direction.values()) {
                BlockEntity adjacent = loaded(level, endpoint.getBlockPos().relative(direction));
                if (adjacent instanceof RopeMasterBlockEntity master && master.endpointPos().equals(endpoint.getBlockPos())) {
                    master.refreshClaim();
                    return true;
                }
            }
            return false;
        }
        BlockPos position = BlockPos.of(claim.getLong("Position"));
        if (!level.isLoaded(position)) return true;
        if (loaded(level, position) instanceof RopeMasterBlockEntity master
                && master.identity().equals(claim.getUUID("Manager")) && master.endpointPos().equals(endpoint.getBlockPos())) return true;
        endpoint.getPersistentData().remove(CLAIM);
        endpoint.setChanged();
        return false;
    }

    private static void rememberBoth(ServerLevel level, BlockEntity first, BlockEntity second, UUID created, UUID removed) {
        for (BlockEntity endpoint : List.of(first, second))
            for (Direction direction : Direction.values()) {
                BlockEntity be = loaded(level, endpoint.getBlockPos().relative(direction));
                if (be instanceof RopeMasterBlockEntity master && master.endpointPos().equals(endpoint.getBlockPos())
                        && (!master.hasSelection() || master.managedRope() == null || master.managedRope().equals(removed))) {
                    master.remember(endpoint == first ? second.getBlockPos() : first.getBlockPos());
                    master.select(created);
                    master.success();
                }
            }
    }

    private static BlockEntity loaded(ServerLevel level, BlockPos pos) {
        return pos != null && level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
    }
    private static long revision(BlockEntity endpoint) { return endpoint.getPersistentData().getCompound(STATE).getLong("Revision"); }
    private static boolean cooling(BlockEntity endpoint, long now) {
        return endpoint.getPersistentData().getCompound(STATE).getLong("Until") > now;
    }
    private static void stamp(ServerLevel level, BlockEntity first, BlockEntity second) {
        for (BlockEntity endpoint : List.of(first, second)) {
            CompoundTag state = new CompoundTag();
            state.putLong("Revision", revision(endpoint) + 1);
            state.putLong("Until", level.getGameTime() + COOLDOWN_TICKS);
            endpoint.getPersistentData().put(STATE, state);
            endpoint.setChanged();
        }
    }
}
