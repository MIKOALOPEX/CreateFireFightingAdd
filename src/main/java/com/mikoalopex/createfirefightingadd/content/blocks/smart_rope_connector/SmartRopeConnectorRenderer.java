package com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.RopeStrandRenderer;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Renders every client strand while sharing the connector's single knot model. */
public class SmartRopeConnectorRenderer extends SafeBlockEntityRenderer<SmartRopeConnectorBlockEntity> {
	public SmartRopeConnectorRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	public boolean shouldRenderOffScreen(SmartRopeConnectorBlockEntity be) {
		return true;
	}

	@Override
	public boolean shouldRender(SmartRopeConnectorBlockEntity be, Vec3 cameraPos) {
		return true;
	}

	@Override
	protected void renderSafe(SmartRopeConnectorBlockEntity be, float partialTicks, PoseStack ms,
			MultiBufferSource buffer, int light, int overlay) {
		boolean attached = be.connectionCount() > 0;
		for (RopeStrandHolderBehavior holder : be.getClientRopes()) {
			RopeStrandRenderer.render(be, holder, partialTicks, ms, buffer);
			attached |= holder.getClientStrand() != null;
		}
		if (!attached)
			return;

		SuperByteBuffer knot = CachedBuffers.partialFacing(
			SimPartialModels.ROPE_CONNECTOR_KNOT, AllBlocks.ROPE.getDefaultState(), Direction.NORTH).light(light);
		BlockPos pos = be.getBlockPos();
		BlockState state = be.getBlockState();
		Vec3 attachment = be.getVisualAttachmentPoint(pos, state);
		Direction facing = state.getValue(SmartRopeConnectorBlock.FACING);
		boolean firstAxis = state.getValue(SmartRopeConnectorBlock.AXIS_ALONG_FIRST_COORDINATE);
		float lastZ = (firstAxis ^ facing.getAxis() == Direction.Axis.Z) ? 90 : 0;
		float y = AngleHelper.horizontalAngle(facing) + (firstAxis || facing.getAxis() != Direction.Axis.Y ? 0 : 90);
		float z = facing == Direction.UP ? 270 : facing == Direction.DOWN ? 90 : 0;

		knot.translate(attachment.subtract(pos.getCenter()));
		knot.rotateCentered((float) Math.toRadians(z), Direction.SOUTH);
		knot.rotateCentered((float) Math.toRadians(y), Direction.UP);
		knot.rotateCentered((float) Math.toRadians(lastZ), Direction.SOUTH);
		knot.rotateCentered((float) (Math.PI / 2), Direction.UP);
		knot.renderInto(ms, buffer.getBuffer(RenderType.solid()));
	}
}
