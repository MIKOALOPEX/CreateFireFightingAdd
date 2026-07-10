package com.mikoalopex.createfirefightingadd.content.equipment.backtank;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.api.backtank.MultipurposeBacktankFluidApi;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlockEntity;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.ComparatorUtil;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;

import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.BlockFace;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class MultipurposeBacktankBlockEntity extends KineticBlockEntity implements Nameable {
	private static final String AIR_TAG = "Air";
	private static final String AIR_TIMER_TAG = "AirTimer";
	private static final String FLUID_TAG = "Fluid";
	private static final String CUSTOM_NAME_TAG = "CustomName";
	private static final int MAX_ACTIVE_FLUID_TRANSFER = 128;

	private final FluidTank tank = new FluidTank(MultipurposeBacktankFluidApi.FLUID_CAPACITY, stack -> !stack.isEmpty()) {
		@Override
		protected void onContentsChanged() {
			setChanged();
			sendData();
		}
	};

	private ScrollOptionBehaviour<MultipurposeBacktankMode> mode;
	private Component customName;
	private int airLevel;
	private int airLevelTimer;
	private boolean airWasFull;
	private boolean pumpNetworkDirty = true;
	private boolean pumpNetworkNeedsClear = true;
	private int pumpNetworkRefreshTimer;

	public MultipurposeBacktankBlockEntity(BlockPos pos, BlockState state) {
		this(CreateFireFightingAdd.MULTIPURPOSE_BACKTANK_BE.get(), pos, state);
	}

	public MultipurposeBacktankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		mode = new ScrollOptionBehaviour<>(MultipurposeBacktankMode.class,
			Component.translatable("createfirefightingadd.multipurpose_backtank.mode"),
			this, new ModeValueBoxTransform());
		mode.withCallback($ -> {
				setChanged();
				sendData();
				schedulePumpNetworkUpdate();
			});
		behaviours.add(mode);
	}

	@Override
	public void onSpeedChanged(float previousSpeed) {
		super.onSpeedChanged(previousSpeed);
		if (Math.abs(previousSpeed) == Math.abs(getSpeed()))
			return;
		schedulePumpNetworkUpdate();
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null)
			return;
		if (level.isClientSide) {
			spawnChargingParticles();
			return;
		}

		float speed = Math.abs(getSpeed());
		if (speed > 0) {
			refillAir(speed);
			updatePumpPressure(false);
			transferFluid((int) Mth.clamp(speed / 2f, 1, MAX_ACTIVE_FLUID_TRANSFER));
		} else if (pumpNetworkDirty) {
			updatePumpPressure(true);
		}
	}

	private void refillAir(float speed) {
		BlockState state = getBlockState();
		if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED))
			return;
		if (airLevelTimer > 0) {
			airLevelTimer--;
			return;
		}

		int max = BacktankUtil.maxAirWithoutEnchants();
		if (airLevel >= max) {
			if (airLevel > max) {
				airLevel = max;
				setChanged();
				sendData();
			}
			airWasFull = true;
			return;
		}
		airWasFull = false;

		int previousComparator = getComparatorOutput();
		float abs = Math.abs(speed);
		int increment = Mth.clamp(((int) abs - 100) / 20, 1, 5);
		airLevel = Math.min(max, airLevel + increment);
		setChanged();
		if (previousComparator != getComparatorOutput())
			level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
		if (airLevel == max) {
			playAirFilledSound();
			airWasFull = true;
			sendData();
		}
		airLevelTimer = Mth.clamp((int) (128f - abs / 5f) - 108, 0, 20);
	}

	private void playAirFilledSound() {
		if (airWasFull || level == null)
			return;
		level.playSound(null, worldPosition, CreateFireFightingAdd.PNEUMATIC_HAMMER_CHARGE_SOUND.get(),
			SoundSource.BLOCKS, 1.0f, 1.0f);
	}

	private void transferFluid(int amount) {
		if (amount <= 0)
			return;
		Direction side = Direction.DOWN;
		BlockPos targetPos = worldPosition.relative(side);
		if (FluidPropagator.getPipe(level, targetPos) != null)
			return;
		IFluidHandler target = getFluidHandlerAt(level, targetPos, side.getOpposite());
		if (target == null)
			return;

		if (getMode() == MultipurposeBacktankMode.INPUT)
			pullFrom(target, amount);
		else
			pushTo(target, amount);
	}

	private void pullFrom(IFluidHandler source, int amount) {
		if (tank.getSpace() <= 0)
			return;
		FluidStack simulated = source.drain(Math.min(amount, tank.getSpace()), FluidAction.SIMULATE);
		if (simulated.isEmpty())
			return;
		int accepted = tank.fill(simulated, FluidAction.SIMULATE);
		if (accepted <= 0)
			return;
		FluidStack drained = source.drain(simulated.copyWithAmount(accepted), FluidAction.EXECUTE);
		if (!drained.isEmpty())
			tank.fill(drained, FluidAction.EXECUTE);
	}

	private void pushTo(IFluidHandler target, int amount) {
		FluidStack simulated = tank.drain(amount, FluidAction.SIMULATE);
		if (simulated.isEmpty())
			return;
		int accepted = target.fill(simulated, FluidAction.SIMULATE);
		if (accepted <= 0)
			return;
		FluidStack drained = tank.drain(accepted, FluidAction.EXECUTE);
		if (drained.isEmpty())
			return;
		int filled = target.fill(drained, FluidAction.EXECUTE);
		if (filled < drained.getAmount()) {
			FluidStack leftover = drained.copyWithAmount(drained.getAmount() - filled);
			tank.fill(leftover, FluidAction.EXECUTE);
		}
	}

	private static IFluidHandler getFluidHandlerAt(Level level, BlockPos pos, Direction side) {
		if (!level.isLoaded(pos))
			return null;
		return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
	}

	private boolean isActivelyPumping() {
		return getSpeed() != 0;
	}

	private boolean isConnectedToBottomCreatePipe() {
		return level != null && FluidPropagator.getPipe(level, worldPosition.relative(Direction.DOWN)) != null;
	}

	public void schedulePumpNetworkUpdate() {
		pumpNetworkDirty = true;
		pumpNetworkNeedsClear = true;
		pumpNetworkRefreshTimer = 0;
	}

	private void updatePumpPressure(boolean forceClearOnly) {
		if (level == null || level.isClientSide)
			return;

		boolean shouldDistribute = false;
		if (pumpNetworkDirty || pumpNetworkNeedsClear) {
			clearBottomPipeNetwork();
			pumpNetworkDirty = false;
			pumpNetworkNeedsClear = false;
			shouldDistribute = true;
		}

		if (forceClearOnly || getSpeed() == 0)
			return;

		if (!shouldDistribute) {
			if (pumpNetworkRefreshTimer > 0) {
				pumpNetworkRefreshTimer--;
				return;
			}
			shouldDistribute = !bottomPipeHasPressure();
		}

		if (!shouldDistribute)
			return;

		pumpNetworkRefreshTimer = 20;
		distributePressureToBottom();
	}

	private void clearBottomPipeNetwork() {
		BlockPos pipePos = worldPosition.relative(Direction.DOWN);
		FluidPropagator.propagateChangedPipe(level, pipePos, level.getBlockState(pipePos));
	}

	private boolean bottomPipeHasPressure() {
		BlockPos pipePos = worldPosition.relative(Direction.DOWN);
		FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pipePos);
		return pipe != null && pipe.hasAnyPressure();
	}

	private void distributePressureToBottom() {
		Direction side = Direction.DOWN;
		BlockFace start = new BlockFace(worldPosition, side);
		boolean pull = getMode() == MultipurposeBacktankMode.INPUT;
		Set<BlockFace> targets = new HashSet<>();
		Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph = new HashMap<>();

		if (!pull)
			FluidPropagator.resetAffectedFluidNetworks(level, worldPosition, side.getOpposite());

		if (!hasEndpoint(level, start, pull)) {
			recordPipeFace(pipeGraph, worldPosition, 0, side, pull);
			recordPipeFace(pipeGraph, start.getConnectedPos(), 1, side.getOpposite(), !pull);

			Queue<Pair<Integer, BlockPos>> frontier = new ArrayDeque<>();
			Set<BlockPos> visited = new HashSet<>();
			int maxDistance = FluidPropagator.getPumpRange();
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

		float pressure = Math.abs(getSpeed());
		for (Set<BlockFace> set : validFaces.values()) {
			int parallelBranches = Math.max(1, set.size() - 1);
			for (BlockFace face : set) {
				BlockPos pipePos = face.getPos();
				Direction pipeSide = face.getFace();

				if (pipePos.equals(worldPosition))
					continue;

				Pair<Integer, Map<Direction, Boolean>> entry = pipeGraph.get(pipePos);
				if (entry == null || !entry.getSecond().containsKey(pipeSide))
					continue;
				FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, pipePos);
				if (pipeBehaviour == null)
					continue;

				pipeBehaviour.addPressure(pipeSide, entry.getSecond().get(pipeSide), pressure / parallelBranches);
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

		if (blockEntity != null && blockEntity.getLevel() != null) {
			IFluidHandler capability = blockEntity.getLevel()
				.getCapability(Capabilities.FluidHandler.BLOCK, blockEntity.getBlockPos(), face.getOpposite());
			if (capability != null)
				return true;
		}

		return FluidPropagator.isOpenEnd(world, blockFace.getPos(), face);
	}

	private boolean searchForEndpointRecursively(Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph,
			Set<BlockFace> targets, Map<Integer, Set<BlockFace>> validFaces, BlockFace currentFace, boolean pull) {
		BlockPos currentPos = currentFace.getPos();
		if (!pipeGraph.containsKey(currentPos))
			return false;
		Pair<Integer, Map<Direction, Boolean>> pair = pipeGraph.get(currentPos);
		int distance = pair.getFirst();

		boolean successfulBranch = false;
		for (Direction nextFacing : Direction.values()) {
			if (nextFacing == currentFace.getFace())
				continue;
			Map<Direction, Boolean> map = pair.getSecond();
			if (!map.containsKey(nextFacing))
				continue;

			BlockFace localTarget = new BlockFace(currentPos, nextFacing);
			if (targets.contains(localTarget)) {
				validFaces.computeIfAbsent(distance, $ -> new HashSet<>()).add(localTarget);
				successfulBranch = true;
				continue;
			}

			if (map.get(nextFacing) != pull)
				continue;
			if (!searchForEndpointRecursively(pipeGraph, targets, validFaces,
				new BlockFace(currentPos.relative(nextFacing), nextFacing.getOpposite()), pull))
				continue;

			validFaces.computeIfAbsent(distance, $ -> new HashSet<>()).add(localTarget);
			successfulBranch = true;
		}

		if (successfulBranch)
			validFaces.computeIfAbsent(distance, $ -> new HashSet<>()).add(currentFace);

		return successfulBranch;
	}

	private void spawnChargingParticles() {
		if (getSpeed() == 0)
			return;
		int max = BacktankUtil.maxAirWithoutEnchants();
		if (airLevel >= max)
			return;
		Vec3 center = VecHelper.getCenterOf(worldPosition);
		Vec3 particle = VecHelper.offsetRandomly(center, level.random, 0.65f);
		Vec3 motion = center.subtract(particle);
		level.addParticle(ParticleTypes.POOF, particle.x, particle.y, particle.z, motion.x * 0.02, motion.y * 0.02, motion.z * 0.02);
	}

	public IFluidHandler getFluidHandler(Direction side) {
		boolean bottomAccess = side == null || side == Direction.DOWN;
		boolean fireHoseAccess = isFireHoseAccessSide(side);
		if (bottomAccess || fireHoseAccess)
			return new ExternalFluidHandler(side);
		return null;
	}

	private boolean isFireHoseAccessSide(Direction side) {
		if (level == null || side == null)
			return false;
		BlockPos neighbour = worldPosition.relative(side);
		return level.isLoaded(neighbour) && level.getBlockEntity(neighbour) instanceof FireHoseBlockEntity;
	}

	public FluidStack getFluid() {
		return tank.getFluid();
	}

	public void setFluid(FluidStack stack) {
		tank.setFluid(stack == null || stack.isEmpty()
			? FluidStack.EMPTY
			: stack.copyWithAmount(Math.min(stack.getAmount(), tank.getCapacity())));
	}

	public MultipurposeBacktankMode getMode() {
		return mode == null ? MultipurposeBacktankMode.INPUT : mode.get();
	}

	public int getAirLevel() {
		return airLevel;
	}

	public void setAirLevel(int airLevel) {
		this.airLevel = Mth.clamp(airLevel, 0, BacktankUtil.maxAirWithoutEnchants());
		airWasFull = this.airLevel >= BacktankUtil.maxAirWithoutEnchants();
		setChanged();
		sendData();
	}

	public int getComparatorOutput() {
		float fluidRatio = tank.getFluidAmount() / (float) tank.getCapacity();
		float airRatio = airLevel / (float) BacktankUtil.maxAirWithoutEnchants();
		return ComparatorUtil.fractionToRedstoneLevel(Math.max(fluidRatio, airRatio));
	}

	@Override
	public Component getName() {
		return customName != null
			? customName
			: Component.translatable("block.createfirefightingadd.multipurpose_backtank");
	}

	public void setCustomName(Component customName) {
		this.customName = customName;
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putInt(AIR_TAG, airLevel);
		tag.putInt(AIR_TIMER_TAG, airLevelTimer);
		tag.put(FLUID_TAG, tank.writeToNBT(registries, new CompoundTag()));
		if (customName != null)
			tag.putString(CUSTOM_NAME_TAG, Component.Serializer.toJson(customName, registries));
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		airLevel = Mth.clamp(tag.getInt(AIR_TAG), 0, BacktankUtil.maxAirWithoutEnchants());
		airWasFull = airLevel >= BacktankUtil.maxAirWithoutEnchants();
		airLevelTimer = tag.getInt(AIR_TIMER_TAG);
		if (tag.contains(FLUID_TAG))
			tank.readFromNBT(registries, tag.getCompound(FLUID_TAG));
		if (tag.contains(CUSTOM_NAME_TAG, CompoundTag.TAG_STRING))
			customName = Component.Serializer.fromJson(tag.getString(CUSTOM_NAME_TAG), registries);
	}

	@Override
	protected void applyImplicitComponents(DataComponentInput componentInput) {
		setAirLevel(componentInput.getOrDefault(AllDataComponents.BACKTANK_AIR, 0));
	}

	@Override
	protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
		components.set(AllDataComponents.BACKTANK_AIR, airLevel);
	}

	private static class ModeValueBoxTransform extends ValueBoxTransform.Sided {
		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 8, 13);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return state.hasProperty(MultipurposeBacktankBlock.HORIZONTAL_FACING)
				&& direction == state.getValue(MultipurposeBacktankBlock.HORIZONTAL_FACING).getClockWise();
		}
	}

	private class ExternalFluidHandler implements IFluidHandler {
		private final Direction side;

		private ExternalFluidHandler(Direction side) {
			this.side = side;
		}

		@Override
		public int getTanks() {
			return tank.getTanks();
		}

		@Override
		public FluidStack getFluidInTank(int tankIndex) {
			return tank.getFluidInTank(tankIndex);
		}

		@Override
		public int getTankCapacity(int tankIndex) {
			return tank.getTankCapacity(tankIndex);
		}

		@Override
		public boolean isFluidValid(int tankIndex, FluidStack stack) {
			return tank.isFluidValid(tankIndex, stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			boolean allowed = canFill();
			return allowed ? tank.fill(resource, action) : 0;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			boolean allowed = canDrain();
			return allowed ? tank.drain(resource, action) : FluidStack.EMPTY;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			boolean allowed = canDrain();
			return allowed ? tank.drain(maxDrain, action) : FluidStack.EMPTY;
		}

		private boolean canFill() {
			if (!isActivelyPumping())
				return true;
			if (getMode() != MultipurposeBacktankMode.INPUT)
				return false;
			return isConnectedToBottomCreatePipe() || isFireHoseAccessSide(side);
		}

		private boolean canDrain() {
			if (!isActivelyPumping())
				return true;
			if (getMode() != MultipurposeBacktankMode.OUTPUT)
				return false;
			return isConnectedToBottomCreatePipe() || isFireHoseAccessSide(side);
		}
	}
}
