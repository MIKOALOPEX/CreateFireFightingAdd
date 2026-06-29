package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

final class SprayEffectSampler {
	private static final int MAX_RANGE = 512;
	private static final int MAX_VISITED_BLOCKS = 4096;

	private SprayEffectSampler() {
	}

	static void traceRays(Level level, Vec3 origin, Vec3 direction, SprayShape shape, int range,
			int rayCount, long tick, long seed, double step, SampleConsumer consumer) {
		if (range <= 0 || rayCount <= 0 || !isFinite(origin) || !isFinite(direction))
			return;

		int safeRange = Math.min(range, MAX_RANGE);
		Set<Long> visited = new HashSet<>(Math.min(MAX_VISITED_BLOCKS, safeRange * rayCount));
		Random random = new Random(seed);
		for (Vec3 rayDirection : shape.stratifiedDirections(direction, rayCount, tick, random)) {
			if (visited.size() >= MAX_VISITED_BLOCKS)
				return;
			if (!isFinite(rayDirection) || rayDirection.lengthSqr() < 1.0E-8)
				continue;
			traceRay(level, origin, rayDirection.normalize(), safeRange, Math.max(0.1, step), visited, consumer);
		}
	}

	private static void traceRay(Level level, Vec3 origin, Vec3 direction, int range,
			double step, Set<Long> visited, SampleConsumer consumer) {
		Vec3 end = origin.add(direction.scale(range));
		BlockHitResult hit = level.clip(new ClipContext(
			origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		double hitDistance = hit.getType() == HitResult.Type.MISS
			? range
			: origin.distanceTo(hit.getLocation());
		double maxDistance = clampDistance(hitDistance, range);

		for (double distance = step; distance <= maxDistance; distance += step) {
			if (visited.size() >= MAX_VISITED_BLOCKS)
				return;
			trySample(level, origin.add(direction.scale(distance)), direction, distance, visited, consumer);
		}

		if (hit.getType() != HitResult.Type.MISS && visited.size() < MAX_VISITED_BLOCKS)
			trySample(level, hit.getLocation(), direction, maxDistance, visited, consumer);
		// Hit locations can lie exactly on a block boundary. Submit the actual
		// collided block as well so surface effects are not lost to rounding.
		if (hit.getType() != HitResult.Type.MISS && visited.size() < MAX_VISITED_BLOCKS)
			trySampleBlock(level, hit.getBlockPos(), hit.getLocation(), direction, maxDistance, visited, consumer);
	}

	private static void trySample(Level level, Vec3 samplePos, Vec3 direction, double distance,
			Set<Long> visited, SampleConsumer consumer) {
		if (!isFinite(samplePos))
			return;
		BlockPos pos = BlockPos.containing(samplePos);
		if (!level.isLoaded(pos))
			return;
		if (!visited.add(pos.asLong()))
			return;

		consumer.accept(pos, level.getBlockState(pos), samplePos, direction, distance);
	}

	private static void trySampleBlock(Level level, BlockPos pos, Vec3 samplePos, Vec3 direction, double distance,
			Set<Long> visited, SampleConsumer consumer) {
		if (!level.isLoaded(pos))
			return;
		if (!visited.add(pos.asLong()))
			return;

		consumer.accept(pos, level.getBlockState(pos), samplePos, direction, distance);
	}

	private static double clampDistance(double distance, int range) {
		if (!Double.isFinite(distance))
			return range;
		if (distance <= 0.0)
			return 0.0;
		return Math.min(distance, range);
	}

	private static boolean isFinite(Vec3 vec) {
		return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
	}

	@FunctionalInterface
	interface SampleConsumer {
		void accept(BlockPos pos, BlockState state, Vec3 samplePos, Vec3 direction, double distance);
	}
}
