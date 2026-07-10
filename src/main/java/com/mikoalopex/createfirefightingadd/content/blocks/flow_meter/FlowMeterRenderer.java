package com.mikoalopex.createfirefightingadd.content.blocks.flow_meter;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.PartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public class FlowMeterRenderer extends SmartBlockEntityRenderer<FlowMeterBlockEntity> {

	private static final float MAX_DISPLAY_PRESSURE = 512f;
	private static final float MAX_DISPLAY_FLOW = 256f;
	private static final float POINTER_TRAVEL = 5.6f / 16f;

	public FlowMeterRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(FlowMeterBlockEntity be, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, int packedOverlay) {
		BlockState state = be.getBlockState();
		if (!state.is(CreateFireFightingAdd.FLUID_FLOW_METER.get()))
			return;

		poseStack.pushPose();
		rotateToFacing(poseStack, state.getValue(FlowMeterBlock.FACING));

		renderPartial(PartialModels.FLUID_FLOW_METER_DIAL, state, poseStack, buffer, packedLight, packedOverlay);

		float pressure = Mth.lerp(partialTick, be.previousDisplayPressure, be.displayPressure);
		float flow = Mth.lerp(partialTick, be.previousDisplayFlow, be.displayFlow);
		renderSlidingPointer(PartialModels.FLUID_FLOW_METER_PURPLE_POINTER_WEST,
			PartialModels.FLUID_FLOW_METER_PURPLE_POINTER_EAST, state, poseStack, buffer,
			packedLight, packedOverlay, pressure / MAX_DISPLAY_PRESSURE);
		renderSlidingPointer(PartialModels.FLUID_FLOW_METER_GREEN_POINTER_WEST,
			PartialModels.FLUID_FLOW_METER_GREEN_POINTER_EAST, state, poseStack, buffer,
			packedLight, packedOverlay, flow / MAX_DISPLAY_FLOW);

		poseStack.popPose();
	}

	private static void renderSlidingPointer(dev.engine_room.flywheel.lib.model.baked.PartialModel west,
			dev.engine_room.flywheel.lib.model.baked.PartialModel east, BlockState state, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, int packedOverlay, float ratio) {
		float offset = (Mth.clamp(ratio, 0f, 1f) - 0.5f) * POINTER_TRAVEL;
		poseStack.pushPose();
		poseStack.translate(0, 0, offset);
		renderPartial(west, state, poseStack, buffer, packedLight, packedOverlay);
		renderPartial(east, state, poseStack, buffer, packedLight, packedOverlay);
		poseStack.popPose();
	}

	private static void renderPartial(dev.engine_room.flywheel.lib.model.baked.PartialModel model,
			BlockState state, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
		SuperByteBuffer partial = CachedBuffers.partial(model, state);
		partial.light(packedLight)
			.overlay(packedOverlay)
			.renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));
	}

	private static void rotateToFacing(PoseStack poseStack, Direction facing) {
		TransformStack<?> transform = TransformStack.of(poseStack);
		transform.center();
		switch (facing) {
			case SOUTH -> transform.rotateYDegrees(180);
			case EAST -> transform.rotateYDegrees(90);
			case WEST -> transform.rotateYDegrees(270);
			case UP -> transform.rotateXDegrees(90);
			case DOWN -> transform.rotateXDegrees(270);
			default -> {
			}
		}
		transform.uncenter();
	}
}
