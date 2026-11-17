package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.mikoalopex.createfirefightingadd.PartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Forwards Create contraption ticks to the moving-endpoint registry.
 *
 * <p>An ordinary port uses the default block-model render shape and is drawn by
 * the contraption itself. The copycat port is entity-rendered, so its material
 * portrait has to be submitted from {@link #renderInContraption} while moving.</p>
 */
public class FireHoseMovementBehaviour implements MovementBehaviour {
	public static final FireHoseMovementBehaviour INSTANCE = new FireHoseMovementBehaviour();

	@Override
	public void tick(MovementContext context) {
		MountedFluidStorage storage = context.getFluidStorage();
		if (storage instanceof FireHoseMountedFluidStorage hoseStorage) {
			if (context.world.isClientSide && context.blockEntityData != null)
				HoseRouteRenderer.track(context, HoseRoute.read(context.blockEntityData.getCompound("HoseRoute")),
					hoseStorage.getEndpointId());
			FireHoseMovingEndpoints.update(context, hoseStorage);
		}
	}

	@Override
	public boolean disableBlockEntityRendering() {
		return true;
	}

	@Override
	public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
			ContraptionMatrices matrices, MultiBufferSource buffer) {
		BlockState state = context.state;
		if (!(state.getBlock() instanceof CopycatFireHoseBlock))
			return;
		PoseStack pose = matrices.getModel();
		int light = LevelRenderer.getLightColor(renderWorld, context.localPos);
		renderCopycatPort(context, renderWorld, pose, buffer, light, state);
	}

	private static void renderCopycatPort(MovementContext context, VirtualRenderWorld renderWorld,
			PoseStack pose, MultiBufferSource buffer, int light, BlockState state) {
		BlockState material = copycatMaterial(context, renderWorld);
		Direction facing = state.getValue(FireHoseBlock.FACING);
		int xRotation = facing == Direction.DOWN ? 180 : facing == Direction.UP ? 0 : 90;
		int yRotation = switch (facing) {
			case EAST -> 90;
			case SOUTH -> 180;
			case WEST -> 270;
			default -> 0;
		};
		BlockPos pos = context.localPos;
		pose.pushPose();
		pose.translate(0.5f, 0.5f, 0.5f);
		pose.mulPose(BlockModelRotation.by(xRotation, yRotation).getRotation().getLeftRotation());
		pose.translate(-0.5f, -0.5f, -0.5f);
		new CopycatFireHoseTemplateRendering(renderWorld, pos, material, buffer)
			.draw(PartialModels.fireHoseCopycatPort(
				state.getValue(FireHoseBlock.SIZE).width()).get(), pose, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	private static BlockState copycatMaterial(MovementContext context, VirtualRenderWorld renderWorld) {
		CompoundTag data = context.blockEntityData;
		if (data != null && data.contains("CopycatPort")) {
			HolderLookup.Provider registries = renderWorld.registryAccess();
			if (registries == null && Minecraft.getInstance().level != null)
				registries = Minecraft.getInstance().level.registryAccess();
			if (registries != null) {
				HoseMaterial material = new HoseMaterial();
				material.read(data.getCompound("CopycatPort"), registries);
				if (material.filled())
					return material.state();
			}
		}
		return AllBlocks.COPYCAT_BASE.getDefaultState();
	}
}
