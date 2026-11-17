package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Maps the stored copycat material onto a baked template shape. Both the port
 * silhouette and the bracket insert reuse this, so their faces sample the
 * material's own atlas region instead of a generated texture.
 */
final class CopycatFireHoseTemplateRendering {
	private static final int NO_TINT = 0xFFFFFF;

	private final BlockState material;
	private final Level level;
	private final BlockPos pos;
	private final BakedModel source;
	private final ModelData data;
	private final VertexConsumer buffer;
	private final Map<Direction, List<BakedQuad>> faces = new EnumMap<>(Direction.class);
	private final int lightFloor;

	CopycatFireHoseTemplateRendering(Level level, BlockPos pos, BlockState material,
			MultiBufferSource buffers) {
		this.level = level;
		this.pos = pos;
		this.material = material;
		Minecraft minecraft = Minecraft.getInstance();
		source = minecraft.getBlockRenderer().getBlockModel(material);
		data = source.getModelData(level, pos, material, ModelData.EMPTY);
		buffer = buffers.getBuffer(ItemBlockRenderTypes.getRenderType(material, false));
		lightFloor = Math.clamp(material.getLightEmission(level, pos), 0, 15) << 4;
		RandomSource random = RandomSource.create(42);
		for (var type : source.getRenderTypes(material, random, data)) {
			for (int side = -1; side < 6; side++) {
				random.setSeed(42);
				for (BakedQuad quad : source.getQuads(material,
					side < 0 ? null : Direction.from3DDataValue(side), random, data, type))
					faces.computeIfAbsent(quad.getDirection(), key -> new ArrayList<>()).add(quad);
			}
		}
	}

	void draw(BakedModel template, PoseStack pose, int light, int overlay) {
		int packedLight = (light & 0xFFFF0000) | Math.max(light & 0xFFFF, lightFloor);
		RandomSource random = RandomSource.create(42);
		for (int side = -1; side < 6; side++) {
			random.setSeed(42);
			for (BakedQuad shape : template.getQuads(null,
				side < 0 ? null : Direction.from3DDataValue(side), random)) {
				Direction direction = shape.getDirection();
				if (direction == null)
					continue;
				for (BakedQuad face : faces.getOrDefault(direction, List.of())) {
					BakedQuad mapped = map(shape, face);
					int tint = tint(face);
					buffer.putBulkData(pose.last(), mapped,
						(tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f,
						(tint & 255) / 255f, 1f, packedLight, overlay);
				}
			}
		}
	}

	private int tint(BakedQuad quad) {
		if (!quad.isTinted())
			return NO_TINT;
		return Minecraft.getInstance().getBlockColors()
			.getColor(material, level, pos, quad.getTintIndex());
	}

	static BakedQuad map(BakedQuad shape, BakedQuad face) {
		int[] output = shape.getVertices().clone();
		int[] input = face.getVertices();
		int stride = output.length / 4;
		int sourceStride = input.length / 4;
		Vector3f origin = xyz(input, 0);
		Vector3f a = xyz(input, 1).sub(origin);
		Vector3f b = xyz(input, 3).sub(origin);
		float aa = a.dot(a);
		float ab = a.dot(b);
		float bb = b.dot(b);
		float determinant = aa * bb - ab * ab;
		if (Math.abs(determinant) < 1e-12)
			return shape;
		for (int i = 0; i < 4; i++) {
			Vector3f point = xyz(output, i).sub(origin);
			float u = (point.dot(a) * bb - point.dot(b) * ab) / determinant;
			float v = (point.dot(b) * aa - point.dot(a) * ab) / determinant;
			for (int coordinate = 4; coordinate <= 5; coordinate++) {
				float start = Float.intBitsToFloat(input[coordinate]);
				float alongU = Float.intBitsToFloat(input[sourceStride + coordinate]);
				float alongV = Float.intBitsToFloat(input[3 * sourceStride + coordinate]);
				output[i * stride + coordinate] = Float.floatToRawIntBits(
					start + u * (alongU - start) + v * (alongV - start));
			}
		}
		return new BakedQuad(output, face.getTintIndex(), shape.getDirection(),
			face.getSprite(), face.isShade());
	}

	private static Vector3f xyz(int[] vertices, int index) {
		int stride = vertices.length / 4;
		int offset = index * stride;
		return new Vector3f(
			Float.intBitsToFloat(vertices[offset]),
			Float.intBitsToFloat(vertices[offset + 1]),
			Float.intBitsToFloat(vertices[offset + 2]));
	}
}
