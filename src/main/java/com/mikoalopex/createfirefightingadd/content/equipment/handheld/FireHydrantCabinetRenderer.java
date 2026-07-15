package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.PartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class FireHydrantCabinetRenderer implements BlockEntityRenderer<FireHydrantCabinetBlockEntity> {
	private static final float DOOR_OPEN_ANGLE = 135.0f;
	private static final float DOOR_PIVOT_X = 0.0f;
	private static final float DOOR_PIVOT_Y = 0.0f;
	private static final float DOOR_PIVOT_Z = 9.0f / 16.0f;

	public FireHydrantCabinetRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(FireHydrantCabinetBlockEntity be, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, int packedOverlay) {
		BlockState state = be.getBlockState();
		if (!state.is(CreateFireFightingAdd.FIRE_HYDRANT_CABINET.get()))
			return;

		poseStack.pushPose();
		rotateDynamicPartsToFacing(poseStack, state.getValue(FireHydrantCabinetBlock.FACING));

		if (be.hasHose())
			renderPartial(PartialModels.FIRE_HYDRANT_CABINET_HOSE, state, poseStack, buffer, packedLight, packedOverlay);
		switch (be.getNozzleType()) {
			case CONE -> renderPartial(PartialModels.FIRE_HYDRANT_CABINET_CONE, state, poseStack, buffer, packedLight, packedOverlay);
			case FLAT -> renderPartial(PartialModels.FIRE_HYDRANT_CABINET_FLAT, state, poseStack, buffer, packedLight, packedOverlay);
			default -> {
			}
		}
		renderDoor(be, partialTick, poseStack, buffer, packedLight, packedOverlay);

		poseStack.popPose();
	}

	private static void renderDoor(FireHydrantCabinetBlockEntity be, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, int packedOverlay) {
		LerpedFloat door = be.getDoorAnimation();
		float progress = door == null ? (be.isDoorOpen() ? 1.0f : 0.0f) : door.getValue(partialTick);
		float angle = progress * DOOR_OPEN_ANGLE;

		poseStack.pushPose();
		poseStack.translate(DOOR_PIVOT_X, DOOR_PIVOT_Y, DOOR_PIVOT_Z);
		poseStack.mulPose(Axis.YP.rotationDegrees(angle));
		poseStack.translate(-DOOR_PIVOT_X, -DOOR_PIVOT_Y, -DOOR_PIVOT_Z);
		renderPartial(PartialModels.FIRE_HYDRANT_CABINET_DOOR, be.getBlockState(), poseStack, buffer, packedLight, packedOverlay);
		poseStack.popPose();
	}

	private static void renderPartial(PartialModel model, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, int packedOverlay) {
		renderPartial(model, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), poseStack,
			buffer, packedLight, packedOverlay);
	}

	private static void renderPartial(PartialModel model, BlockState state, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, int packedOverlay) {
		SuperByteBuffer partial = CachedBuffers.partial(model, state);
		partial.light(packedLight)
			.overlay(packedOverlay)
			.renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));
	}

	private static void rotateDynamicPartsToFacing(PoseStack poseStack, Direction facing) {
		TransformStack<?> transform = TransformStack.of(poseStack);
		transform.center();
		transform.rotateYDegrees(switch (facing) {
			case EAST -> 90;
			case SOUTH -> 180;
			case WEST -> 270;
			default -> 0;
		});
		transform.uncenter();
	}
}
