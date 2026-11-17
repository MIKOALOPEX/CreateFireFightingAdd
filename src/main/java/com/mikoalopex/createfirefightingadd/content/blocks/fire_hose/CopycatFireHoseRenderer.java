package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.mikoalopex.createfirefightingadd.PartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the copycat port from the stored material and leaves the curved body
 * to {@link HoseRouteRenderer}.
 */
public class CopycatFireHoseRenderer extends FireHoseRenderer {

	public CopycatFireHoseRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(FireHoseBlockEntity be, float partialTicks, PoseStack pose,
			MultiBufferSource buffers, int light, int overlay) {
		super.renderSafe(be, partialTicks, pose, buffers, light, overlay);
		if (!(be instanceof CopycatFireHoseBlockEntity copycat) || be.getLevel() == null)
			return;

		BlockState state = be.getBlockState();
		Direction facing = state.getValue(FireHoseBlock.FACING);
		int xRotation = facing == Direction.DOWN ? 180 : facing == Direction.UP ? 0 : 90;
		int yRotation = switch (facing) {
			case EAST -> 90;
			case SOUTH -> 180;
			case WEST -> 270;
			default -> 0;
		};
		// An unfilled disguise still shows the copycat surface instead of disappearing.
		BlockState material = copycat.portMaterial().filled()
			? copycat.portMaterial().state()
			: AllBlocks.COPYCAT_BASE.getDefaultState();
		int width = be.getHoseSize().width();
		pose.pushPose();
		pose.translate(0.5f, 0.5f, 0.5f);
		pose.mulPose(BlockModelRotation.by(xRotation, yRotation).getRotation().getLeftRotation());
		pose.translate(-0.5f, -0.5f, -0.5f);
		new CopycatFireHoseTemplateRendering(be.getLevel(), be.getBlockPos(), material, buffers)
			.draw(PartialModels.fireHoseCopycatPort(width).get(), pose, light, overlay);
		pose.popPose();
	}
}
