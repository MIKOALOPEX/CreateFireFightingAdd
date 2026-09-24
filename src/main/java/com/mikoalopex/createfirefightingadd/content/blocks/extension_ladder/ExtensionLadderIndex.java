package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Tracks loaded ladder anchors by local chunk without scanning block positions. */
final class ExtensionLadderIndex {
	private static final Map<Level, Map<Long, Map<BlockPos, WeakReference<ExtensionLadderBlockEntity>>>> LEVELS =
		new WeakHashMap<>();

	private ExtensionLadderIndex() {
	}

	static synchronized void add(ExtensionLadderBlockEntity ladder) {
		if (ladder.getLevel() == null)
			return;
		LEVELS.computeIfAbsent(ladder.getLevel(), key -> new HashMap<>())
			.computeIfAbsent(ChunkPos.asLong(ladder.getBlockPos()), key -> new HashMap<>())
			.put(ladder.getBlockPos(), new WeakReference<>(ladder));
	}

	static synchronized void remove(ExtensionLadderBlockEntity ladder) {
		var chunks = LEVELS.get(ladder.getLevel());
		if (chunks == null)
			return;
		long key = ChunkPos.asLong(ladder.getBlockPos());
		var anchors = chunks.get(key);
		if (anchors == null)
			return;
		var entry = anchors.get(ladder.getBlockPos());
		if (entry != null && entry.get() == ladder)
			anchors.remove(ladder.getBlockPos());
		if (anchors.isEmpty())
			chunks.remove(key);
		if (chunks.isEmpty())
			LEVELS.remove(ladder.getLevel());
	}

	static synchronized boolean isEmpty(Level level) {
		var chunks = LEVELS.get(level);
		return chunks == null || chunks.isEmpty();
	}

	static synchronized List<ExtensionLadderBlockEntity> find(Level level, AABB bounds) {
		List<ExtensionLadderBlockEntity> result = new ArrayList<>();
		var chunks = LEVELS.get(level);
		if (chunks == null)
			return result;
		for (int x = ((int) Math.floor(bounds.minX)) >> 4; x <= ((int) Math.floor(bounds.maxX)) >> 4; x++) {
			for (int z = ((int) Math.floor(bounds.minZ)) >> 4; z <= ((int) Math.floor(bounds.maxZ)) >> 4; z++) {
				var anchors = chunks.get(ChunkPos.asLong(x, z));
				if (anchors == null)
					continue;
				for (var entry : anchors.entrySet()) {
					ExtensionLadderBlockEntity ladder = entry.getValue().get();
					BlockPos pos = entry.getKey();
					if (ladder != null && !ladder.isRemoved() && ladder.getLevel() == level
						&& pos.getX() >= bounds.minX && pos.getX() <= bounds.maxX
						&& pos.getY() >= bounds.minY && pos.getY() <= bounds.maxY
						&& pos.getZ() >= bounds.minZ && pos.getZ() <= bounds.maxZ)
						result.add(ladder);
				}
			}
		}
		return result;
	}
}
