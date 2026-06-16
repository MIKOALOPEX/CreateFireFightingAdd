package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

public class ConeNozzleRenderer implements BlockEntityRenderer<ConeNozzleBlockEntity> {

	@Override
	public void render(ConeNozzleBlockEntity be, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, int packedOverlay) {
		SprayDebugRenderer.renderDebug(be, poseStack);
	}

	@Override
	public boolean shouldRenderOffScreen(ConeNozzleBlockEntity be) {
		return true;
	}
}
