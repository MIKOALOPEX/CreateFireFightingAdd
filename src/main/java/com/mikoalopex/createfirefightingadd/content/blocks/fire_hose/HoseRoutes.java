package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** Saved separately so an unloaded bracket can discover that its hose was removed. */
public final class HoseRoutes extends SavedData {
    private final Map<UUID, HoseRoute> routes = new HashMap<>();

    public static HoseRoutes get(Level level) {
        ServerLevel server = (ServerLevel) level;
        return server.getDataStorage().computeIfAbsent(
            new Factory<>(HoseRoutes::new, HoseRoutes::load, null), "createfirefightingadd_hose_routes");
    }

    private static HoseRoutes load(CompoundTag tag, HolderLookup.Provider registries) {
        HoseRoutes data = new HoseRoutes();
        for (Tag entry : tag.getList("Routes", Tag.TAG_COMPOUND)) {
            HoseRoute route = HoseRoute.read((CompoundTag) entry);
            if (route != null && route.nodes.size() >= 2)
                data.routes.put(route.id, route);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        routes.values().forEach(route -> list.add(route.write()));
        tag.put("Routes", list);
        return tag;
    }

    public HoseRoute find(UUID id) {
        return routes.get(id);
    }

    public HoseRoute ensure(FireHoseBlockEntity hose) {
        if (hose.route != null) {
            HoseRoute current = find(hose.route.id);
            if (current != null)
                return current;
        }
        FireHoseBlockEntity partner = hose.getPairedHose();
        if (partner == null || !hose.getHoseAppearance().rendersHose())
            return null;
        HoseRoute route = new HoseRoute(UUID.randomUUID());
        route.appearance = hose.getHoseAppearanceId();
        route.nodes.add(HoseRoute.Node.of(hose));
        route.nodes.add(HoseRoute.Node.of(partner));
        routes.put(route.id, route);
        publish(hose.getLevel(), route);
        return route;
    }

    public void publish(Level level, HoseRoute route) {
        route.revision++;
        setDirty();
        for (HoseRoute.Node node : route.nodes) {
            BlockEntity be = node.resolve(level);
            if (be instanceof FireHoseBlockEntity hose) {
                hose.route = HoseRoute.read(route.write());
                hose.notifyUpdate();
            } else if (be instanceof HoseBracketBlockEntity bracket) {
                bracket.route = HoseRoute.read(route.write());
                bracket.notifyUpdate();
            }
        }
    }

    public void relocate(Level level, HoseRoute snapshot, HoseRoute.Node node) {
        if (snapshot == null)
            return;
        HoseRoute route = find(snapshot.id);
        if (route == null)
            return;
        int index = route.index(node.id());
        if (index >= 0 && !route.nodes.get(index).equals(node)) {
            route.nodes.set(index, node);
            publish(level, route);
        }
    }

    public void removeBracket(Level level, HoseRoute snapshot, UUID nodeId) {
        if (snapshot == null)
            return;
        HoseRoute route = find(snapshot.id);
        if (route != null && route.nodes.removeIf(node -> !node.endpoint() && node.id().equals(nodeId)))
            publish(level, route);
    }

    public void discard(Level level, HoseRoute snapshot) {
        if (snapshot == null)
            return;
        HoseRoute route = routes.remove(snapshot.id);
        if (route == null)
            return;
        List<HoseRoute.Node> oldNodes = List.copyOf(route.nodes);
        route.nodes.clear();
        route.revision++;
        setDirty();
        for (HoseRoute.Node node : oldNodes) {
            BlockEntity be = node.resolve(level);
            if (be instanceof FireHoseBlockEntity hose) {
                hose.route = route;
                hose.notifyUpdate();
            } else if (be instanceof HoseBracketBlockEntity) {
                level.destroyBlock(node.pos(), true);
            }
        }
    }

    public static void disconnect(FireHoseBlockEntity hose) {
        if (hose.getLevel() != null && !hose.getLevel().isClientSide && hose.route != null)
            get(hose.getLevel()).discard(hose.getLevel(), hose.route);
    }

    public static void tick(FireHoseBlockEntity hose) {
        if (hose.route == null || hose.getLevel() == null || hose.getLevel().isClientSide)
            return;
        HoseRoutes data = get(hose.getLevel());
        HoseRoute route = data.find(hose.route.id);
        if (route == null) {
            if (!hose.route.nodes.isEmpty()) {
                hose.route.nodes.clear();
                hose.notifyUpdate();
            }
            return;
        }
        if (hose.getFireHosePartnerPos() == null) {
            data.discard(hose.getLevel(), route);
            return;
        }
        data.relocate(hose.getLevel(), route, HoseRoute.Node.of(hose));
        if (!route.appearance.equals(hose.getHoseAppearanceId())) {
            if (!hose.getHoseAppearance().rendersHose())
                data.discard(hose.getLevel(), route);
            else {
                route.appearance = hose.getHoseAppearanceId();
                data.publish(hose.getLevel(), route);
            }
        } else if (hose.route.revision != route.revision) {
            hose.route = HoseRoute.read(route.write());
            hose.notifyUpdate();
        }
        if (hose.isController() && hose.getLevel().getGameTime() % 5 == 0)
            data.removeOutOfRangeBrackets(hose, route);
    }

    private void removeOutOfRangeBrackets(FireHoseBlockEntity hose, HoseRoute route) {
        FireHoseBlockEntity partner = hose.getPairedHose();
        if (partner == null || route.nodes.size() < 3)
            return;
        Vec3 first = hose.getWorldCenterVec();
        Vec3 last = partner.getWorldCenterVec();
        double limit = Config.hoseMaxLength * Config.hoseSnapMultiplier;
        List<HoseBracketBlockEntity> detached = new java.util.ArrayList<>();
        for (HoseRoute.Node node : route.nodes) {
            if (node.endpoint() || !(node.resolve(hose.getLevel()) instanceof HoseBracketBlockEntity bracket)
                || bracket.assembling)
                continue;
            Vec3 center = SableStructureCompat.transformPositionToWorld(bracket, bracket.getBlockPos().getCenter());
            if (center.distanceToSqr(first) > limit * limit || center.distanceToSqr(last) > limit * limit)
                detached.add(bracket);
        }
        if (detached.isEmpty())
            return;
        // Remove only the guides; the original fluid connection retains its own distance check.
        var ids = new java.util.HashSet<UUID>();
        detached.forEach(bracket -> ids.add(bracket.nodeId));
        route.nodes.removeIf(node -> ids.contains(node.id()));
        publish(hose.getLevel(), route);
        for (HoseBracketBlockEntity bracket : detached)
            hose.getLevel().destroyBlock(bracket.getBlockPos(), true);
    }

    public static double maximumDistance(FireHoseBlockEntity hose, double original) {
        if (hose.route == null || hose.route.nodes.size() < 3)
            return original;
        FireHoseBlockEntity partner = hose.getPairedHose();
        if (partner == null)
            return original;
        Vec3 a = hose.getWorldCenterVec();
        Vec3 b = partner.getWorldCenterVec();
        double maximum = original;
        for (HoseRoute.Node node : hose.route.nodes) {
            if (node.endpoint())
                continue;
            BlockEntity be = node.resolve(hose.getLevel());
            // Missing or mounted nodes are not evidence of a broken connection.
            if (!(be instanceof HoseBracketBlockEntity))
                continue;
            Vec3 p = SableStructureCompat.transformPositionToWorld(be, node.center());
            maximum = Math.max(maximum, Math.max(p.distanceTo(a), p.distanceTo(b)));
        }
        return maximum;
    }
}
