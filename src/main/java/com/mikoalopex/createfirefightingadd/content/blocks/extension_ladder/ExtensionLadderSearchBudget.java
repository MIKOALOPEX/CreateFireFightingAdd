package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Shares the per-level setup budget between searching ladders in request order. */
final class ExtensionLadderSearchBudget {
	private static final Map<Level, Budget> LEVELS = new WeakHashMap<>();

	private ExtensionLadderSearchBudget() {
	}

	static boolean acquire(ExtensionLadderBlockEntity ladder) {
		Level level = ladder.getLevel();
		Budget budget = LEVELS.computeIfAbsent(level, ignored -> new Budget());
		long tick = level.getGameTime();
		if (budget.tick != tick) {
			budget.tick = tick;
			budget.remaining = 4;
			budget.waiting.values().removeIf(ref -> {
				ExtensionLadderBlockEntity queued = ref.get();
				return queued == null || queued.isRemoved();
			});
		}
		budget.waiting.put(ladder.getBlockPos(), new WeakReference<>(ladder));
		// Leave deferred searches queued; exhausting the budget does not mean support is absent.
		if (budget.remaining == 0 || !budget.waiting.keySet().iterator().next().equals(ladder.getBlockPos()))
			return false;
		budget.remaining--;
		budget.waiting.remove(ladder.getBlockPos());
		return true;
	}

	static void cancel(ExtensionLadderBlockEntity ladder) {
		Budget budget = LEVELS.get(ladder.getLevel());
		if (budget != null) {
			var queued = budget.waiting.get(ladder.getBlockPos());
			if (queued != null && queued.get() == ladder)
				budget.waiting.remove(ladder.getBlockPos());
		}
	}

	private static final class Budget {
		private long tick = Long.MIN_VALUE;
		private int remaining;
		private final Map<BlockPos, WeakReference<ExtensionLadderBlockEntity>> waiting = new LinkedHashMap<>();
	}
}
