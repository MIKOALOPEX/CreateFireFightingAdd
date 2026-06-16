package com.createfireworkadd.createfirefightingadd.content.fluids.water_intake;

import com.createfireworkadd.createfirefightingadd.Config;
import com.createfireworkadd.createfirefightingadd.Createfirefightingadd;
import com.createfireworkadd.createfirefightingadd.content.fluids.nozzle.BucketControllerBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.content.fluids.pipes.VanillaFluidTargets;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.BlockFace;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WaterIntakeBlockEntity extends KineticBlockEntity
		implements BlockEntitySubLevelActor {

	private static final int TANK_CAPACITY = 1000;
	private static final int CAULDRON_LEVEL_AMOUNT = 333;
	private static final String TAG_BOUND_BUCKET_POS = "BoundBucketPos";

	private SmartFluidTankBehaviour tank;
	private BlockPos boundBucketPos;
	private int scanTickCounter;
	private boolean nearInfiniteWater;
	private int cauldronFillProgress;

	public WaterIntakeBlockEntity(BlockPos pos, BlockState state) {
		this(Createfirefightingadd.WATER_INTAKE_BE.get(), pos, state);
	}

	public WaterIntakeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		tank = SmartFluidTankBehaviour.single(this, TANK_CAPACITY);
		tank.getPrimaryHandler().setValidator(stack -> stack.getFluid().is(FluidTags.WATER));
		tank.allowInsertion();
		tank.allowExtraction();
		tank.whenFluidUpdates(this::notifyUpdate);
		behaviours.add(tank);
	}

	@Override
	public float calculateStressApplied() {
		float impact = 4.0f;
		this.lastStressApplied = impact;
		return impact;
	}

	private int getPumpRate() {
		return Math.max(1, (int)(Math.abs(getSpeed()) / 2));
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide())
			return;

		Direction facing = getBlockState().getValue(WaterIntakeBlock.FACING);
		if (!level.hasSignal(worldPosition.relative(facing), facing))
			return;

		if (getSpeed() == 0)
			return;

		scanTickCounter++;
		if (scanTickCounter % Config.intakeScanInterval == 0) {
			scanForInfiniteWater();
		}

		if (!nearInfiniteWater)
			return;

		tryWirelessTransfer();

		int pumpRate = getPumpRate();
		fillInternalTank(pumpRate);
		tryOutputFluid(pumpRate);
	}

	private void fillInternalTank(int pumpRate) {
		FluidStack fluid = tank.getPrimaryHandler().getFluidInTank(0);
		int space = TANK_CAPACITY - fluid.getAmount();
		if (space <= 0)
			return;
		int amount = Math.min(space, pumpRate);
		tank.getPrimaryHandler().fill(
			new FluidStack(Fluids.WATER, amount), IFluidHandler.FluidAction.EXECUTE);
	}

	private void scanForInfiniteWater() {
		SubLevel mySL = Sable.HELPER.getContaining(this);
		Level scanLevel = mySL != null ? mySL.getLevel() : level;
		BlockPos scanCenter = worldPosition;
		if (mySL != null)
			scanCenter = BlockPos.containing(
				mySL.logicalPose().transformPosition(Vec3.atCenterOf(worldPosition)));

		int range = Config.intakeScanRange;
		for (BlockPos checkPos : BlockPos.betweenClosed(
				scanCenter.offset(-range, -range, -range),
				scanCenter.offset(range, range, range))) {
			if (scanLevel.getBlockState(checkPos).getBlock() == Blocks.WATER
					&& isInfiniteSource(scanLevel, checkPos)) {
				nearInfiniteWater = true;
				return;
			}
		}
		nearInfiniteWater = false;
	}

	private static boolean isInfiniteSource(Level scanLevel, BlockPos pos) {
		int waterNeighbors = 0;
		for (Direction d : Direction.values()) {
			BlockState state = scanLevel.getBlockState(pos.relative(d));
			if (state.getBlock() == Blocks.WATER || state.getFluidState().is(FluidTags.WATER))
				waterNeighbors++;
		}
		return waterNeighbors >= 2;
	}

	private void tryOutputFluid(int pumpRate) {
		Direction outputFace = getBlockState().getValue(WaterIntakeBlock.FACING).getOpposite();
		BlockPos neighborPos = worldPosition.relative(outputFace);
		BlockState neighborState = level.getBlockState(neighborPos);

		if (tryFillCauldron(neighborPos, neighborState, pumpRate))
			return;

		IFluidHandler neighborHandler = level.getCapability(
			Capabilities.FluidHandler.BLOCK, neighborPos, outputFace.getOpposite());
		if (neighborHandler != null) {
			pushFluidDirectly(neighborHandler, pumpRate);
			return;
		}

		FluidTransportBehaviour pipeBehaviour = BlockEntityBehaviour.get(level, neighborPos,
			FluidTransportBehaviour.TYPE);
		if (pipeBehaviour != null && pipeBehaviour.canHaveFlowToward(neighborState, outputFace.getOpposite()))
			distributePressureTo(outputFace);
	}

	private void pushFluidDirectly(IFluidHandler handler, int pumpRate) {
		FluidStack fluid = tank.getPrimaryHandler().getFluidInTank(0);
		int toTransfer = Math.min(fluid.getAmount(), pumpRate);
		if (toTransfer > 0) {
			FluidStack drained = tank.getPrimaryHandler().drain(toTransfer, IFluidHandler.FluidAction.SIMULATE);
			int filled = handler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
			if (filled > 0)
				tank.getPrimaryHandler().drain(filled, IFluidHandler.FluidAction.EXECUTE);
		}
	}

	private void distributePressureTo(Direction side) {
		if (getSpeed() == 0)
			return;

		BlockFace start = new BlockFace(worldPosition, side);
		boolean pull = false;
		Set<BlockFace> targets = new HashSet<>();
		Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph = new HashMap<>();

		FluidPropagator.resetAffectedFluidNetworks(level, worldPosition, side.getOpposite());

		if (!hasReachedValidOutputEndpoint(start)) {
			pipeGraph.computeIfAbsent(worldPosition, $ -> Pair.of(0, new IdentityHashMap<>()))
				.getSecond()
				.put(side, pull);
			pipeGraph.computeIfAbsent(start.getConnectedPos(), $ -> Pair.of(1, new IdentityHashMap<>()))
				.getSecond()
				.put(side.getOpposite(), !pull);

			List<Pair<Integer, BlockPos>> frontier = new ArrayList<>();
			Set<BlockPos> visited = new HashSet<>();
			int maxDistance = FluidPropagator.getPumpRange();
			frontier.add(Pair.of(1, start.getConnectedPos()));

			while (!frontier.isEmpty()) {
				Pair<Integer, BlockPos> entry = frontier.remove(0);
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
					if (hasReachedValidOutputEndpoint(blockFace)) {
						pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
							.getSecond()
							.put(face, pull);
						targets.add(blockFace);
						continue;
					}

					FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, connectedPos);
					if (pipeBehaviour == null)
						continue;
					if (visited.contains(connectedPos))
						continue;
					if (distance + 1 >= maxDistance) {
						pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
							.getSecond()
							.put(face, pull);
						targets.add(blockFace);
						continue;
					}

					pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
						.getSecond()
						.put(face, pull);
					pipeGraph.computeIfAbsent(connectedPos, $ -> Pair.of(distance + 1, new IdentityHashMap<>()))
						.getSecond()
						.put(face.getOpposite(), !pull);
					frontier.add(Pair.of(distance + 1, connectedPos));
				}
			}
		}

		Map<Integer, Set<BlockFace>> validFaces = new HashMap<>();
		searchForEndpointRecursively(pipeGraph, targets, validFaces,
			new BlockFace(start.getPos(), start.getOppositeFace()), pull);

		float pressure = Math.abs(getSpeed());
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

	private boolean searchForEndpointRecursively(Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph,
			Set<BlockFace> targets, Map<Integer, Set<BlockFace>> validFaces, BlockFace currentFace, boolean pull) {
		BlockPos currentPos = currentFace.getPos();
		if (!pipeGraph.containsKey(currentPos))
			return false;
		Pair<Integer, Map<Direction, Boolean>> pair = pipeGraph.get(currentPos);
		int distance = pair.getFirst();

		boolean atLeastOneBranchSuccessful = false;
		for (Direction nextFacing : Iterate.directions) {
			if (nextFacing == currentFace.getFace())
				continue;
			Map<Direction, Boolean> map = pair.getSecond();
			if (!map.containsKey(nextFacing))
				continue;

			BlockFace localTarget = new BlockFace(currentPos, nextFacing);
			if (targets.contains(localTarget)) {
				validFaces.computeIfAbsent(distance, $ -> new HashSet<>())
					.add(localTarget);
				atLeastOneBranchSuccessful = true;
				continue;
			}

			if (map.get(nextFacing) != pull)
				continue;
			if (!searchForEndpointRecursively(pipeGraph, targets, validFaces,
				new BlockFace(currentPos.relative(nextFacing), nextFacing.getOpposite()), pull))
				continue;

			validFaces.computeIfAbsent(distance, $ -> new HashSet<>())
				.add(localTarget);
			atLeastOneBranchSuccessful = true;
		}

		if (atLeastOneBranchSuccessful)
			validFaces.computeIfAbsent(distance, $ -> new HashSet<>())
				.add(currentFace);

		return atLeastOneBranchSuccessful;
	}

	private boolean hasReachedValidOutputEndpoint(BlockFace blockFace) {
		BlockPos connectedPos = blockFace.getConnectedPos();
		BlockState connectedState = level.getBlockState(connectedPos);
		BlockEntity blockEntity = level.getBlockEntity(connectedPos);
		Direction face = blockFace.getFace();

		if (PumpBlock.isPump(connectedState)
			&& connectedState.getValue(PumpBlock.FACING).getAxis() == face.getAxis()
			&& blockEntity instanceof PumpBlockEntity) {
			Direction pumpFacing = connectedState.getValue(PumpBlock.FACING);
			return blockFace.getOppositeFace() != pumpFacing;
		}

		FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, connectedPos);
		if (pipe != null && pipe.canHaveFlowToward(connectedState, blockFace.getOppositeFace()))
			return false;

		if (FluidPropagator.hasFluidCapability(level, connectedPos, face.getOpposite()))
			return true;

		if (VanillaFluidTargets.canProvideFluidWithoutCapability(connectedState))
			return true;

		return false;
	}

	private boolean tryFillCauldron(BlockPos cauldronPos, BlockState state, int pumpRate) {
		if (!state.is(Blocks.CAULDRON) && !state.is(Blocks.WATER_CAULDRON))
			return false;

		if (state.is(Blocks.WATER_CAULDRON) && state.getBlock() instanceof LayeredCauldronBlock lcb
			&& lcb.isFull(state))
			return true;

		FluidStack fluid = tank.getPrimaryHandler().getFluidInTank(0);
		int needed = CAULDRON_LEVEL_AMOUNT - cauldronFillProgress;
		int toDrain = Math.min(fluid.getAmount(), Math.min(pumpRate, needed));
		if (toDrain <= 0)
			return true;

		tank.getPrimaryHandler().drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
		cauldronFillProgress += toDrain;

		if (cauldronFillProgress >= CAULDRON_LEVEL_AMOUNT) {
			cauldronFillProgress -= CAULDRON_LEVEL_AMOUNT;
			if (state.is(Blocks.CAULDRON)) {
				level.setBlock(cauldronPos, Blocks.WATER_CAULDRON.defaultBlockState()
					.setValue(LayeredCauldronBlock.LEVEL, 1), Block.UPDATE_ALL);
			} else {
				int current = state.getValue(LayeredCauldronBlock.LEVEL);
				this.level.setBlock(cauldronPos, state.setValue(LayeredCauldronBlock.LEVEL, current + 1),
					Block.UPDATE_ALL);
			}
		}

		return true;
	}

	private void tryWirelessTransfer() {
		if (boundBucketPos == null)
			return;

		SubLevel mySL = Sable.HELPER.getContaining(this);
		Level worldLevel = mySL != null ? mySL.getLevel() : level;

		if (!worldLevel.isLoaded(boundBucketPos)) {
			return;
		}

		BlockEntity be = worldLevel.getBlockEntity(boundBucketPos);
		if (!(be instanceof BucketControllerBlockEntity bucket)) {
			boundBucketPos = null;
			setChanged();
			return;
		}

		double distSqr = Sable.HELPER.distanceSquaredWithSubLevels(
			worldLevel,
			worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
			boundBucketPos.getX() + 0.5, boundBucketPos.getY() + 0.5, boundBucketPos.getZ() + 0.5);
		if (distSqr > (double) Config.wirelessMaxBindDistance * Config.wirelessMaxBindDistance) {
			boundBucketPos = null;
			setChanged();
			return;
		}

		IFluidHandler bucketHandler = bucket.getTankHandler();
		if (bucketHandler == null)
			return;

		FluidStack water = new FluidStack(Fluids.WATER, Config.wirelessTransferSpeed);
		bucketHandler.fill(water, IFluidHandler.FluidAction.EXECUTE);
	}

	public void setBoundBucket(BlockPos pos) {
		this.boundBucketPos = pos;
		notifyUpdate();
	}

	public BlockPos getBoundBucket() {
		return boundBucketPos;
	}

	public IFluidHandler getTankCapability() {
		return tank.getCapability();
	}

	public IFluidHandler getFluidHandler(Direction side) {
		if (side == getBlockState().getValue(WaterIntakeBlock.FACING).getOpposite())
			return tank.getCapability();
		return null;
	}

	@Override
	public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
		super.writeSafe(tag, registries);
		writeBoundBucket(tag);
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		writeBoundBucket(tag);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		readBoundBucket(tag);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		readBoundBucket(tag);
	}

	private void writeBoundBucket(CompoundTag tag) {
		if (boundBucketPos == null)
			return;

		BlockPos pos = transformBoundPosForWrite(boundBucketPos);
		if (pos != null)
			tag.putLong(TAG_BOUND_BUCKET_POS, pos.asLong());
	}

	private void readBoundBucket(CompoundTag tag) {
		if (!tag.contains(TAG_BOUND_BUCKET_POS))
			return;

		BlockPos pos = BlockPos.of(tag.getLong(TAG_BOUND_BUCKET_POS));
		boundBucketPos = transformBoundPosForRead(pos);
	}

	private static BlockPos transformBoundPosForWrite(BlockPos pos) {
		SubLevelSchematicSerializationContext ctx = SubLevelSchematicSerializationContext.getCurrentContext();
		if (ctx == null)
			return pos;
		if (ctx.getType() == SubLevelSchematicSerializationContext.Type.PLACE)
			return ctx.getSetupTransform().apply(pos);
		if (!ctx.getBoundingBox().contains(pos.getX(), pos.getY(), pos.getZ()))
			return null;
		return ctx.getPlaceTransform().apply(pos);
	}

	private static BlockPos transformBoundPosForRead(BlockPos pos) {
		SubLevelSchematicSerializationContext ctx = SubLevelSchematicSerializationContext.getCurrentContext();
		if (ctx == null || ctx.getType() != SubLevelSchematicSerializationContext.Type.PLACE)
			return pos;
		return ctx.getPlaceTransform().apply(pos);
	}
}
