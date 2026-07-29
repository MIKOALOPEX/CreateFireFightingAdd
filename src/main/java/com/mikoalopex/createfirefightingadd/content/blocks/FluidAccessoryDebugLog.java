package com.mikoalopex.createfirefightingadd.content.blocks;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Disabled-by-default diagnostics for pipe-derived utility blocks.
 *
 * <p>The call sites remain available for field testing without adding temporary
 * logging back into the fluid transfer path.</p>
 */
public final class FluidAccessoryDebugLog {
	public static final boolean ENABLED = false;
	public static final String TAG = "[PIPE_ACC_DBG]";

	private static final Logger LOGGER = LogUtils.getLogger();

	private FluidAccessoryDebugLog() {
	}

	public static void log(String message, Object... args) {
		if (ENABLED)
			LOGGER.info(TAG + " " + message, args);
	}

	public static void warn(String message, Object... args) {
		if (ENABLED)
			LOGGER.warn(TAG + " " + message, args);
	}

	public static int amount(@Nullable IFluidHandler handler) {
		if (handler == null)
			return -1;
		int amount = 0;
		for (int tank = 0; tank < handler.getTanks(); tank++)
			amount += handler.getFluidInTank(tank).getAmount();
		return amount;
	}

	public static String contents(@Nullable IFluidHandler handler) {
		if (handler == null)
			return "no_capability";
		StringBuilder result = new StringBuilder();
		for (int tank = 0; tank < handler.getTanks(); tank++) {
			if (tank > 0)
				result.append(',');
			result.append(tank).append('=').append(fluid(handler.getFluidInTank(tank)));
		}
		return result.toString();
	}

	public static String fluid(@Nullable FluidStack stack) {
		if (stack == null || stack.isEmpty())
			return "empty";
		return BuiltInRegistries.FLUID.getKey(stack.getFluid()) + "@" + stack.getAmount();
	}
}
