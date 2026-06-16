package com.createfireworkadd.createfirefightingadd.content.kinetics.pump;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.createfireworkadd.createfirefightingadd.Config;
import com.createfireworkadd.createfirefightingadd.Createfirefightingadd;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Create pump variant with a larger pressure range and pressure value.
 */
public class HighPressurePumpBlockEntity extends PumpBlockEntity implements FireFightingPumpPressureProvider {

	public HighPressurePumpBlockEntity(BlockPos pos, BlockState state) {
		this(Createfirefightingadd.HIGH_PRESSURE_PUMP_BE.get(), pos, state);
	}

	public HighPressurePumpBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
	}

	@Override
	public float createfirefightingadd$getFluidPressure() {
		return Math.abs(getSpeed()) * Config.highPressurePumpMultiplier;
	}

	@Override
	public int createfirefightingadd$getPumpRange() {
		return Math.max(1, Math.round(FluidPropagator.getPumpRange() * Config.highPressurePumpMultiplier));
	}

	@Override
	protected void distributePressureTo(Direction side) {
		if (getSpeed() == 0)
			return;

		BlockFace start = new BlockFace(worldPosition, side);
		boolean pull = isPullingOnSide(isFront(side));
		Set<BlockFace> targets = new HashSet<>();
		Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph = new HashMap<>();

		if (!pull)
			FluidPropagator.resetAffectedFluidNetworks(level, worldPosition, side.getOpposite());

		if (!hasEndpoint(level, start, pull)) {
			recordPipeFace(pipeGraph, worldPosition, 0, side, pull);
			recordPipeFace(pipeGraph, start.getConnectedPos(), 1, side.getOpposite(), !pull);

			Queue<Pair<Integer, BlockPos>> frontier = new ArrayDeque<>();
			Set<BlockPos> visited = new HashSet<>();
			int maxDistance = createfirefightingadd$getPumpRange();
			frontier.add(Pair.of(1, start.getConnectedPos()));

			while (!frontier.isEmpty()) {
				Pair<Integer, BlockPos> entry = frontier.poll();
				int distance = entry.getFirst();
				BlockPos currentPos = entry.getSecond();

				if (!level.isLoaded(currentPos))
					continue;
				if (visited.contains(currentPos))
					continue;
				visited.add(currentPos);
				BlockState currentState = level.getBlockState(currentPos);
				FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, currentPos);
				if (pipe == null)
					continue;

				for (Direction face : FluidPropagator.getPipeConnections(currentState, pipe)) {
					BlockFace blockFace = new BlockFace(currentPos, face);
					BlockPos connectedPos = blockFace.getConnectedPos();

					if (!level.isLoaded(connectedPos))
						continue;
					if (blockFace.isEquivalent(start))
						continue;
					if (hasEndpoint(level, blockFace, pull)) {
						recordTarget(pipeGraph, targets, currentPos, distance, face, pull, blockFace);
						continue;
					}

					FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, connectedPos);
					if (pipeBehaviour == null)
						continue;
					if (level.getBlockEntity(connectedPos) instanceof PumpBlockEntity)
						continue;
					if (visited.contains(connectedPos))
						continue;
					if (distance + 1 >= maxDistance) {
						recordTarget(pipeGraph, targets, currentPos, distance, face, pull, blockFace);
						continue;
					}

					recordPipeFace(pipeGraph, currentPos, distance, face, pull);
					recordPipeFace(pipeGraph, connectedPos, distance + 1, face.getOpposite(), !pull);
					frontier.add(Pair.of(distance + 1, connectedPos));
				}
			}
		}

		Map<Integer, Set<BlockFace>> validFaces = new HashMap<>();
		searchForEndpointRecursively(pipeGraph, targets, validFaces,
			new BlockFace(start.getPos(), start.getOppositeFace()), pull);

		float pressure = createfirefightingadd$getFluidPressure();
		for (Set<BlockFace> set : validFaces.values()) {
			int parallelBranches = Math.max(1, set.size() - 1);
			for (BlockFace face : set) {
				BlockPos pipePos = face.getPos();
				Direction pipeSide = face.getFace();

				if (pipePos.equals(worldPosition))
					continue;

				boolean inbound = pipeGraph.get(pipePos)
					.getSecond()
					.get(pipeSide);
				FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, pipePos);
				if (pipeBehaviour == null || pipeBehaviour.interfaces == null)
					continue;

				pipeBehaviour.addPressure(pipeSide, inbound, pressure / parallelBranches);
			}
		}
	}

	private void recordTarget(Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph,
			Set<BlockFace> targets, BlockPos pipePos, int distance, Direction face, boolean pull,
			BlockFace target) {
		recordPipeFace(pipeGraph, pipePos, distance, face, pull);
		targets.add(target);
	}

	private void recordPipeFace(Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph,
			BlockPos pipePos, int distance, Direction face, boolean pull) {
		pipeGraph.computeIfAbsent(pipePos, $ -> Pair.of(distance, new IdentityHashMap<>()))
			.getSecond()
			.put(face, pull);
	}

	private boolean hasEndpoint(LevelAccessor world, BlockFace blockFace, boolean pull) {
		BlockPos connectedPos = blockFace.getConnectedPos();
		BlockState connectedState = world.getBlockState(connectedPos);
		BlockEntity blockEntity = world.getBlockEntity(connectedPos);
		Direction face = blockFace.getFace();

		if (PumpBlock.isPump(connectedState)
			&& connectedState.getValue(PumpBlock.FACING).getAxis() == face.getAxis()
			&& blockEntity instanceof PumpBlockEntity pumpBE) {
			boolean pumpFront = pumpBE.getBlockState().getValue(PumpBlock.FACING) == blockFace.getOppositeFace();
			return pumpBE.isPullingOnSide(pumpFront) != pull;
		}

		FluidTransportBehaviour pipe = FluidPropagator.getPipe(world, connectedPos);
		if (pipe != null && pipe.canHaveFlowToward(connectedState, blockFace.getOppositeFace()))
			return false;

		if (blockEntity != null) {
			IFluidHandler capability = blockEntity.getLevel()
				.getCapability(Capabilities.FluidHandler.BLOCK, blockEntity.getBlockPos(), face.getOpposite());
			if (capability != null)
				return true;
		}

		return FluidPropagator.isOpenEnd(world, blockFace.getPos(), face);
	}
}
