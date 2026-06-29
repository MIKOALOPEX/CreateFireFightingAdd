package com.mikoalopex.createfirefightingadd.content.contraptions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.content.fluids.SafeFluidStacks;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageWrapper;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.fluid.CombinedTankWrapper;

import net.minecraft.core.BlockPos;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class ContraptionFluidAccess {
	private ContraptionFluidAccess() {
	}

	@Nullable
	public static IFluidHandler mountedFluids(MovementContext context) {
		return mountedFluids(context, null);
	}

	@Nullable
	public static IFluidHandler mountedFluids(MovementContext context, @Nullable BlockPos excludedLocalPos) {
		if (context == null || context.contraption == null || context.contraption.getStorage() == null)
			return context == null ? null : context.getFluidStorage();

		MountedFluidStorageWrapper fluids;
		try {
			fluids = context.contraption.getStorage().getFluids();
		} catch (IllegalStateException ignored) {
			return context.getFluidStorage();
		}

		if (excludedLocalPos == null)
			return fluids;

		List<IFluidHandler> handlers = new ArrayList<>();
		for (var entry : fluids.storages.entrySet()) {
			if (excludedLocalPos.equals(entry.getKey()))
				continue;
			MountedFluidStorage storage = entry.getValue();
			if (storage != null)
				handlers.add(storage);
		}
		if (handlers.isEmpty())
			return null;
		return new CombinedTankWrapper(handlers.toArray(IFluidHandler[]::new));
	}

	public static FluidStack findDrainable(IFluidHandler handler, int amount, Predicate<FluidStack> predicate) {
		if (handler == null || amount <= 0)
			return FluidStack.EMPTY;

		for (int tank = 0; tank < handler.getTanks(); tank++) {
			FluidStack stack = SafeFluidStacks.copy(handler.getFluidInTank(tank));
			if (stack.isEmpty())
				continue;
			FluidStack request = stack.copyWithAmount(amount);
			if (!predicate.test(request))
				continue;
			FluidStack available = SafeFluidStacks.normalize(handler.drain(request, IFluidHandler.FluidAction.SIMULATE));
			if (available.getAmount() >= amount)
				return available.copyWithAmount(amount);
		}
		return FluidStack.EMPTY;
	}
}
