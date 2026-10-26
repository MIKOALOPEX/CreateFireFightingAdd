package com.mikoalopex.createfirefightingadd.content.blocks.physical_hose;

import java.util.LinkedHashMap;
import java.util.Map;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseDynamicRenderer;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlock;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.HoseBracketRenderer;
import com.mikoalopex.createfirefightingadd.api.fire_hose.FireHoseEndpointModel;
import com.mikoalopex.createfirefightingadd.api.fire_hose.FireHoseAppearances;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import static com.mikoalopex.createfirefightingadd.content.blocks.physical_hose.PhysicalHoseGeometry.vector;

/** Draws the supplied mid boxes and replaces bent shoulders with continuous hose faces. */
public final class PhysicalHoseRenderer extends SafeBlockEntityRenderer<PhysicalHoseBlockEntity> {
    private static final Map<String, PartialModel> MODELS = new LinkedHashMap<>();
    static {
        for (int width : new int[] {4, 6, 8})
            for (String color : new String[] {"", "_black"})
                for (String part : new String[] {"a", "mid", "b"}) {
                    String name = width + "_" + part + color;
                    MODELS.put(name, PartialModel.of(CreateFireFightingAdd.path("block/physical_fire_hose/" + name)));
                }
        for (int width : new int[] {4, 6, 8})
            MODELS.put(width + "_head", PartialModel.of(CreateFireFightingAdd.path("block/physical_fire_hose/" + width + "_head")));
    }

