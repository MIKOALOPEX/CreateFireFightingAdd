package com.mikoalopex.createfirefightingadd.content.kinetics.pump;

import com.mikoalopex.createfirefightingadd.PartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class HighPressurePumpRenderer extends KineticBlockEntityRenderer<HighPressurePumpBlockEntity> {

	public HighPressurePumpRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected SuperByteBuffer getRotatedModel(HighPressurePumpBlockEntity be, BlockState state) {
		return CachedBuffers.partialFacingVertical(PartialModels.HIGH_PRESSURE_PUMP_COG, state, state.getValue(HighPressurePumpBlock.FACING));
	}
}
