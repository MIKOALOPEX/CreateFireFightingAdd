package com.mikoalopex.createfirefightingadd.content.kinetics.pump;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.content.fluids.pump.PumpBlock;

public class HighPressurePumpBlock extends PumpBlock {

	public HighPressurePumpBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntityType<? extends HighPressurePumpBlockEntity> getBlockEntityType() {
		return CreateFireFightingAdd.HIGH_PRESSURE_PUMP_BE.get();
	}

	@Override
	public boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) {
		return false;
	}
}