    public PhysicalHoseRenderer(BlockEntityRendererProvider.Context context) {}
    public static void models(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        MODELS.values().forEach(model -> event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(model.modelLocation())));
    }

    @Override public boolean shouldRenderOffScreen(PhysicalHoseBlockEntity be) { return true; }
    @Override public boolean shouldRender(PhysicalHoseBlockEntity be, Vec3 camera) { return true; }

    @Override protected void renderSafe(PhysicalHoseBlockEntity be, float partial, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        var appearance = be.getHoseAppearance();
        if (be.getBlockState().getValue(FireHoseBlock.APPEARANCE) == FireHoseEndpointModel.CUSTOM) {
            var facing = be.getBlockState().getValue(FireHoseBlock.FACING);
            int x = facing == net.minecraft.core.Direction.UP ? 0 : facing == net.minecraft.core.Direction.DOWN ? 180 : 90;
            int y = switch (facing) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(net.minecraft.client.resources.model.BlockModelRotation.by(x, y).getRotation().getLeftRotation());
            pose.translate(-0.5, -0.5, -0.5);
            HoseBracketRenderer.renderPipe(MODELS.get(be.width() + "_head").get(), pose,
                buffers.getBuffer(RenderType.entityCutoutNoCull(appearance.endpointTexture())), light);
            pose.popPose();
        }
        var texture = appearance.hoseTexture();
        if (texture == null) return;
        var segments = PhysicalHoseGeometry.segments(be.renderPoints(partial));
        if (segments.isEmpty()) return;
        int width = be.width();
        var start = be.renderEnd(true);
        var end = be.renderEnd(false);
        if (start != null && end != null && !PhysicalHoseGeometry.ownerAtFirst(start.point(), end.point(), segments)) {
            var swap = start;
            start = end;
            end = swap;
        }
        for (int i = 0; i < segments.size(); i++) {
            var segment = segments.get(i);
            if (segment.middleLength() >= 0.75 - 1e-6)
                draw(width + "_mid", segment, pose, buffers, appearance, light, overlay);
            if (i > 0 && PhysicalHoseGeometry.straight(segments.get(i - 1), segment)) {
                draw(width + "_a", segment, pose, buffers, appearance, light, overlay);
                draw(width + "_b", segments.get(i - 1), pose, buffers, appearance, light, overlay);
            }
        }
        VertexConsumer tube = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        for (int i = 0; i < segments.size(); i++) {
            var segment = segments.get(i);
            if (segment.middleLength() < 0.75 - 1e-6)
                line(segment.midStart(), segment.midEnd(), segment.normal(), segment.up(),
                    (float) (0.5 - segment.middleLength() / 2), pose, tube, light, width);
            if (i > 0 && !PhysicalHoseGeometry.straight(segments.get(i - 1), segment)) {
                var previous = segments.get(i - 1);
                bridge(previous.midEnd(), segment.midStart(), previous.normal(), segment.normal().scale(-1),
                    previous.up(), segment.up(), pose, tube, light, width);
            }
        }
        var first = segments.getFirst();
        var last = segments.getLast();
        if (start != null) headBridge(start, first.midStart(), first.normal().scale(-1), first.up(), pose, tube, light, width);
        else line(first.start(), first.midStart(), first.normal(), first.up(), 0, pose, tube, light, width);
        if (end != null) headBridge(end, last.midEnd(), last.normal(), last.up(), pose, tube, light, width);
        else line(last.midEnd(), last.end(), last.normal(), last.up(), 0.875f, pose, tube, light, width);
    }

    private static void draw(String name, PhysicalHoseGeometry.Segment segment, PoseStack pose,
            MultiBufferSource buffers, FireHoseAppearances.Entry appearance, int light, int overlay) {
        Vector3d normal = vector(segment.normal()), up = vector(segment.up());
        var rotation = new Matrix3d(up.cross(normal, new Vector3d()), up, normal).getNormalizedRotation(new org.joml.Quaterniond());
        Vec3 center = segment.center();
        pose.pushPose();
        pose.translate(center.x, center.y, center.z);
        pose.mulPose(new Quaternionf(rotation));
        pose.translate(-0.5, -0.5, -0.5);
        if (FireHoseAppearances.endpointModel(appearance.id()) == FireHoseEndpointModel.CUSTOM)
            HoseBracketRenderer.renderPipe(MODELS.get(name).get(), pose,
                buffers.getBuffer(RenderType.entityCutoutNoCull(appearance.hoseTexture())), light);
        else
            CachedBuffers.partial(MODELS.get(name + (FireHoseAppearances.isBlack(appearance.id()) ? "_black" : "")),
                Blocks.AIR.defaultBlockState()).light(light).overlay(overlay)
                .renderInto(pose, buffers.getBuffer(RenderType.cutout()));
        pose.popPose();
    }

    private static void headBridge(PhysicalHoseBlockEntity.EndFrame head, Vec3 mid, Vec3 normal, Vec3 up,
            PoseStack pose, VertexConsumer buffer, int light, int width) {
        Vec3 direction = normal.scale(-1);
        Vec3 endUp = PhysicalHoseGeometry.alignedUp(head.normal(), head.up(), direction, up);
        // Join the two model faces directly; a spline can fold back inside a short terminal gap.
        FireHoseDynamicRenderer.renderSurfaceSegment(pose, vector(head.normal()), vector(direction),
            vector(head.up()), vector(endUp), vector(head.point()), vector(mid), new float[4], light, -1, buffer, width, width);
    }

    private static void line(Vec3 a, Vec3 b, Vec3 normal, Vec3 up, float start,
            PoseStack pose, VertexConsumer buffer, int light, int width) {
        if (a.distanceToSqr(b) < 1e-12) return;
        FireHoseDynamicRenderer.renderSegment(pose, vector(normal), vector(normal), vector(up), vector(up),
            vector(a), vector(b), start, start + (float) a.distanceTo(b), light, -1, buffer, width, width);
    }

    private static void bridge(Vec3 a, Vec3 b, Vec3 normalA, Vec3 normalB, Vec3 upA, Vec3 upB,
            PoseStack pose, VertexConsumer buffer, int light, int width) {
        var points = FireHoseDynamicRenderer.attachedSpline(a, b, normalA, normalB, upA, upB, true);
        float[] phases = {0.875f, 0.875f, 0.875f, 0.875f};
        for (int i = 0; i + 1 < points.size(); i++) {
            var from = points.get(i); var to = points.get(i + 1);
            FireHoseDynamicRenderer.renderSurfaceSegment(pose, from.normal(), to.normal(), from.up(), to.up(), from.point(), to.point(),
                phases, light, -1, buffer, width, width);
        }
    }
}
