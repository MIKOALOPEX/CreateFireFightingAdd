package com.mikoalopex.createfirefightingadd.content.kinetics.rocker;

import java.util.*;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Indexes loaded rockers for extended handle ray tests. */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class RockerTargets {
    private static final Map<Level, Map<BlockPos, Set<RockerBlockEntity>>> LEVELS = new WeakHashMap<>();
    private static final Map<RockerBlockEntity, BlockPos> CELLS = new WeakHashMap<>();
    private static BlockPos cell(Vec3 p) { return new BlockPos((int)Math.floor(p.x/8),(int)Math.floor(p.y/8),(int)Math.floor(p.z/8)); }
    static synchronized void update(RockerBlockEntity be) {
        Level world = SableStructureCompat.worldLevel(be);
        var cells = LEVELS.computeIfAbsent(world,k->new HashMap<>());
        BlockPos next = cell(SableStructureCompat.transformPositionToWorld(be,Vec3.atCenterOf(be.getBlockPos())));
        BlockPos previous = CELLS.put(be,next);
        if (next.equals(previous)) return;
        if (previous != null && cells.containsKey(previous)) { cells.get(previous).remove(be); if(cells.get(previous).isEmpty()) cells.remove(previous); }
        cells.computeIfAbsent(next,k->Collections.newSetFromMap(new IdentityHashMap<>())).add(be);
    }
    static synchronized void remove(RockerBlockEntity be) {
        BlockPos pos = CELLS.remove(be);
        var cells = LEVELS.get(SableStructureCompat.worldLevel(be));
        if(cells != null && pos != null && cells.containsKey(pos)) { cells.get(pos).remove(be); if(cells.get(pos).isEmpty()) cells.remove(pos); }
    }
    public static synchronized void clearClient() {
        LEVELS.keySet().removeIf(level -> level.isClientSide);
        CELLS.keySet().removeIf(be -> be.getLevel() == null || be.getLevel().isClientSide);
    }
    @SubscribeEvent public static synchronized void stopped(ServerStoppedEvent event) { LEVELS.clear(); CELLS.clear(); }
    public record Target(RockerBlockEntity be, Vec3 local, Vec3 world) {}
    public static synchronized Target pick(Player player) {
        var cells = LEVELS.get(player.level()); if (cells == null) return null;
        double reach = Math.min(16, player.blockInteractionRange());
        Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(reach));
        var obstruction = player.level().clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        double best = obstruction.getType() == HitResult.Type.MISS ? reach*reach : eye.distanceToSqr(obstruction.getLocation()) + 0.002;
        Target target = null;
        BlockPos center = cell(eye); int radius = (int)Math.ceil((reach+4)/8);
        for(int x=-radius;x<=radius;x++) for(int y=-radius;y<=radius;y++) for(int z=-radius;z<=radius;z++) {
            var entries = cells.get(center.offset(x,y,z)); if(entries == null) continue;
            for(var be : entries) {
                if(be.isRemoved()) continue;
                Vec3 origin = Vec3.atLowerCornerOf(be.getBlockPos());
                Vec3 worldOrigin = SableStructureCompat.transformPositionToWorld(be,origin);
                Vec3 a = SableStructureCompat.transformNormalToLocal(be,eye.subtract(worldOrigin));
                Vec3 b = SableStructureCompat.transformNormalToLocal(be,end.subtract(worldOrigin));
                var hit = RockerGeometry.clip(a,b,be.facing(),be.angle(1));
                if(hit == null) continue;
                Vec3 world = SableStructureCompat.transformPositionToWorld(be,origin.add(hit));
                double distance = eye.distanceToSqr(world);
                if(distance <= best) { best=distance; target=new Target(be,hit,world); }
            }
        }
        return target;
    }
    private RockerTargets() {}
}
