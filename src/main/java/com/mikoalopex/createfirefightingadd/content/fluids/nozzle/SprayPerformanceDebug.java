package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Low-volume diagnostics for spray-related TPS spikes.
 */
final class SprayPerformanceDebug {
	private static final boolean ENABLED =
		Boolean.getBoolean("createfirefightingadd.sprayPerf.enabled");
	private static final long SOURCE_WARN_NANOS =
		Long.getLong("createfirefightingadd.sprayPerf.sourceWarnNanos", 5_000_000L);
	private static final long TICK_WARN_NANOS =
		Long.getLong("createfirefightingadd.sprayPerf.tickWarnNanos", 20_000_000L);
	private static final Map<Level, TickStats> TICK_STATS =
		Collections.synchronizedMap(new WeakHashMap<>());

	private SprayPerformanceDebug() {
	}

	static long start() {
		if (!ENABLED)
			return 0L;
		return System.nanoTime();
	}

	static void record(Level level, String source, BlockPos pos, long startNanos,
			int activeProjectiles, Supplier<String> details) {
		recordInternal(level, source, pos, startNanos, activeProjectiles, details, true);
	}

	static void recordStandalone(Level level, String source, BlockPos pos, long startNanos,
			int activeProjectiles, Supplier<String> details) {
		recordInternal(level, source, pos, startNanos, activeProjectiles, details, false);
	}

	private static void recordInternal(Level level, String source, BlockPos pos, long startNanos,
			int activeProjectiles, Supplier<String> details, boolean aggregate) {
		if (level == null || level.isClientSide || startNanos <= 0)
			return;

		long elapsed = System.nanoTime() - startNanos;
		if (elapsed <= 0)
			return;

		String detail = details == null ? "" : details.get();
		long tick = level.getGameTime();
		if (aggregate) {
			synchronized (TICK_STATS) {
				TickStats stats = TICK_STATS.get(level);
				if (stats == null || stats.tick != tick) {
					stats = new TickStats(tick);
					TICK_STATS.put(level, stats);
				}
				stats.add(source, pos, elapsed, activeProjectiles, detail);
				if (!stats.logged && stats.totalNanos >= TICK_WARN_NANOS) {
					stats.logged = true;
					CreateFireFightingAdd.LOGGER.warn(
						"[SprayPerf] tick={} level={} total={}ms calls={} slowest={}ms source={} pos={} projectiles={} detail={}",
						tick, level.dimension().location(), millis(stats.totalNanos), stats.calls,
						millis(stats.slowestNanos), stats.slowestSource, stats.slowestPos,
						stats.slowestProjectiles, stats.slowestDetail);
				}
			}
		}

		if (elapsed >= SOURCE_WARN_NANOS) {
			CreateFireFightingAdd.LOGGER.warn(
				"[SprayPerf] slow source tick={} level={} source={} pos={} elapsed={}ms projectiles={} detail={}",
				tick, level.dimension().location(), source, pos, millis(elapsed), activeProjectiles, detail);
		}
	}

	private static String millis(long nanos) {
		return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
	}

	private static final class TickStats {
		private final long tick;
		private long totalNanos;
		private long slowestNanos;
		private int calls;
		private boolean logged;
		private String slowestSource = "";
		private BlockPos slowestPos = BlockPos.ZERO;
		private int slowestProjectiles = -1;
		private String slowestDetail = "";

		private TickStats(long tick) {
			this.tick = tick;
		}

		private void add(String source, BlockPos pos, long elapsedNanos, int activeProjectiles, String detail) {
			totalNanos += elapsedNanos;
			calls++;
			if (elapsedNanos <= slowestNanos)
				return;
			slowestNanos = elapsedNanos;
			slowestSource = source;
			slowestPos = pos == null ? BlockPos.ZERO : pos.immutable();
			slowestProjectiles = activeProjectiles;
			slowestDetail = detail;
		}
	}
}
