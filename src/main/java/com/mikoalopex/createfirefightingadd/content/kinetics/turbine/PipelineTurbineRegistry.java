package com.mikoalopex.createfirefightingadd.content.kinetics.turbine;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.api.kinetics.PressureSourceStressProvider;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rebuilds turbine output from the current fluid network instead of trusting
 * persistent registration state. Runtime maps are only caches for dirty merging
 * and stale output cleanup.
 */
public final class PipelineTurbineRegistry {
	private static final int NETWORKS_PER_TICK = 2;

	private static final Map<ResourceKey<Level>, Integer> LEVEL_IDENTITIES = new HashMap<>();
	private static final Map<ResourceKey<Level>, Long> LAST_PROCESSED_TICK = new HashMap<>();
	private static final Map<ResourceKey<Level>, LinkedHashSet<TurbineKey>> DIRTY_TURBINES = new HashMap<>();
	private static final Map<TurbineKey, NetworkKey> TURBINE_NETWORKS = new HashMap<>();
	private static final Map<NetworkKey, Set<TurbineKey>> NETWORK_MEMBERS = new HashMap<>();

	private PipelineTurbineRegistry() {
	}

	static void submit(PipelineTurbineBlockEntity turbine) {
		Level level = turbine.getLevel();
		if (level == null || level.isClientSide)
			return;
		prepareLevel(level);
		DIRTY_TURBINES
			.computeIfAbsent(level.dimension(), $ -> new LinkedHashSet<>())
			.add(TurbineKey.of(level, turbine.getBlockPos()));
	}

	static void unregister(PipelineTurbineBlockEntity turbine) {
		Level level = turbine.getLevel();
		if (level == null || level.isClientSide)
			return;
		prepareLevel(level);
		removeFromCachedNetwork(TurbineKey.of(level, turbine.getBlockPos()), level, true);
	}

	static void process(Level level) {
		if (level == null || level.isClientSide)
			return;
		prepareLevel(level);

		ResourceKey<Level> dimension = level.dimension();
		long gameTime = level.getGameTime();
		if (LAST_PROCESSED_TICK.getOrDefault(dimension, Long.MIN_VALUE) == gameTime)
			return;
		LAST_PROCESSED_TICK.put(dimension, gameTime);

		LinkedHashSet<TurbineKey> queue = DIRTY_TURBINES.get(dimension);
		if (queue == null || queue.isEmpty())
			return;

		int processed = 0;
		Iterator<TurbineKey> iterator = queue.iterator();
		while (iterator.hasNext() && processed < NETWORKS_PER_TICK) {
			TurbineKey key = iterator.next();
			iterator.remove();
			rebuildFrom(level, key);
			processed++;
		}
	}

	private static void prepareLevel(Level level) {
		ResourceKey<Level> dimension = level.dimension();
		int identity = System.identityHashCode(level);
		Integer previousIdentity = LEVEL_IDENTITIES.putIfAbsent(dimension, identity);
		if (previousIdentity != null && previousIdentity != identity)
			clearDimension(dimension, identity);
	}

	private static void clearDimension(ResourceKey<Level> dimension, int newIdentity) {
		LEVEL_IDENTITIES.put(dimension, newIdentity);
		LAST_PROCESSED_TICK.remove(dimension);
		DIRTY_TURBINES.remove(dimension);
		TURBINE_NETWORKS.entrySet().removeIf(entry -> entry.getKey().dimension().equals(dimension));
		NETWORK_MEMBERS.entrySet().removeIf(entry -> entry.getKey().dimension().equals(dimension));
	}

	private static void rebuildFrom(Level level, TurbineKey dirtyKey) {
		BlockEntity dirtyBlockEntity = level.getBlockEntity(dirtyKey.pos());
		if (!(dirtyBlockEntity instanceof PipelineTurbineBlockEntity dirtyTurbine)) {
			removeFromCachedNetwork(dirtyKey, level, true);
			return;
		}

		float dirtyPressure = dirtyTurbine.readCurrentPipePressure();
		dirtyTurbine.recordReceivedPressure(dirtyPressure);
		if (dirtyPressure <= 0) {
			removeFromCachedNetwork(dirtyKey, level, true);
			dirtyTurbine.clearCoordinatorAssignment();
			return;
		}

		NetworkScan initialScan = scanNetwork(level, new SearchNode(0, dirtyKey.pos()));
		if (initialScan.sources.isEmpty()) {
			clearTurbines(level, initialScan.turbines);
			return;
		}

		NetworkScan fullScan = expandFromSources(level, initialScan.sources);
		if (fullScan.sources.isEmpty() || fullScan.turbines.isEmpty()) {
			clearTurbines(level, initialScan.turbines);
			return;
		}

		assignNetwork(level, fullScan);
	}

