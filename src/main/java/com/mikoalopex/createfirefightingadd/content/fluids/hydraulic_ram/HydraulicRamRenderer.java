package com.mikoalopex.createfirefightingadd.content.fluids.hydraulic_ram;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.PartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class HydraulicRamRenderer extends SmartBlockEntityRenderer<HydraulicRamBlockEntity> {

	public HydraulicRamRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(HydraulicRamBlockEntity be, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, int packedOverlay) {
		BlockState state = be.getBlockState();
		if (!state.is(CreateFireFightingAdd.HYDRAULIC_RAM.get()))
			return;

		poseStack.pushPose();
		rotateToFacing(poseStack, state.getValue(HydraulicRamBlock.FACING));
		poseStack.translate(0, be.getStrokeOffset(partialTick), 0);

		SuperByteBuffer partial = CachedBuffers.partial(PartialModels.HYDRAULIC_RAM_MOVE, Blocks.AIR.defaultBlockState());
		partial.light(packedLight)
			.overlay(packedOverlay)
			.renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));

		poseStack.popPose();
	}

	private static void rotateToFacing(PoseStack poseStack, Direction facing) {
		float angle = switch (facing) {
			case NORTH -> 180;
			case EAST -> 90;
			case WEST -> 270;
			default -> 0;
		};
		if (angle == 0)
			return;
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.mulPose(Axis.YP.rotationDegrees(angle));
		poseStack.translate(-0.5f, -0.5f, -0.5f);
	}
}
