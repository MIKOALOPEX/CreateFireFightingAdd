package com.mikoalopex.createfirefightingadd.api.nozzle;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Immutable description of a server-side spray contact with a block position.
 */
public record NozzleSprayHitContext(
	Level level,
	BlockPos pos,
	BlockState state,
	FluidStack fluid,
	NozzleSprayFluidType fluidType,
	boolean ignited,
	@Nullable Vec3 origin,
	@Nullable Vec3 hitLocation,
	@Nullable Vec3 direction,
	double distance
) {
	public boolean isWaterLike() {
		return fluidType == NozzleSprayFluidType.WATER
			|| fluidType == NozzleSprayFluidType.MILK
			|| fluidType == NozzleSprayFluidType.POTION;
	}
}
