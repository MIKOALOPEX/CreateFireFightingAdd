package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.PartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

public class HandheldNozzleControllerEntityRenderer extends EntityRenderer<HandheldNozzleControllerEntity> {
	public HandheldNozzleControllerEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(HandheldNozzleControllerEntity entity, float entityYaw, float partialTick,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		HandheldNozzleType type = HandheldNozzleControllerItem.readBinding(entity.getControllerStack())
			.map(HandheldNozzleControllerItem.Binding::nozzleType)
			.orElse(HandheldNozzleType.NONE);

		poseStack.pushPose();
		poseStack.translate(0.0f, HandheldNozzleControllerEntity.GROUND_RENDER_Y_OFFSET, 0.0f);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entity.getFixedYaw()));
		poseStack.translate(-0.5f, -0.5f, -0.5f);

		renderPartial(PartialModels.HANDHELD_NOZZLE_BASE, poseStack, buffer, packedLight);
		renderPartial(PartialModels.HANDHELD_NOZZLE_HANDLE, poseStack, buffer, packedLight);
		renderPartial(PartialModels.HANDHELD_NOZZLE_COG, poseStack, buffer, packedLight);
		if (type == HandheldNozzleType.CONE)
			renderPartial(PartialModels.HANDHELD_NOZZLE_CONE, poseStack, buffer, packedLight);
		else if (type == HandheldNozzleType.FLAT)
			renderPartial(PartialModels.HANDHELD_NOZZLE_FLAT, poseStack, buffer, packedLight);
		poseStack.popPose();
		super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
	}

	@Override
	public ResourceLocation getTextureLocation(HandheldNozzleControllerEntity entity) {
		return CreateFireFightingAdd.path("textures/item/handheld_nozzle_controller.png");
	}

	private static void renderPartial(PartialModel model, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		SuperByteBuffer partial = CachedBuffers.partial(model, Blocks.AIR.defaultBlockState());
		partial.light(packedLight)
			.overlay(OverlayTexture.NO_OVERLAY)
			.renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));
	}
}
