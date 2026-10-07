package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import com.mikoalopex.createfirefightingadd.PartialModels;
import com.mikoalopex.createfirefightingadd.api.rope.RopeHookMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.Blocks;

public class RopeLatchRenderer implements BlockEntityRenderer<RopeLatchBlockEntity> {
    public RopeLatchRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(RopeLatchBlockEntity be, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        // Match the static model's blockstate rotations before applying the local hinge rotation.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(be.getMode() == RopeHookMode.ARRESTING
            ? TailHookGeometry.rotation(be.getBlockState()) : RopeHookOrientation.rotation(be.getBlockState()));
        pose.translate(-0.5, -0.5, -0.5);
        if (be.getMode() == RopeHookMode.ARRESTING) {
            CachedBuffers.partial(PartialModels.TAIL_HOOK_BASE, Blocks.AIR.defaultBlockState())
                .light(light).overlay(overlay).renderInto(pose, buffers.getBuffer(RenderType.cutout()));
            var base = TailHookGeometry.BASE_PITCH;
            var rod = TailHookGeometry.ROD_PITCH;
            pose.translate(base.x, base.y, base.z);
            pose.mulPose(Axis.XP.rotationDegrees(-be.getRodAngle(partialTick)));
            pose.translate(-rod.x, -rod.y, -rod.z);
            CachedBuffers.partial(PartialModels.TAIL_HOOK_ROD, Blocks.AIR.defaultBlockState())
                .light(light).overlay(overlay).renderInto(pose, buffers.getBuffer(RenderType.cutout()));
            pose.popPose();
            return;
        }
        CachedBuffers.partial(PartialModels.ROPE_HOOK_BODY, Blocks.AIR.defaultBlockState())
            .light(light).overlay(overlay).renderInto(pose, buffers.getBuffer(RenderType.cutout()));
        double x = 10.86244 / 16, y = 1.15057 / 16, z = 8.0 / 16;
        pose.translate(x, y, z);
        pose.mulPose(Axis.ZP.rotationDegrees(be.getGateAngle(partialTick)));
        pose.translate(-x, -y, -z);
        CachedBuffers.partial(PartialModels.ROPE_HOOK_MOVE, Blocks.AIR.defaultBlockState())
            .light(light).overlay(overlay).renderInto(pose, buffers.getBuffer(RenderType.cutout()));
        pose.popPose();
    }
}
