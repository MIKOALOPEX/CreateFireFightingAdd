package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FireHoseMountedFluidStorageType extends MountedFluidStorageType<FireHoseMountedFluidStorage> {

	public FireHoseMountedFluidStorageType() {
		super(FireHoseMountedFluidStorage.CODEC);
	}

	@Override
	@Nullable
	public FireHoseMountedFluidStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
		if (be instanceof FireHoseBlockEntity hose)
			return FireHoseMountedFluidStorage.fromEndpoint(hose);
		return null;
	}
}
