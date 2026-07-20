package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.Map;

import com.simibubi.create.content.logistics.depot.DepotBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * Scans existing block entities inside spray bounds instead of iterating every air block.
 */
final class SprayDepotScanner {
	static final int PROCESSABLE = 1;
	static final int PROCESSED = 2;

	private SprayDepotScanner() {
	}

	static Stats scan(Level level, AABB scanArea, int budget, Visitor visitor) {
		Stats stats = new Stats();
		if (level == null || visitor == null || budget <= 0)
			return stats;

		int minChunkX = ((int) Math.floor(scanArea.minX)) >> 4;
		int maxChunkX = ((int) Math.floor(scanArea.maxX)) >> 4;
		int minChunkZ = ((int) Math.floor(scanArea.minZ)) >> 4;
		int maxChunkZ = ((int) Math.floor(scanArea.maxZ)) >> 4;

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
				if (chunk == null)
					continue;
				stats.loadedChunks++;
				for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
					stats.blockEntities++;
					BlockPos pos = entry.getKey();
					if (!containsBlockCenter(scanArea, pos))
						continue;
					stats.inArea++;
					if (!(entry.getValue() instanceof DepotBlockEntity depot))
						continue;
					stats.depots++;
					if (stats.testedDepots >= budget) {
						stats.budgetHit = true;
						return stats;
					}
					stats.testedDepots++;
					int result = visitor.visit(pos.immutable(), depot);
					if ((result & PROCESSABLE) != 0)
						stats.processable++;
					if ((result & PROCESSED) != 0)
						stats.processed++;
				}
			}
		}
		return stats;
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
