package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.List;
import java.util.Map;

import com.mikoalopex.createfirefightingadd.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Checks real nozzle buffers across delivery gaps without starting a world. */
public final class NozzleTankCapacityTest {

	public static void main(String[] args) {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
		net.minecraft.server.Bootstrap.bootStrap();
		int previous = Config.nozzleTankCapacity;
		try {
			for (int capacity : new int[] { 10, 1000, 64000 }) {
				Config.nozzleTankCapacity = capacity;
				checkBuffer(new ConeNozzleBlockEntity(BlockEntityType.CHEST, BlockPos.ZERO,
					Blocks.CHEST.defaultBlockState()), capacity);
				checkBuffer(new FlatNozzleBlockEntity(BlockEntityType.CHEST, BlockPos.ZERO,
					Blocks.CHEST.defaultBlockState()), capacity);
			}
		} finally {
			Config.nozzleTankCapacity = previous;
		}
		System.out.println("Cone and flat nozzle buffers: capacity, delivery gaps and unchanged consumption passed");
	}

	private static void checkBuffer(AbstractSprayDeviceBlockEntity nozzle, int capacity) {
		var handler = nozzle.tank.getPrimaryHandler();
		// Fluid classification needs loaded datapack tags and Create's registered fluids.
		handler.setValidator(stack -> stack.getFluid() == Fluids.WATER);
		check(nozzle.getMountedFluidCapacity() == capacity, "Assembly must receive the configured capacity");
		check(nozzle.getFluidConsumptionPerTick() == 10, "Buffer size must not change consumption");
		check(handler.fill(new FluidStack(Fluids.WATER, 64001), FluidAction.EXECUTE) == capacity,
			"The actual tank must accept fluid up to the configured capacity");
		int suppliedTicks = 0;
		while (handler.getFluidAmount() >= nozzle.getFluidConsumptionPerTick()) {
			check(handler.drain(nozzle.getFluidConsumptionPerTick(), FluidAction.EXECUTE).getAmount() == 10,
				"Each spraying tick must receive its full fluid cost");
			suppliedTicks++;
		}
		check(suppliedTicks == capacity / 10, "Stored fluid must bridge the expected delivery gap");
		check(handler.isEmpty(), "The buffer must not create fluid");
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
