package com.mikoalopex.createfirefightingadd.content.blocks.physical_hose;

import java.util.Map;
import java.util.WeakHashMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseItemHandler;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.HoseBracketInteraction;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureClientCompat;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.outliner.ChasingAABBOutline;
import net.createmod.catnip.render.PonderRenderTypes;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;
import org.joml.Vector4f;

/**
 * Selects segment centers and previews bracket placement in world space.
 * Reach indicators use straight distances; native physics determines hose sag.
 */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID, value = Dist.CLIENT)
public final class PhysicalHoseBracketClient {
    private static final Map<PhysicalHoseBlockEntity, Boolean> HOSES = new WeakHashMap<>();
    private static PhysicalHoseBlockEntity hovered;
    private static int hoveredSegment;
    public static void track(PhysicalHoseBlockEntity hose) { HOSES.put(hose, true); }
    private static InteractionHand hand() {
        var player = Minecraft.getInstance().player;
        if (player != null) for (var hand : InteractionHand.values())
            if (HoseBracketInteraction.isBracket(player.getItemInHand(hand))) return hand;
        return null;
    }
    private static PhysicalHoseAnchors.Snapshot snapshot(PhysicalHoseBlockEntity hose, float partial) {
        var offset = Vec3.atLowerCornerOf(hose.getBlockPos());
        return new PhysicalHoseAnchors.Snapshot(hose.bracketStrand, hose.renderPoints(partial).stream()
            .map(point -> SableStructureClientCompat.renderPositionToWorld(hose, point.add(offset))).toList(), hose.bracketFirstLength);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        hovered = null;
        HOSES.keySet().removeIf(hose -> hose.isRemoved() || hose.getLevel() != mc.level);
        var hand = hand();
        if (hand == null || mc.level == null || mc.player == null || mc.screen != null || !PhysicalHoses.enabled()) return;
        var selected = HoseBracketInteraction.selection(mc.player.getItemInHand(hand));
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (selected.hasUUID("PhysicalEndpoint")) {
            var hose = PhysicalHoseBracketInteraction.selected(mc.level, selected);
            if (hose != null) preview(hose, snapshot(hose, partial), selected.getInt("Segment"));
            return;
        }
        Vec3 eye = mc.player.getEyePosition(partial);
        double reach = mc.player.blockInteractionRange();
        Vec3 ray = eye.add(mc.player.getViewVector(partial).scale(reach));
        double best = reach;
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK)
            best = Math.min(best, eye.distanceTo(SableStructureCompat.projectFromBlockToWorld(mc.level, hit.getBlockPos(), hit.getLocation())));
        for (var hose : HOSES.keySet()) {
            var snapshot = snapshot(hose, partial);
            if (snapshot.rope() == null) continue;
            var segments = PhysicalHoseAnchors.indexedSegments(snapshot.points());
            for (int i = 0; i < segments.size(); i++) {
                if (PhysicalHoseAnchors.fixed(hose.anchors, i)) continue;
                var segment = segments.get(i);
                if (segment == null) continue;
                double hit = PhysicalHoseAnchors.hit(eye, ray, segment, hose.width());
                if (hit < best) { best = hit; hovered = hose; hoveredSegment = i; }
            }
        }
        if (hovered != null) {
            var segment = PhysicalHoseAnchors.indexedSegments(snapshot(hovered, partial).points()).get(hoveredSegment);
            box("physical_hose_hover", segment.center(), segment.normal(), segment.up(), hovered.width() / 32.0, FireHoseItemHandler.SUCCESS_LIME);
        }
    }
    private static void preview(PhysicalHoseBlockEntity hose, PhysicalHoseAnchors.Snapshot snapshot, int segment) {
        var mc = Minecraft.getInstance();
        if (!snapshot.valid(segment) || PhysicalHoseAnchors.fixed(hose.anchors, segment)) return;
        var geometry = PhysicalHoseAnchors.indexedSegments(snapshot.points());
        var current = geometry.get(segment);
        if (current == null) return;
        box("physical_hose_selected", current.center(), current.normal(), current.up(), hose.width() / 32.0, FireHoseItemHandler.SUCCESS_LIME);
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
        var target = PhysicalHoseAnchors.target(snapshot, segment, pos, hit.getDirection(), mc.player.getLookAngle(),
            point -> SableStructureCompat.projectFromBlockToWorld(mc.level, hit.getBlockPos(), point));
        Vec3 a = target.start(), b = target.end();
        var placement = PhysicalHoseAnchors.placement(snapshot, hose.anchors, segment, a, b);
        Vec3 center = a.add(b).scale(0.5);
        var outliner = Outliner.getInstance();
        var entry = outliner.getOutlines().get("physical_hose_target");
        PreviewOutline outline;
        if (entry != null && entry.getOutline() instanceof PreviewOutline existing) {
            outline = existing;
            outliner.keep("physical_hose_target");
        } else {
            outline = new PreviewOutline(center);
            outliner.showOutline("physical_hose_target", outline);
        }
        outline.target(new AABB(center, center));
        outline.placement = placement;
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        var hand = hand();
        if (!event.isUseItem() || hand == null || event.getHand() != hand || mc.player == null || mc.screen != null) return;
        var selection = HoseBracketInteraction.selection(mc.player.getItemInHand(hand));
        if (selection.hasUUID("PhysicalEndpoint")) {
            if (!mc.player.isShiftKeyDown()) return;
            PacketDistributor.sendToServer(new PhysicalHoseBracketPacket(BlockPos.ZERO, selection.getUUID("Strand"), -1, hand));
        } else {
            if (hovered == null || hovered.isRemoved() || hovered.bracketStrand == null) return;
            PacketDistributor.sendToServer(new PhysicalHoseBracketPacket(hovered.getBlockPos(), hovered.bracketStrand, hoveredSegment, hand));
        }
        event.setCanceled(true); event.setSwingHand(false);
    }
    private static void line(String key, Vec3 a, Vec3 b, int color) {
        Outliner.getInstance().showLine(key, a, b).colored(color).lineWidth(1 / 16f);
    }
    private static void box(String key, Vec3 center, Vec3 normal, Vec3 up, double half, int color) {
        Vec3 side = up.cross(normal).scale(half);
        up = up.scale(half); normal = normal.scale(half);
        Vec3[] corners = new Vec3[8];
        for (int i = 0; i < 8; i++) corners[i] = center.add(side.scale((i & 1) == 0 ? -1 : 1))
            .add(up.scale((i & 2) == 0 ? -1 : 1)).add(normal.scale((i & 4) == 0 ? -1 : 1));
        for (int i = 0; i < 8; i++) for (int axis = 1; axis <= 4; axis *= 2)
            if ((i & axis) == 0) line(key + "_" + i + "_" + axis, corners[i], corners[i | axis], color);
    }
    private static final class PreviewOutline extends ChasingAABBOutline {
        private PhysicalHoseAnchors.Placement placement;
        private final Vector3d start = new Vector3d();
        private final Vector3d end = new Vector3d();
        private final Vector4f lineColor = new Vector4f();

        private PreviewOutline(Vec3 center) {
            super(new AABB(center, center));
            getParams().colored(FireHoseItemHandler.SUCCESS_LIME).lineWidth(0.2f).disableLineNormals();
        }

        @Override
        protected void renderBox(PoseStack poseStack, SuperRenderTypeBuffer buffers, Vec3 camera,
                AABB bounds, Vector4f color, int light, boolean disableNormals) {
            super.renderBox(poseStack, buffers, camera, bounds, color, light, disableNormals);
            // Share the marker's interpolated center so both lines remain attached during movement.
            Vec3 center = bounds.getCenter();
            renderLine(poseStack, buffers, camera, placement.left(), center, placement.leftReachable(), color.w, light);
            renderLine(poseStack, buffers, camera, center, placement.right(), placement.rightReachable(), color.w, light);
        }

        private void renderLine(PoseStack poseStack, SuperRenderTypeBuffer buffers, Vec3 camera,
                Vec3 a, Vec3 b, boolean reachable, float alpha, int light) {
            int rgb = reachable ? FireHoseItemHandler.SUCCESS_LIME : FireHoseItemHandler.NUH_UH_RED;
            lineColor.set((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, alpha);
            start.set(a.x, a.y, a.z);
            end.set(b.x, b.y, b.z);
            bufferCuboidLine(poseStack, buffers.getBuffer(PonderRenderTypes.outlineSolid()), camera,
                start, end, getParams().getLineWidth() / 0.2f / 16f, lineColor, light, false);
        }
    }
    private PhysicalHoseBracketClient() {}
}
