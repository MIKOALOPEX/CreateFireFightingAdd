package com.mikoalopex.createfirefightingadd.content.equipment.backtank;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.PartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class MultipurposeBacktankRenderer extends SmartBlockEntityRenderer<MultipurposeBacktankBlockEntity> {
	private static final float COG_PIVOT_Y = 7.5f / 16f;
	private static final float COG_PIVOT_Z = 12f / 16f;

	public MultipurposeBacktankRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(MultipurposeBacktankBlockEntity be, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, int packedOverlay) {
		BlockState state = be.getBlockState();
		if (!state.is(CreateFireFightingAdd.MULTIPURPOSE_BACKTANK.get()))
			return;

		poseStack.pushPose();
		rotateToFacing(poseStack, state.getValue(MultipurposeBacktankBlock.HORIZONTAL_FACING));

		renderShaft(be, state, poseStack, buffer, packedLight, packedOverlay);
		renderCog(be, state, poseStack, buffer, packedLight, packedOverlay);

		poseStack.popPose();
	}

	private static void renderShaft(MultipurposeBacktankBlockEntity be, BlockState state, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, int packedOverlay) {
		float angle = KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), Direction.Axis.Y);
		poseStack.pushPose();
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.mulPose(Axis.YP.rotation(angle));
		poseStack.translate(-0.5f, -0.5f, -0.5f);
		renderPartial(PartialModels.MULTIPURPOSE_BACKTANK_SHAFT, state, poseStack, buffer, packedLight, packedOverlay);
		poseStack.popPose();
	}

	private static void renderCog(MultipurposeBacktankBlockEntity be, BlockState state, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, int packedOverlay) {
		float angle = KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), Direction.Axis.X);
		poseStack.pushPose();
		poseStack.translate(0, COG_PIVOT_Y, COG_PIVOT_Z);
		poseStack.mulPose(Axis.XP.rotation(angle));
		poseStack.translate(0, -COG_PIVOT_Y, -COG_PIVOT_Z);
		renderPartial(PartialModels.MULTIPURPOSE_BACKTANK_COG, state, poseStack, buffer, packedLight, packedOverlay);
		poseStack.popPose();
	}

	private static void renderPartial(PartialModel model, BlockState state, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, int packedOverlay) {
		SuperByteBuffer partial = CachedBuffers.partial(model, Blocks.AIR.defaultBlockState());
		partial.light(packedLight)
			.overlay(packedOverlay)
			.renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));
	}

	private static void rotateToFacing(PoseStack poseStack, Direction facing) {
		float angle = switch (facing) {
			case EAST -> 270;
			case SOUTH -> 180;
			case WEST -> 90;
			default -> 0;
		};
		if (angle == 0)
			return;
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.mulPose(Axis.YP.rotationDegrees(angle));
		poseStack.translate(-0.5f, -0.5f, -0.5f);
	}
}
