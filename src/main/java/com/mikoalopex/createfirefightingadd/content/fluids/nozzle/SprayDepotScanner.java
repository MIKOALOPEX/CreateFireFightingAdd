package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.UUID;
import java.util.ConcurrentModificationException;

import com.simibubi.create.content.logistics.depot.DepotBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

/**
 * Resumes loaded block-entity scans within per-pass and shared per-level budgets.
 */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class SprayDepotScanner {
	static final int PROCESSABLE = 1;
	static final int PROCESSED = 2;
	private static final Map<Level, ScanState> STATES = new HashMap<>();
	private static final int CHUNKS_PER_PASS = 8;
	private static final int ENTRIES_PER_PASS = 128;
	private static final int CHUNKS_PER_TICK = 64;
	private static final int ENTRIES_PER_TICK = 1024;

	private SprayDepotScanner() {
	}

	static Stats scan(Level level, AABB scanArea, Vec3 origin, UUID subLevelId, int budget, Visitor visitor) {
		Stats stats = new Stats();
		if (level == null || level.isClientSide || visitor == null || budget <= 0)
			return stats;
		ScanState state = STATES.computeIfAbsent(level, key -> new ScanState());
		long tick = level.getGameTime();
		if (state.tick != tick) {
			state.tick = tick;
			state.chunks = CHUNKS_PER_TICK;
			state.entries = ENTRIES_PER_TICK;
			state.cursors.values().removeIf(cursor -> tick - cursor.lastUsed > 200);
			state.waiting.removeIf(waiting -> !state.cursors.containsKey(waiting)
				|| tick - state.cursors.get(waiting).lastUsed > 20);
		}
		ScanKey key = new ScanKey(BlockPos.containing(origin), subLevelId);
		Cursor cursor = state.cursors.computeIfAbsent(key, ignored -> new Cursor());
		cursor.lastUsed = tick;
		while (state.cursors.size() > 256) {
			ScanKey oldest = state.cursors.keySet().iterator().next();
			state.cursors.remove(oldest);
			state.waiting.remove(oldest);
		}
		state.waiting.add(key);
		// Deferred sources retain their place so earlier tickers cannot consume every budget.
		if (!state.waiting.iterator().next().equals(key) || state.chunks <= 0 || state.entries <= 0) {
			stats.budgetHit = true;
			return stats;
		}
		state.waiting.remove(key);
		int minChunkX = ((int) Math.floor(scanArea.minX)) >> 4;
		int maxChunkX = ((int) Math.floor(scanArea.maxX)) >> 4;
		int minChunkZ = ((int) Math.floor(scanArea.minZ)) >> 4;
		int maxChunkZ = ((int) Math.floor(scanArea.maxZ)) >> 4;

		int width = maxChunkX - minChunkX + 1;
		int count = width * (maxChunkZ - minChunkZ + 1);
		int examinedChunks = 0;
		try {
			while (examinedChunks < Math.min(count, CHUNKS_PER_PASS) && state.chunks > 0
				&& stats.blockEntities < ENTRIES_PER_PASS && state.entries > 0 && stats.testedDepots < budget) {
				int index = Math.floorMod(cursor.chunkIndex, count);
				int x = minChunkX + index % width;
				int z = minChunkZ + index / width;
				state.chunks--;
				examinedChunks++;
				LevelChunk chunk = level.getChunkSource().getChunk(x, z, false);
				if (chunk == null) {
					cursor.advance();
					continue;
				}
				stats.loadedChunks++;
				if (cursor.chunk != chunk || cursor.iterator == null) {
					cursor.chunk = chunk;
					cursor.iterator = chunk.getBlockEntities().entrySet().iterator();
				}
				while (stats.blockEntities < ENTRIES_PER_PASS && state.entries > 0
					&& stats.testedDepots < budget && cursor.iterator.hasNext()) {
					var entry = cursor.iterator.next();
					stats.blockEntities++;
					state.entries--;
					BlockPos pos = entry.getKey();
					if (!containsBlockCenter(scanArea, pos))
						continue;
					stats.inArea++;
					if (!(entry.getValue() instanceof DepotBlockEntity depot) || depot.isRemoved())
						continue;
					stats.depots++;
					stats.testedDepots++;
					int result = visitor.visit(pos.immutable(), depot);
					if ((result & PROCESSABLE) != 0)
						stats.processable++;
					if ((result & PROCESSED) != 0)
						stats.processed++;
				}
				if (!cursor.iterator.hasNext())
					cursor.advance();
				else
					break;
			}
		} catch (ConcurrentModificationException changed) {
			// A block entity was added or removed between passes; rebuild this chunk's cursor.
			cursor.iterator = null;
		}
		stats.budgetHit = examinedChunks < count || cursor.iterator != null || stats.testedDepots >= budget;
		return stats;
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (!event.getLevel().isClientSide())
			STATES.remove(event.getLevel());
	}

	@SubscribeEvent
	public static void onChunkUnload(ChunkEvent.Unload event) {
		if (event.getLevel().isClientSide())
			return;
		// Iterators retain their backing chunk; discard them when that chunk unloads.
		ScanState state = STATES.get(event.getLevel());
		if (state != null)
			state.cursors.values().removeIf(cursor -> cursor.chunk == event.getChunk());
	}

	private record ScanKey(BlockPos origin, UUID subLevelId) {
	}

	private static final class ScanState {
		private long tick = Long.MIN_VALUE;
		private int chunks;
		private int entries;
		private final Map<ScanKey, Cursor> cursors = new LinkedHashMap<>(16, 0.75f, true);
		private final java.util.Set<ScanKey> waiting = new java.util.LinkedHashSet<>();
	}

	private static final class Cursor {
		private int chunkIndex;
		private long lastUsed;
		private LevelChunk chunk;
		private Iterator<Map.Entry<BlockPos, BlockEntity>> iterator;

		private void advance() {
			chunkIndex++;
			chunk = null;
			iterator = null;
		}
	}

	private static boolean containsBlockCenter(AABB area, BlockPos pos) {
		return area.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}

	@FunctionalInterface
	interface Visitor {
		int visit(BlockPos pos, DepotBlockEntity depot);
	}

	static final class Stats {
		int loadedChunks;
		int blockEntities;
		int inArea;
		int depots;
		int testedDepots;
		int processable;
		int processed;
		boolean budgetHit;
	}
}
