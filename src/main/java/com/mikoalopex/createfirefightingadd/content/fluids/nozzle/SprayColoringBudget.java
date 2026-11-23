package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Bounds dye writes across all sprayers in a level without delaying extinguishing. */
final class SprayColoringBudget {
	static final int MAX_CHANGES_PER_TICK = 128;
	private static final Map<Level, TickBudget> LEVELS = new WeakHashMap<>();

	private SprayColoringBudget() {
	}

	static boolean tryAcquire(Level level, BlockPos pos) {
		if (level.isClientSide)
			return false;
		return LEVELS.computeIfAbsent(level, unused -> new TickBudget()).tryAcquire(level.getGameTime(), pos.asLong());
	}

	static final class TickBudget {
		private long tick = Long.MIN_VALUE;
		private final Set<Long> positions = new HashSet<>();

		boolean tryAcquire(long currentTick, long pos) {
			if (tick != currentTick) {
				tick = currentTick;
				positions.clear();
			}
			return positions.size() < MAX_CHANGES_PER_TICK && positions.add(pos);
		}
	}
}