	private static NetworkScan expandFromSources(Level level, Map<SourceKey, SourceInfo> initialSources) {
		NetworkScan merged = new NetworkScan();
		Queue<SourceInfo> pendingSources = new ArrayDeque<>(initialSources.values());
		Set<SourceKey> expandedSources = new HashSet<>();

		while (!pendingSources.isEmpty()) {
			SourceInfo source = pendingSources.poll();
			if (!expandedSources.add(source.key()))
				continue;

			NetworkScan sourceScan = scanNetwork(level, new SearchNode(1, source.pos().relative(source.face())));
			merged.merge(sourceScan);
			for (SourceInfo discovered : sourceScan.sources.values())
				if (!expandedSources.contains(discovered.key()))
					pendingSources.add(discovered);
		}
		return merged;
	}

	private static NetworkScan scanNetwork(Level level, SearchNode start) {
		NetworkScan scan = new NetworkScan();
		Queue<SearchNode> frontier = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		frontier.add(start);

		while (!frontier.isEmpty()) {
			SearchNode entry = frontier.poll();
			if (entry.distance() > Config.pipelineTurbineSourceScanRange)
				continue;

			BlockPos pos = entry.pos();
			if (!level.isLoaded(pos) || !visited.add(pos))
				continue;

			scan.visited.add(pos.immutable());
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity instanceof PipelineTurbineBlockEntity)
				scan.turbines.add(TurbineKey.of(level, pos));
			collectSource(level, scan, pos, null);

			FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pos);
			if (pipe == null)
				continue;

