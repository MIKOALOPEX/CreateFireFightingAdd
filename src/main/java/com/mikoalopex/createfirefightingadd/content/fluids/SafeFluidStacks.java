package com.mikoalopex.createfirefightingadd.content.fluids;

import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Normalizes fluid stacks before storage, sync, and packet encoding touch them.
 */
public final class SafeFluidStacks {
	private SafeFluidStacks() {
	}

	public static FluidStack normalize(@Nullable FluidStack stack) {
		return stack == null || stack.isEmpty() ? FluidStack.EMPTY : stack;
	}

	public static FluidStack copy(@Nullable FluidStack stack) {
		stack = normalize(stack);
		return stack.isEmpty() ? FluidStack.EMPTY : stack.copy();
	}
}
