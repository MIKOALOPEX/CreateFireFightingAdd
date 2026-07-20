package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.mikoalopex.createfirefightingadd.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Keeps non-critical spray work staggered without delaying core extinguishing.
 */
final class SprayAuxiliaryScheduler {
	private static final Map<Level, TickSources> ACTIVE_SOURCES = new WeakHashMap<>();

	private SprayAuxiliaryScheduler() {
	}

	static boolean shouldProcess(Level level, BlockPos sourcePos, boolean sablePass) {
		if (level == null || level.isClientSide)
			return false;

		int activeSources = reportActive(level, sourcePos);
		int interval = Math.max(1, sablePass
			? Config.spraySableProcessingInterval
			: Config.sprayAuxiliaryInterval);
		int degradeAt = Math.max(1, Config.sprayMaxActiveNozzlesBeforeDegrade);
		if (activeSources > degradeAt) {
			int overloadSteps = 1 + (activeSources - degradeAt - 1) / degradeAt;
			interval *= Math.min(8, 1 + overloadSteps);
		}

		if (interval <= 1)
			return true;
		long key = sourcePos == null ? 0L : sourcePos.asLong();
		int phase = Math.floorMod((int) (key ^ (key >>> 32)), interval);
		return Math.floorMod((int) level.getGameTime() + phase, interval) == 0;
	}

	private static int reportActive(Level level, BlockPos sourcePos) {
		long tick = level.getGameTime();
		long key = sourcePos == null ? 0L : sourcePos.asLong();
		synchronized (ACTIVE_SOURCES) {
			TickSources sources = ACTIVE_SOURCES.get(level);
			if (sources == null || sources.tick != tick) {
				sources = new TickSources(tick);
				ACTIVE_SOURCES.put(level, sources);
			}
			sources.keys.add(key);
			return sources.keys.size();
		}
	}

	private static final class TickSources {
		private final long tick;
		private final Set<Long> keys = new HashSet<>();

		private TickSources(long tick) {
			this.tick = tick;
		}
	}
}
