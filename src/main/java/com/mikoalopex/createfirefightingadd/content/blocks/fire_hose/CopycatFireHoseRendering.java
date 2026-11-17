package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Maps each material face onto the tube with its own orientation and native pixel density. */
final class CopycatFireHoseRendering implements VertexConsumer {
    private final VertexConsumer delegate;
    private final CopycatHoseUv.FaceUv[] faces = new CopycatHoseUv.FaceUv[4];
    private final int[] colors = new int[4];
    private final int emission;
    private final int tubeWidth;
    private final float textureU0;
    private final float textureU1;
    private int face;
    final RenderType type;

    CopycatFireHoseRendering(Level level, BlockPos pos, BlockState material, MultiBufferSource buffers,
            float tubeWidth) {
        var minecraft = Minecraft.getInstance();
        var model = minecraft.getBlockRenderer().getBlockModel(material);
        var data = model.getModelData(level, pos, material, ModelData.EMPTY);
        type = ItemBlockRenderTypes.getRenderType(material, false);
        delegate = buffers.getBuffer(type);
        emission = Math.clamp(material.getLightEmission(level, pos), 0, 15) << 4;
        this.tubeWidth = Math.round(tubeWidth);
        textureU0 = HoseTexture.u0(this.tubeWidth);
        textureU1 = HoseTexture.u1(this.tubeWidth);
        Direction[] sides = {Direction.DOWN, Direction.UP, Direction.WEST, Direction.EAST};
        for (int i = 0; i < 4; i++) {
            BakedQuad selected = null;
            for (var layer : model.getRenderTypes(material, RandomSource.create(42), data)) {
                var quads = model.getQuads(material, sides[i], RandomSource.create(42), data, layer);
                if (!quads.isEmpty()) { selected = quads.getFirst(); break; }
                for (var quad : model.getQuads(material, null, RandomSource.create(42), data, layer))
                    if (quad.getDirection() == sides[i]) { selected = quad; break; }
                if (selected != null) break;
            }
            var sprite = selected == null ? model.getParticleIcon(data) : selected.getSprite();
            faces[i] = selected == null ? CopycatHoseUv.sprite(sprite) : CopycatHoseUv.of(selected, sides[i]);
            colors[i] = selected != null && selected.isTinted()
                ? minecraft.getBlockColors().getColor(material, level, pos, selected.getTintIndex()) : 0xFFFFFF;
        }
    }

    void select(int side) { face = side; }
    @Override public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z); return this; }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) {
        int tint = colors[face];
        delegate.setColor(r * (tint >> 16 & 255) / 255, g * (tint >> 8 & 255) / 255, b * (tint & 255) / 255, a); return this;
    }
    @Override public VertexConsumer setUv(float u, float v) {
        var faceUv = faces[face];
        // The caller's u already encodes the drawn width relative to the ordinary
        // hose texture. Convert it to the same fraction of the material face so
        // 4/6/8 all keep the block's texel density instead of a fixed middle half.
        float across = textureU1 > textureU0
            ? Math.clamp((u - textureU0) / (textureU1 - textureU0), 0f, 1f) : 0.5f;
        float materialAcross = CopycatHoseUv.across(across, tubeWidth, faceUv.acrossExtent());
        delegate.setUv(faceUv.u(materialAcross, v), faceUv.v(materialAcross, v));
        return this;
    }
    @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
    @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(Math.max(u, emission), v); return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
}
