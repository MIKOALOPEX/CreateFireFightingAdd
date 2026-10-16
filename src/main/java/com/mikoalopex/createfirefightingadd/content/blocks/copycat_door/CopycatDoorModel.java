package com.mikoalopex.createfirefightingadd.content.blocks.copycat_door;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import com.simibubi.create.foundation.model.BakedModelHelper;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

public class CopycatDoorModel extends CopycatModel {
    public CopycatDoorModel(BakedModel original) { super(original); }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        BlockState material = getMaterial(data);
        return getModelOf(material).getRenderTypes(material, random, ModelData.EMPTY);
    }

    @Override
    protected List<BakedQuad> getCroppedQuads(BlockState state, Direction side, RandomSource random,
            BlockState material, ModelData data, RenderType type) {
        return quads(state, side, random, material, data, type, state.getValue(CopycatDoorBlock.OPEN) ? 1 : 0);
    }

    public List<BakedQuad> quads(BlockState state, Direction side, RandomSource random,
            BlockState material, ModelData data, RenderType type, float progress) {
        boolean base = material.is(AllBlocks.COPYCAT_BASE.get());
        BakedModel source = base ? originalModel : getModelOf(material);
        var input = source.getQuads(base ? state : material, side, random, data, type);
        List<BakedQuad> result = new ArrayList<>();
        for (DoorGeometry.Part part : DoorGeometry.parts(state, progress)) {
            for (BakedQuad quad : input) {
                AABB box = part.bounds();
                Vec3 move = Vec3.ZERO;
                if (base) {
                    double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
                    for (int vertex = 0; vertex < 4; vertex++) {
                        double x = BakedQuadHelper.getXYZ(quad.getVertices(), vertex).x;
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    }
                    if (maxX < box.minX + 1e-6 && minX < box.minX || minX > box.maxX - 1e-6 && maxX > box.maxX) continue;
                } else if (quad.getDirection() == Direction.SOUTH) {
                    box = new AABB(box.minX, 0, 1 - DoorGeometry.THICKNESS, box.maxX, 1, 1);
                    move = new Vec3(0, 0, DoorGeometry.THICKNESS - 1);
                } else if (quad.getDirection() == Direction.EAST) {
                    double width = box.getXsize();
                    move = new Vec3(box.maxX - 1, 0, 0);
                    box = new AABB(1 - width, 0, 0, 1, 1, DoorGeometry.THICKNESS);
                } else if (quad.getDirection() == Direction.WEST) {
                    move = new Vec3(box.minX, 0, 0);
                    box = new AABB(0, 0, 0, box.getXsize(), 1, DoorGeometry.THICKNESS);
                }
                int[] vertices = BakedModelHelper.cropAndMove(quad.getVertices(), quad.getSprite(), box, move);
                for (int vertex = 0; vertex < 4; vertex++) {
                    Vec3 p = BakedQuadHelper.getXYZ(vertices, vertex);
                    BakedQuadHelper.setXYZ(vertices, vertex, DoorGeometry.point(part.transform(), p.x, p.y, p.z));
                }
                Vector3f normal = part.transform().transformDirection(new Vector3f(quad.getDirection().getStepX(),
                    quad.getDirection().getStepY(), quad.getDirection().getStepZ())).normalize();
                int stride = vertices.length / 4;
                int packedNormal = ((byte) (normal.x * 127) & 255) | (((byte) (normal.y * 127) & 255) << 8)
                    | (((byte) (normal.z * 127) & 255) << 16);
                for (int vertex = 0; vertex < 4; vertex++) vertices[vertex * stride + 7] = packedNormal;
                if (part.transform().determinant() < 0) {
                    for (int i = 0; i < stride; i++) {
                        int temp = vertices[stride + i]; vertices[stride + i] = vertices[3 * stride + i]; vertices[3 * stride + i] = temp;
                    }
                }
                result.add(new BakedQuad(vertices, quad.getTintIndex(), Direction.getNearest(normal.x, normal.y, normal.z),
                    quad.getSprite(), quad.isShade()));
            }
        }
        return result;
    }
}
