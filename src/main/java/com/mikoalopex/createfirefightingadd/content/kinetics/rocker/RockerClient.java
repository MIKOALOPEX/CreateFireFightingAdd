package com.mikoalopex.createfirefightingadd.content.kinetics.rocker;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllKeys;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=CreateFireFightingAdd.MODID,value=Dist.CLIENT)
public final class RockerClient {
    private static RockerTargets.Target hovered;
    public static boolean isHovered(RockerBlockEntity be) {
        return outlinesVisible() && hovered != null && hovered.be() == be;
    }
    private static boolean outlinesVisible() {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.screen == null && !mc.options.hideGui && !AllKeys.ROTATE_MENU.isPressed();
    }
    @SubscribeEvent public static void renderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY && !outlinesVisible())
            for (var key : EDGES) Outliner.getInstance().remove(key);
    }
    private static final Object[] EDGES = new Object[12];
    static { for(int i=0;i<12;i++) EDGES[i]=new Object(); }
    @SubscribeEvent public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        var mc=Minecraft.getInstance();
        if(!event.isUseItem() || mc.player==null || mc.screen!=null || AllKeys.ROTATE_MENU.isPressed() || mc.player.getMainHandItem().is(AllItems.WRENCH.get())) return;
        var hit=RockerTargets.pick(mc.player);
        if(hit==null || hit.be().dial(hit.local())) return;
        event.setCanceled(true); event.setSwingHand(true);
        PacketDistributor.sendToServer(new RockerUsePacket());
    }
    public static void tick() {
        var mc=Minecraft.getInstance();
        for(var key:EDGES) Outliner.getInstance().remove(key);
        hovered = null;
        if(mc.level==null) { RockerTargets.clearClient(); return; }
        if(!outlinesVisible()) return;
        // Native core targeting takes priority over the extended rod ray.
        RockerBlockEntity core = null;
        Vec3 local = null;
        if (mc.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK
            && mc.level.getBlockEntity(blockHit.getBlockPos()) instanceof RockerBlockEntity be) {
            core = be;
            local = blockHit.getLocation().subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
            hovered = new RockerTargets.Target(be, local,
                SableStructureCompat.transformPositionToWorld(be, blockHit.getLocation()));
        } else {
            hovered = RockerTargets.pick(mc.player);
        }
        if(!mc.player.getMainHandItem().is(AllItems.WRENCH.get())) return;
        if (core == null) return;
        var be = core;
        var coreBounds = be.getBlockState().getShape(be.getLevel(), be.getBlockPos()).bounds();
        int side=RockerGeometry.selectionSide(local,be.facing(),coreBounds);
        if(side==0) return;
        var bounds = RockerGeometry.coreHalf(be.facing(), side, coreBounds);
        Vec3[] corners=new Vec3[8];
        for(int i=0;i<8;i++) {
            Vec3 v=RockerGeometry.point(new Vec3((i&1)==0?bounds.minX:bounds.maxX,
                (i&2)==0?bounds.minY:bounds.maxY,(i&4)==0?bounds.minZ:bounds.maxZ),be.facing(),0);
            corners[i]=SableStructureCompat.transformPositionToWorld(be,Vec3.atLowerCornerOf(be.getBlockPos()).add(v));
        }
        int key=0;
        for(int i=0;i<8;i++) for(int bit=1;bit<=4;bit*=2) if((i&bit)==0)
            Outliner.getInstance().showLine(EDGES[key++],corners[i],corners[i|bit]).colored(0x70FF33).lineWidth(1/16f).disableLineNormals();
    }
    private RockerClient() {}
}
