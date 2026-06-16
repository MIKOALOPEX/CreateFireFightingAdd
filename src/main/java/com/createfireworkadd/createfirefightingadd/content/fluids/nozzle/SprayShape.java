package com.createfireworkadd.createfirefightingadd.content.fluids.nozzle;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public interface SprayShape {

	int getRange();

	/**
	 * Iterates every block position within the spray volume, ordered by distance from origin.
	 *
	 * @param origin    world-space spray origin
	 * @param direction world-space spray direction (normalized)
	 * @param action    called for each position; receives world pos, distance, and grid coordinates
	 */
	void forEachPosition(Vec3 origin, Vec3 direction, PositionAction action);

	/**
	 * Generates a random launch direction within the spray volume.
	 * Default returns the base direction unchanged (no spread).
	 */
	default Vec3 randomSprayDirection(Vec3 baseDirection, RandomSource random) {
		return baseDirection;
	}

	/** Deterministic variant used when both sides must generate identical directions from a shared seed. */
	default Vec3 randomSprayDirection(Vec3 baseDirection, Random random) {
		return baseDirection;
	}

	/** Generates deterministically distributed directions that ensure even cone coverage
	 *  across the full radius. Default falls back to random. */
	default List<Vec3> stratifiedDirections(Vec3 baseDirection, int count, long tick, Random random) {
		List<Vec3> dirs = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
			dirs.add(randomSprayDirection(baseDirection, random));
		return dirs;
	}

	@FunctionalInterface
	interface PositionAction {
		void accept(BlockPos pos, int dist, int gridU, int gridV);
	}
}