			BlockState state = level.getBlockState(pos);
			for (Direction face : FluidPropagator.getPipeConnections(state, pipe)) {
				BlockPos next = pos.relative(face);
				collectSource(level, scan, next, pos);
				if (!visited.contains(next))
					frontier.add(new SearchNode(entry.distance() + 1, next));
			}
		}
		return scan;
	}

	private static void collectSource(Level level, NetworkScan scan, BlockPos pos, BlockPos networkParent) {
		SourceInfo source = readSourceInfo(level, pos, networkParent);
		if (source.isValid())
			scan.sources.putIfAbsent(source.key(), source);
	}

	private static SourceInfo readSourceInfo(Level level, BlockPos pos, BlockPos networkParent) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof PipelineTurbineBlockEntity)
			return SourceInfo.NONE;

		Direction sourceFace = Direction.NORTH;
		if (networkParent != null) {
			sourceFace = Direction.getNearest(
				networkParent.getX() - pos.getX(),
				networkParent.getY() - pos.getY(),
				networkParent.getZ() - pos.getZ());
		}

		if (blockEntity instanceof PressureSourceStressProvider provider) {
			float speed = Math.abs(provider.createFireFightingAdd$getPressureSourceSpeed());
			if (speed <= 0)
				return SourceInfo.NONE;
			return new SourceInfo(
				SourceKey.of(level, pos),
				pos.immutable(),
				sourceFace,
				speed,
				Math.max(0, provider.createFireFightingAdd$getPressureSourceStressImpact()));
		}

		if (blockEntity instanceof KineticBlockEntity kinetic) {
			float speed = Math.abs(kinetic.getSpeed());
			if (speed <= 0)
				return SourceInfo.NONE;
			float impact = Math.abs(kinetic.calculateStressApplied());
			if (impact <= 0)
				impact = (float) BlockStressValues.getImpact(kinetic.getBlockState().getBlock());
			return new SourceInfo(SourceKey.of(level, pos), pos.immutable(), sourceFace, speed, Math.max(0, impact));
		}
		return SourceInfo.NONE;
	}

	private static void assignNetwork(Level level, NetworkScan scan) {
		NetworkKey networkKey = NetworkKey.of(level, scan.anchor());
		Set<TurbineKey> previousMembers = new HashSet<>();
		Set<NetworkKey> oldNetworks = new HashSet<>();

		Set<TurbineKey> existingNetworkMembers = NETWORK_MEMBERS.get(networkKey);
		if (existingNetworkMembers != null)
			previousMembers.addAll(existingNetworkMembers);

		for (TurbineKey turbine : scan.turbines) {
			NetworkKey oldNetwork = TURBINE_NETWORKS.get(turbine);
			if (oldNetwork != null && oldNetworks.add(oldNetwork)) {
				Set<TurbineKey> oldMembers = NETWORK_MEMBERS.get(oldNetwork);
				if (oldMembers != null)
					previousMembers.addAll(oldMembers);
			}
		}

		for (NetworkKey oldNetwork : oldNetworks) {
			if (oldNetwork.equals(networkKey))
				continue;
			Set<TurbineKey> oldMembers = NETWORK_MEMBERS.get(oldNetwork);
			if (oldMembers != null)
				oldMembers.removeAll(scan.turbines);
			if (oldMembers == null || oldMembers.isEmpty())
				NETWORK_MEMBERS.remove(oldNetwork);
		}

		NETWORK_MEMBERS.put(networkKey, new HashSet<>(scan.turbines));
		for (TurbineKey turbine : scan.turbines)
			TURBINE_NETWORKS.put(turbine, networkKey);

		previousMembers.removeAll(scan.turbines);
		for (TurbineKey stale : previousMembers) {
			TURBINE_NETWORKS.remove(stale);
			BlockEntity blockEntity = level.getBlockEntity(stale.pos());
			if (blockEntity instanceof PipelineTurbineBlockEntity turbine) {
				turbine.clearCoordinatorAssignment();
				submit(turbine);
			}
		}

		float outputSpeed = scan.maxSourceSpeed();
		float sharedCapacity = scan.totalStressImpact() * Config.pipelineTurbineStressEfficiency / scan.turbines.size();
		for (TurbineKey turbineKey : scan.turbines) {
			BlockEntity blockEntity = level.getBlockEntity(turbineKey.pos());
			if (!(blockEntity instanceof PipelineTurbineBlockEntity turbine))
				continue;
			float pressure = turbine.readCurrentPipePressure();
			turbine.recordReceivedPressure(pressure);
			turbine.applyCoordinatorOutput(outputSpeed, pressure, sharedCapacity);
		}
	}

	private static void clearTurbines(Level level, Set<TurbineKey> turbines) {
		for (TurbineKey key : turbines) {
			removeFromCachedNetwork(key, level, false);
			BlockEntity blockEntity = level.getBlockEntity(key.pos());
			if (blockEntity instanceof PipelineTurbineBlockEntity turbine)
				turbine.clearCoordinatorAssignment();
		}
	}

	private static void removeFromCachedNetwork(TurbineKey key, Level level, boolean enqueueRemaining) {
		NetworkKey networkKey = TURBINE_NETWORKS.remove(key);
		if (networkKey == null)
			return;

		Set<TurbineKey> members = NETWORK_MEMBERS.get(networkKey);
		if (members == null)
			return;

		members.remove(key);
		if (members.isEmpty()) {
			NETWORK_MEMBERS.remove(networkKey);
			return;
		}

		if (enqueueRemaining) {
			for (TurbineKey member : members) {
				BlockEntity blockEntity = level.getBlockEntity(member.pos());
				if (blockEntity instanceof PipelineTurbineBlockEntity turbine) {
					submit(turbine);
					break;
				}
			}
		}
	}

	private record SearchNode(int distance, BlockPos pos) {
	}

	private record SourceKey(ResourceKey<Level> dimension, BlockPos pos) {
		private static SourceKey of(Level level, BlockPos pos) {
			return new SourceKey(level.dimension(), pos.immutable());
		}
	}

	private record SourceInfo(SourceKey key, BlockPos pos, Direction face, float speed, float stressImpact) {
		private static final SourceInfo NONE = new SourceInfo(
			new SourceKey(Level.OVERWORLD, BlockPos.ZERO), BlockPos.ZERO, Direction.NORTH, 0, 0);

		private boolean isValid() {
			return speed > 0 && stressImpact > 0;
		}
	}

	private record NetworkKey(ResourceKey<Level> dimension, BlockPos anchor) {
		private static NetworkKey of(Level level, BlockPos anchor) {
			return new NetworkKey(level.dimension(), anchor.immutable());
		}
	}

	private record TurbineKey(ResourceKey<Level> dimension, BlockPos pos) {
		private static TurbineKey of(Level level, BlockPos pos) {
			return new TurbineKey(level.dimension(), pos.immutable());
		}
	}

	private static final class NetworkScan {
		private final Set<BlockPos> visited = new HashSet<>();
		private final Set<TurbineKey> turbines = new HashSet<>();
		private final Map<SourceKey, SourceInfo> sources = new HashMap<>();

		private void merge(NetworkScan other) {
			visited.addAll(other.visited);
			turbines.addAll(other.turbines);
			sources.putAll(other.sources);
		}

		private BlockPos anchor() {
			BlockPos anchor = BlockPos.ZERO;
			boolean found = false;
			for (BlockPos pos : visited) {
				if (!found || compareBlockPos(pos, anchor) < 0) {
					anchor = pos;
					found = true;
				}
			}
			return anchor;
		}

		private float totalStressImpact() {
			float total = 0;
			for (SourceInfo source : sources.values())
				total += source.stressImpact();
			return total;
		}

		private float maxSourceSpeed() {
			float speed = 0;
			for (SourceInfo source : sources.values())
				speed = Math.max(speed, source.speed());
			return speed;
		}

		private static int compareBlockPos(BlockPos first, BlockPos second) {
			if (first.getX() != second.getX())
				return Integer.compare(first.getX(), second.getX());
			if (first.getY() != second.getY())
				return Integer.compare(first.getY(), second.getY());
			return Integer.compare(first.getZ(), second.getZ());
		}
	}
}
