package com.mikoalopex.createfirefightingadd.content.blocks.traffic_cone;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.PartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class TrafficConeRenderer implements BlockEntityRenderer<TrafficConeBlockEntity> {
	public TrafficConeRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(TrafficConeBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
			int packedLight, int packedOverlay) {
		if (!be.getBlockState().is(CreateFireFightingAdd.TRAFFIC_CONE.get()))
			return;

		poseStack.pushPose();
		poseStack.translate(0.5f, 0.0f, 0.5f);
		poseStack.mulPose(Axis.YP.rotationDegrees(yawFromDirection(be.getDirection())));
		poseStack.translate(-0.5f, 0.0f, -0.5f);

		SuperByteBuffer partial = CachedBuffers.partial(PartialModels.TRAFFIC_CONE, Blocks.AIR.defaultBlockState());
		partial.light(packedLight)
			.overlay(packedOverlay)
			.renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));
		poseStack.popPose();
	}

	private static float yawFromDirection(Vec3 direction) {
		Vec3 normalized = TrafficConeBlockEntity.normalizeHorizontal(direction);
		if (normalized.lengthSqr() < 1.0E-6)
			return 0;
		return (float) Math.toDegrees(Math.atan2(normalized.x, normalized.z));
	}
}
