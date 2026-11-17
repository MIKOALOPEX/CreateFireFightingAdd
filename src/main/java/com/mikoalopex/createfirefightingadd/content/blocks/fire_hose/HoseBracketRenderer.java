package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.mikoalopex.createfirefightingadd.PartialModels;
import com.mikoalopex.createfirefightingadd.api.fire_hose.FireHoseAppearances;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllBlocks;

import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders the bracket frame and ordinary hose insert using the connected hose
 * appearance, or the route's copycat body material when one is filled.
 */
public class HoseBracketRenderer implements BlockEntityRenderer<HoseBracketBlockEntity> {
    public HoseBracketRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(HoseBracketBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        renderModel(be.getBlockState(), be.angle(), be.route, be.getBlockPos(), pose, buffers, light, true);
    }

    public static void renderModel(BlockState state, float angle, HoseRoute route, PoseStack pose,
                                   MultiBufferSource buffers, int light) {
        renderModel(state, angle, route, BlockPos.ZERO, pose, buffers, light, true);
    }

    public static void renderModel(BlockState state, float angle, HoseRoute route, BlockPos pos, PoseStack pose,
                                   MultiBufferSource buffers, int light) {
        renderModel(state, angle, route, pos, pose, buffers, light, true);
    }

    private static void renderModel(BlockState state, float angle, HoseRoute route, BlockPos pos, PoseStack pose,
                                   MultiBufferSource buffers, int light, boolean pipeVisible) {
        Direction face = state.getValue(HoseBracketBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(HoseBracketBlock.rotation(face, angle));
        pose.translate(-0.5, -0.5, -0.5);
        var bracket = state.getValue(HoseBracketBlock.WOODEN) ? PartialModels.HOSE_BRACKET_WOOD : PartialModels.HOSE_BRACKET_METAL;
        CachedBuffers.partial(bracket, Blocks.AIR.defaultBlockState()).light(light)
            .renderInto(pose, buffers.getBuffer(RenderType.cutoutMipped()));

        var appearance = FireHoseAppearances.get(route == null ? FireHoseAppearances.DEFAULT : route.appearance);
        int width = route == null ? FireHoseSize.EIGHT.width() : route.size;
        Level level = Minecraft.getInstance().level;
        BlockState material = route == null || level == null ? null : CopycatFireHose.material(level, route);
        if (material == null && route != null && route.copycatRoute)
            material = AllBlocks.COPYCAT_BASE.getDefaultState();
        if (pipeVisible && (appearance.rendersHose() || material != null)) {
            boolean useZModel = face.getAxis() != Direction.Axis.Z;
            BakedModel pipe = PartialModels.hoseSetpoint(useZModel, width).get();
            if (!useZModel) {
                pose.translate(0.5, 0.5, 0.5);
                pose.mulPose(Axis.XP.rotationDegrees(90));
                pose.translate(-0.5, -0.5, -0.5);
            }
            if (material != null && level != null) {
                new CopycatFireHoseTemplateRendering(level, pos, material, buffers)
                    .draw(pipe, pose, light, OverlayTexture.NO_OVERLAY);
            } else {
                renderPipe(pipe, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(appearance.hoseTexture())), light);
            }
        }
        pose.popPose();
    }

    /** Preserves model UVs while substituting the hose appearance texture. */
    public static void renderPipe(BakedModel model, PoseStack pose, VertexConsumer buffer, int light) {
        RandomSource random = RandomSource.create(42);
        for (int side = -1; side < 6; side++) {
            random.setSeed(42);
            for (var quad : model.getQuads(null, side < 0 ? null : Direction.from3DDataValue(side), random)) {
                int[] vertices = quad.getVertices();
                int stride = vertices.length / 4;
                var sprite = quad.getSprite();
                Direction normal = quad.getDirection();
                for (int i = 0; i < 4; i++) {
                    int offset = i * stride;
                    float u = (Float.intBitsToFloat(vertices[offset + 4]) - sprite.getU0()) / (sprite.getU1() - sprite.getU0());
                    float v = (Float.intBitsToFloat(vertices[offset + 5]) - sprite.getV0()) / (sprite.getV1() - sprite.getV0());
                    buffer.addVertex(pose.last().pose(), Float.intBitsToFloat(vertices[offset]),
                        Float.intBitsToFloat(vertices[offset + 1]), Float.intBitsToFloat(vertices[offset + 2]))
                        .setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose.last(), normal.getStepX(), normal.getStepY(), normal.getStepZ());
                }
            }
        }
    }
}
