package com.createfireworkadd.createfirefightingadd.content.fluids.nozzle;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

public class FlatNozzleRenderer implements BlockEntityRenderer<FlatNozzleBlockEntity> {

	@Override
	public void render(FlatNozzleBlockEntity be, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, int packedOverlay) {
		SprayDebugRenderer.renderDebug(be, poseStack);
	}

	@Override
	public boolean shouldRenderOffScreen(FlatNozzleBlockEntity be) {
		return true;
	}
}
