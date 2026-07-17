package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import com.mikoalopex.createfirefightingadd.PartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class HandheldNozzleControllerItemRenderer extends CustomRenderedItemModelRenderer {
	private static final float THIRD_PERSON_MODEL_SCALE = 1.2f;
	private static final float BOUND_GUI_MODEL_SCALE = 0.72f;
	private static final float BOUND_GUI_X_OFFSET = 0.42f;
	private static final float BOUND_GUI_Y_OFFSET = -0.22f;
	private static final float COG_SPEED_IDLE = 2.0f;
	private static final float COG_SPEED_SPRAYING = 18.0f;
	private static final float COG_PIVOT_X = 8.0f / 16.0f - 0.5f;
	private static final float COG_PIVOT_Y = 8.5f / 16.0f - 0.5f;
	private static final float COG_PIVOT_Z = 7.5f / 16.0f - 0.5f;
	private static final float HANDLE_PIVOT_X = 8.0f / 16.0f - 0.5f;
	private static final float HANDLE_PIVOT_Y = 10.0f / 16.0f - 0.5f;
	private static final float HANDLE_PIVOT_Z = 5.0f / 16.0f - 0.5f;

	@Override
	protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
			ItemDisplayContext transformType, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
		HandheldNozzleType type = HandheldNozzleControllerItem.readBinding(stack)
			.map(HandheldNozzleControllerItem.Binding::nozzleType)
			.orElse(HandheldNozzleType.NONE);

		poseStack.pushPose();
		if (isThirdPersonHand(transformType))
			poseStack.scale(THIRD_PERSON_MODEL_SCALE, THIRD_PERSON_MODEL_SCALE, THIRD_PERSON_MODEL_SCALE);
		else if (transformType == ItemDisplayContext.GUI && type.hasNozzle()) {
			poseStack.translate(BOUND_GUI_X_OFFSET, BOUND_GUI_Y_OFFSET, 0.0f);
			poseStack.scale(BOUND_GUI_MODEL_SCALE, BOUND_GUI_MODEL_SCALE, BOUND_GUI_MODEL_SCALE);
		}

		renderer.renderSolid(PartialModels.HANDHELD_NOZZLE_BASE.get(), light);
		HandheldNozzleHoseRenderer.captureRenderedAnchor(stack, transformType, poseStack);

		float spray = HandheldNozzleClientHandler.getSprayProgress(AnimationTickHolder.getPartialTicks());
		renderHandle(renderer, poseStack, light, spray);
		renderCog(renderer, poseStack, light, spray);

		if (type == HandheldNozzleType.CONE)
			renderer.renderSolid(PartialModels.HANDHELD_NOZZLE_CONE.get(), light);
		else if (type == HandheldNozzleType.FLAT)
			renderer.renderSolid(PartialModels.HANDHELD_NOZZLE_FLAT.get(), light);

		HandheldNozzleHoseRenderer.renderFirstPersonLocalHose(stack, transformType, poseStack, buffer, light);
		poseStack.popPose();
	}

	private static boolean isThirdPersonHand(ItemDisplayContext transformType) {
		return transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
			|| transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
	}

	private static void renderHandle(PartialItemModelRenderer renderer, PoseStack poseStack, int light, float spray) {
		float angle = Mth.lerp(spray, 0.0f, 22.5f);
		poseStack.pushPose();
		poseStack.translate(HANDLE_PIVOT_X, HANDLE_PIVOT_Y, HANDLE_PIVOT_Z);
		poseStack.mulPose(Axis.XP.rotationDegrees(angle));
		poseStack.translate(-HANDLE_PIVOT_X, -HANDLE_PIVOT_Y, -HANDLE_PIVOT_Z);
		renderer.renderSolid(PartialModels.HANDHELD_NOZZLE_HANDLE.get(), light);
		poseStack.popPose();
	}

	private static void renderCog(PartialItemModelRenderer renderer, PoseStack poseStack, int light, float spray) {
		float speed = Mth.lerp(spray, COG_SPEED_IDLE, COG_SPEED_SPRAYING);
		float angle = AnimationTickHolder.getRenderTime() * speed;
		poseStack.pushPose();
		poseStack.translate(COG_PIVOT_X, COG_PIVOT_Y, COG_PIVOT_Z);
		poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
		poseStack.translate(-COG_PIVOT_X, -COG_PIVOT_Y, -COG_PIVOT_Z);
		renderer.renderSolid(PartialModels.HANDHELD_NOZZLE_COG.get(), light);
		poseStack.popPose();
	}
}
