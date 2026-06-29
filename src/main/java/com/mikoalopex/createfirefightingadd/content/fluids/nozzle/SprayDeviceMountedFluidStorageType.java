package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SprayDeviceMountedFluidStorageType extends MountedFluidStorageType<SprayDeviceMountedFluidStorage> {

	public SprayDeviceMountedFluidStorageType() {
		super(SprayDeviceMountedFluidStorage.CODEC);
	}

	@Override
	@Nullable
	public SprayDeviceMountedFluidStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
		if (be instanceof AbstractSprayDeviceBlockEntity device)
			return SprayDeviceMountedFluidStorage.fromDevice(device);
		return null;
	}
}
