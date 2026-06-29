package com.mikoalopex.createfirefightingadd.content.kinetics.pump;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.FireFightingWrenchableBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;

public class HighPressurePumpBlock extends PumpBlock implements FireFightingWrenchableBlock {

	public HighPressurePumpBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntityType<? extends HighPressurePumpBlockEntity> getBlockEntityType() {
		return CreateFireFightingAdd.HIGH_PRESSURE_PUMP_BE.get();
	}

	@Override
	public boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}
}
