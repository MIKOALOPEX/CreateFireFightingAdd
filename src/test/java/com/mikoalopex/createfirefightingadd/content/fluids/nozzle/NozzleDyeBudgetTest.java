package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import com.mikoalopex.createfirefightingadd.integration.dragonsplus.DragonsPlusCompat;

import net.neoforged.neoforge.fluids.FluidStack;

/** Checks bounded writes and optional dependency isolation without starting a world. */
public final class NozzleDyeBudgetTest {
	public static void main(String[] args) throws Exception {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
			java.util.List.of(), java.util.List.of(), java.util.Map.of());
		net.minecraft.server.Bootstrap.bootStrap();
		SprayColoringBudget.TickBudget budget = new SprayColoringBudget.TickBudget();
		check(budget.tryAcquire(1, 0), "First contact must be accepted");
		check(!budget.tryAcquire(1, 0), "Two dyes must not rewrite one block in the same tick");
		for (int i = 1; i < SprayColoringBudget.MAX_CHANGES_PER_TICK; i++)
			check(budget.tryAcquire(1, i), "Distinct contacts within budget must be accepted");
		check(!budget.tryAcquire(1, 999), "All sprayers must share the write cap");
		check(budget.tryAcquire(2, 0), "Budget must reset on the next tick");
		check(budget.tryAcquire(2, 999), "Deferred contacts must be eligible next tick");
		Class.forName(DragonsPlusCompat.class.getName()).getDeclaredMethods();
		check(DragonsPlusCompat.color(FluidStack.EMPTY) == null, "Dragons Plus must remain optional");
		System.out.println("NozzleDyeBudget: write cap, deduplication, reset and optional loading passed");
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
