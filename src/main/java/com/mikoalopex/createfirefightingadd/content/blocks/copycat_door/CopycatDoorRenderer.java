package com.mikoalopex.createfirefightingadd.content.blocks.copycat_door;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;

public class CopycatDoorRenderer implements BlockEntityRenderer<CopycatDoorBlockEntity> {
    public CopycatDoorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CopycatDoorBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = entity.getBlockState();
        if (state.getBlock() instanceof MultidirectionalTrapdoorBlock || state.getValue(CopycatDoorBlock.VISIBLE)) return;
        var mc = Minecraft.getInstance();
        if (!(mc.getBlockRenderer().getBlockModel(state) instanceof CopycatDoorModel model)) return;
        var material = entity.getMaterial();
        var source = mc.getBlockRenderer().getBlockModel(material);
        var data = source.getModelData(entity.getLevel(), entity.getBlockPos(), material, ModelData.EMPTY);
        var random = RandomSource.create(42);
        float progress = entity.progress(partialTick);
        var consumer = buffers.getBuffer(ItemBlockRenderTypes.getRenderType(material, false));
        for (var layer : source.getRenderTypes(material, random, data)) {
            for (int side = -1; side < 6; side++) {
                random.setSeed(42);
                for (var quad : model.quads(state, side < 0 ? null : Direction.from3DDataValue(side), random, material, data, layer, progress)) {
                    int tint = quad.isTinted() ? mc.getBlockColors().getColor(material, entity.getLevel(), entity.getBlockPos(), quad.getTintIndex()) : 0xFFFFFF;
                    consumer.putBulkData(pose.last(), quad, (tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f,
                        (tint & 255) / 255f, 1, light, overlay);
                }
            }
        }
    }
}
