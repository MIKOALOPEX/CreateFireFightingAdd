package com.mikoalopex.createfirefightingadd.content.fluids.hydraulic_ram;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;

import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.BlockFace;
import net.createmod.catnip.math.VecHelper;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class HydraulicRamBlockEntity extends SmartBlockEntity {
	private static final int MAX_CAPACITY = 1000;
	private static final int MIN_THRESHOLD = 10;
	private static final int THRESHOLD_DIAL_MAX = 500;
	private static final int MIN_INPUT_RATE = 2;
	private static final int MAX_OUTPUT_PRESSURE = 256;
	private static final int STROKE_TICKS = 20;
	private static final int LIFT_TICKS = 5;
	private static final int HOLD_TICKS = 10;
	private static final int OUTPUT_AGE = LIFT_TICKS + HOLD_TICKS;
	private static final int PASSIVE_INPUT_MEMORY_TICKS = 2;
	private static final int PRESSURE_CHECK_INTERVAL = 10;
	private static final int PRESSURE_RETRY_INTERVAL = 100;
	private static final String TAG_BUFFER = "Buffer";
	private static final String TAG_PENDING = "PendingOutput";
	private static final String TAG_ACTIVE_PRESSURE = "ActivePressure";
	private static final String TAG_STROKE_TICKS = "StrokeTicks";
	private static final String TAG_PREVIOUS_STROKE_TICKS = "PreviousStrokeTicks";
	private static final String TAG_OUTPUT_DONE = "OutputDone";

	private final FluidTank bufferTank = new FluidTank(MAX_CAPACITY, stack -> !stack.isEmpty()) {
		@Override
		protected void onContentsChanged() {
			setChanged();
		}
	};
	private FluidStack pendingOutput = FluidStack.EMPTY;
	private final InputHandler inputHandler = new InputHandler();
	private final OutputHandler outputHandler = new OutputHandler();
	private ScrollValueBehaviour thresholdScroll;
	private int inputThisTick;
	private int passiveInputMemoryTicks;
	private int lastInputRate;
	private int activeOutputPressure;
	private int lastStrokeInputRate;
	private int strokeTicks;
	private int previousStrokeTicks;
	private boolean outputPerformed;
	private long lastOutputDrainTick = Long.MIN_VALUE;
	private int outputDrainedThisTick;
	private int pressureRefreshTimer;
	private int pressureRefreshFailures;
	private boolean pressureApplied;

	public HydraulicRamBlockEntity(BlockPos pos, BlockState state) {
		super(CreateFireFightingAdd.HYDRAULIC_RAM_BE.get(), pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		thresholdScroll = new HydraulicRamThresholdBehaviour(
			Component.translatable("createfirefightingadd.hydraulic_ram.threshold"),
			this,
			new ThresholdValueBoxTransform())
			.between(0, THRESHOLD_DIAL_MAX)
			.withFormatter(value -> thresholdFromDial(value) + " mB")
			.withCallback($ -> {
				setChanged();
				sendData();
			});
		thresholdScroll.setValue(THRESHOLD_DIAL_MAX);
		behaviours.add(thresholdScroll);
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null)
			return;

		previousStrokeTicks = strokeTicks;
		if (level.isClientSide)
			return;

		if (strokeTicks > 0) {
			tickStroke();
			inputThisTick = 0;
			return;
		}

		if (!pendingOutput.isEmpty()) {
			tryOutputPending();
			refreshOutputPressure(false);
			inputThisTick = 0;
			return;
		}

		boolean hadPassiveInput = inputThisTick >= MIN_INPUT_RATE || passiveInputMemoryTicks > 0;
		if (!hadPassiveInput)
			pullFromAdjacentContainer();
		lastInputRate = inputThisTick;
		if (lastInputRate >= MIN_INPUT_RATE && bufferTank.getFluidAmount() >= getThreshold())
			beginStroke(lastInputRate);
		if (passiveInputMemoryTicks > 0)
			passiveInputMemoryTicks--;
		inputThisTick = 0;
	}

	private void tickStroke() {
		int age = STROKE_TICKS - strokeTicks;
		if (age == 0)
			spawnWaterDrops();
		if (!outputPerformed && age >= OUTPUT_AGE) {
			outputPerformed = true;
			tryOutputPending();
			refreshOutputPressure(false);
		}
		strokeTicks--;
		if (strokeTicks <= 0) {
			previousStrokeTicks = 0;
			if (pendingOutput.isEmpty())
				clearOutputPressure();
		}
		sendData();
	}

	private void beginStroke(int inputRate) {
		FluidStack buffer = bufferTank.getFluid();
		if (buffer.isEmpty())
			return;

		int outputAmount = Mth.clamp((int) Math.floor(buffer.getAmount() * Config.hydraulicRamOutputRatio),
			0, buffer.getAmount());
		if (outputAmount <= 0) {
			bufferTank.drain(buffer.getAmount(), FluidAction.EXECUTE);
			return;
		}

		pendingOutput = buffer.copyWithAmount(outputAmount);
		bufferTank.drain(buffer.getAmount(), FluidAction.EXECUTE);
		lastStrokeInputRate = inputRate;
		activeOutputPressure = computeOutputPressure(inputRate);
		strokeTicks = STROKE_TICKS;
		previousStrokeTicks = STROKE_TICKS;
		outputPerformed = false;
		outputDrainedThisTick = 0;
		lastOutputDrainTick = Long.MIN_VALUE;
		pressureApplied = false;
		pressureRefreshFailures = 0;
		refreshOutputPressure(true);
		playRamClickSound();
		setChanged();
		sendData();
	}

	private int computeOutputPressure(int inputRate) {
		if (inputRate < MIN_INPUT_RATE)
			return 0;
		return Mth.clamp(inputRate * 4, 0, MAX_OUTPUT_PRESSURE);
	}

	private void pullFromAdjacentContainer() {
		Direction inputSide = getInputSide();
		BlockPos sourcePos = worldPosition.relative(inputSide);
		if (!level.isLoaded(sourcePos))
			return;
		if (isCreatePressureSource(sourcePos))
			return;

		IFluidHandler source = level.getCapability(Capabilities.FluidHandler.BLOCK, sourcePos, inputSide.getOpposite());
		if (source == null)
			return;

		int rate = getContainerInputRate(source);
		if (rate < MIN_INPUT_RATE || bufferTank.getSpace() <= 0)
			return;

		int toDrain = Math.min(rate, bufferTank.getSpace());
		FluidStack simulated = source.drain(toDrain, FluidAction.SIMULATE);
		if (simulated.isEmpty())
			return;
		int accepted = bufferTank.fill(simulated, FluidAction.SIMULATE);
		if (accepted <= 0)
			return;

		FluidStack drained = source.drain(simulated.copyWithAmount(accepted), FluidAction.EXECUTE);
		if (drained.isEmpty())
			return;
		int filled = bufferTank.fill(drained, FluidAction.EXECUTE);
		if (filled > 0)
			recordInput(filled, false);
	}

	private int getContainerInputRate(IFluidHandler handler) {
		int capacity = 0;
		int amount = 0;
		for (int i = 0; i < handler.getTanks(); i++) {
			int tankCapacity = Math.max(0, handler.getTankCapacity(i));
			capacity += tankCapacity;
			FluidStack stack = handler.getFluidInTank(i);
			if (!stack.isEmpty())
				amount += Math.min(stack.getAmount(), tankCapacity);
		}
		if (capacity <= 0 || amount <= 0)
			return 0;
		return (int) Math.floor(amount / (float) capacity * MAX_OUTPUT_PRESSURE);
	}

	private boolean isCreatePressureSource(BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof PumpBlockEntity)
			return true;
		return FluidPropagator.getPipe(level, pos) != null;
	}

	private void tryOutputPending() {
		if (pendingOutput.isEmpty() || !ensurePendingOutputPressure())
			return;

		Direction outputSide = getOutputSide();
		BlockPos targetPos = worldPosition.relative(outputSide);
		IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, outputSide.getOpposite());
		if (target == null)
			return;

		int remainingRate = getRemainingOutputRate();
		if (remainingRate <= 0)
			return;
		FluidStack offered = pendingOutput.copyWithAmount(Math.min(pendingOutput.getAmount(), remainingRate));
		int accepted = target.fill(offered, FluidAction.SIMULATE);
		if (accepted <= 0)
			return;
		FluidStack drained = drainPendingOutput(accepted, FluidAction.EXECUTE, true);
		if (drained.isEmpty())
			return;
		int filled = target.fill(drained, FluidAction.EXECUTE);
		if (filled < drained.getAmount())
			restorePending(drained.copyWithAmount(drained.getAmount() - filled));
		if (pendingOutput.isEmpty())
			clearOutputPressure();
		else if (activeOutputPressure <= 0)
			activeOutputPressure = computeOutputPressure(lastStrokeInputRate);
	}

	private void refreshOutputPressure(boolean force) {
		if (level == null || level.isClientSide)
			return;
		if (pendingOutput.isEmpty() || activeOutputPressure <= 0) {
			clearOutputPressure();
			return;
		}
		if (!force && pressureRefreshTimer-- > 0)
			return;
		if (pressureApplied && hasOutputPressure()) {
			pressureRefreshTimer = PRESSURE_CHECK_INTERVAL;
			pressureRefreshFailures = 0;
			return;
		}

		pressureApplied = distributePressureTo(getOutputSide(), activeOutputPressure);
		if (pressureApplied) {
			pressureRefreshFailures = 0;
			pressureRefreshTimer = PRESSURE_CHECK_INTERVAL;
		} else {
			pressureRefreshFailures++;
			pressureRefreshTimer = pressureRefreshFailures < 3
				? PRESSURE_CHECK_INTERVAL
				: PRESSURE_RETRY_INTERVAL;
		}
	}

	private void clearOutputPressure() {
		if (level == null || level.isClientSide)
			return;
		BlockPos pipePos = worldPosition.relative(getOutputSide());
		FluidPropagator.propagateChangedPipe(level, pipePos, level.getBlockState(pipePos));
		activeOutputPressure = 0;
		pressureApplied = false;
		pressureRefreshFailures = 0;
		pressureRefreshTimer = 0;
	}

	private boolean hasOutputPressure() {
		Direction outputSide = getOutputSide();
		BlockPos pipePos = worldPosition.relative(outputSide);
		FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pipePos);
		if (pipe == null)
			return hasOutputEndpoint(level, new BlockFace(worldPosition, outputSide));
		PipeConnection connection = pipe.getConnection(outputSide.getOpposite());
		if (connection == null || connection.getPressure() == null)
			return false;
		return connection.getPressure().getFirst() > 0 || connection.getPressure().getSecond() > 0;
	}

	private boolean distributePressureTo(Direction side, float pressure) {
		BlockFace start = new BlockFace(worldPosition, side);
		boolean pull = false;
		Set<BlockFace> targets = new HashSet<>();
		Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph = new HashMap<>();

		if (hasOutputEndpoint(level, start))
			return true;

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
			if (!level.isLoaded(currentPos) || !visited.add(currentPos))
				continue;

			BlockState currentState = level.getBlockState(currentPos);
			FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, currentPos);
			if (pipe == null)
				continue;

			for (Direction face : FluidPropagator.getPipeConnections(currentState, pipe)) {
				BlockFace blockFace = new BlockFace(currentPos, face);
				BlockPos connectedPos = blockFace.getConnectedPos();
				if (!level.isLoaded(connectedPos) || blockFace.isEquivalent(start))
					continue;
				if (hasOutputEndpoint(level, blockFace)) {
					recordTarget(pipeGraph, targets, currentPos, distance, face, pull, blockFace);
					continue;
				}

				FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, connectedPos);
				if (pipeBehaviour == null || visited.contains(connectedPos))
					continue;
				if (level.getBlockEntity(connectedPos) instanceof PumpBlockEntity)
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

		Map<Integer, Set<BlockFace>> validFaces = new HashMap<>();
		boolean successfulBranch = searchForEndpointRecursively(pipeGraph, targets, validFaces,
			new BlockFace(start.getPos(), start.getOppositeFace()), pull);

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
		return successfulBranch;
	}

	private void recordTarget(Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph,
			Set<BlockFace> targets, BlockPos pipePos, int distance, Direction face, boolean pull, BlockFace target) {
		recordPipeFace(pipeGraph, pipePos, distance, face, pull);
		targets.add(target);
	}

	private void recordPipeFace(Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph,
			BlockPos pipePos, int distance, Direction face, boolean pull) {
		pipeGraph.computeIfAbsent(pipePos, $ -> Pair.of(distance, new IdentityHashMap<>()))
			.getSecond()
			.put(face, pull);
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

	private boolean hasOutputEndpoint(LevelAccessor world, BlockFace blockFace) {
		BlockPos connectedPos = blockFace.getConnectedPos();
		BlockState connectedState = world.getBlockState(connectedPos);
		BlockEntity blockEntity = world.getBlockEntity(connectedPos);
		Direction face = blockFace.getFace();

		if (PumpBlock.isPump(connectedState)
			&& connectedState.getValue(PumpBlock.FACING).getAxis() == face.getAxis()
			&& blockEntity instanceof PumpBlockEntity pumpBE) {
			boolean pumpFront = pumpBE.getBlockState().getValue(PumpBlock.FACING) == blockFace.getOppositeFace();
			return pumpBE.isPullingOnSide(pumpFront);
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

	private void recordInput(int amount, boolean passive) {
		if (amount <= 0)
			return;
		inputThisTick += amount;
		if (passive)
			passiveInputMemoryTicks = PASSIVE_INPUT_MEMORY_TICKS;
	}

	private int getThreshold() {
		return thresholdScroll == null ? MAX_CAPACITY : thresholdFromDial(thresholdScroll.getValue());
	}

	private static int thresholdFromDial(int value) {
		int clamped = Mth.clamp(value, 0, THRESHOLD_DIAL_MAX);
		return MIN_THRESHOLD + Math.round(clamped / (float) THRESHOLD_DIAL_MAX * (MAX_CAPACITY - MIN_THRESHOLD));
	}

	public Direction getInputSide() {
		return HydraulicRamBlock.inputSide(getBlockState());
	}

	public Direction getOutputSide() {
		return HydraulicRamBlock.outputSide(getBlockState());
	}

	public IFluidHandler getFluidHandler(@Nullable Direction side) {
		if (side == null)
			return null;
		if (side == getInputSide())
			return inputHandler;
		if (side == getOutputSide())
			return outputHandler;
		return null;
	}

	public float getStrokeOffset(float partialTick) {
		float ticks = Mth.lerp(partialTick, previousStrokeTicks, strokeTicks);
		if (ticks <= 0)
			return 0;
		float age = STROKE_TICKS - ticks;
		float ratio = strokeRatio(age);
		return ratio * (2f / 16f);
	}

	private static float strokeRatio(float age) {
		if (age <= LIFT_TICKS)
			return Mth.clamp(age / LIFT_TICKS, 0, 1);
		if (age <= OUTPUT_AGE)
			return 1;
		return Mth.clamp(1f - (age - OUTPUT_AGE) / (STROKE_TICKS - OUTPUT_AGE), 0, 1);
	}

	public void onDirectionChanged() {
		pressureApplied = false;
		pressureRefreshFailures = 0;
		if (!pendingOutput.isEmpty()) {
			ensurePendingOutputPressure();
			pressureRefreshTimer = 0;
			refreshOutputPressure(true);
		} else {
			clearOutputPressure();
		}
		setChanged();
		sendData();
	}

	/** Re-arms hydraulic ram pressure after a connected pipe graph was rebuilt. */
	public static void notifyNetworkChanged(Level level, BlockPos origin) {
		if (level == null || level.isClientSide || !level.isLoaded(origin))
			return;

		Queue<Pair<Integer, BlockPos>> frontier = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		frontier.add(Pair.of(0, origin));
		int maxDistance = FluidPropagator.getPumpRange() + 1;

		while (!frontier.isEmpty()) {
			Pair<Integer, BlockPos> entry = frontier.poll();
			int distance = entry.getFirst();
			BlockPos currentPos = entry.getSecond();
			if (!level.isLoaded(currentPos) || !visited.add(currentPos))
				continue;

			BlockEntity blockEntity = level.getBlockEntity(currentPos);
			if (blockEntity instanceof HydraulicRamBlockEntity ram) {
				BlockPos outputPipe = ram.worldPosition.relative(ram.getOutputSide());
				if (outputPipe.equals(origin) || visited.contains(outputPipe))
					ram.requestPressureRefresh();
				continue;
			}
			if (blockEntity instanceof PumpBlockEntity || distance >= maxDistance)
				continue;

			FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, currentPos);
			if (pipe == null)
				continue;
			BlockState state = level.getBlockState(currentPos);
			for (Direction face : FluidPropagator.getPipeConnections(state, pipe)) {
				BlockPos next = currentPos.relative(face);
				if (!visited.contains(next))
					frontier.add(Pair.of(distance + 1, next));
			}
		}
	}

	private void requestPressureRefresh() {
		if (pendingOutput.isEmpty() || activeOutputPressure <= 0)
			return;
		pressureApplied = false;
		pressureRefreshFailures = 0;
		pressureRefreshTimer = 0;
	}

	private boolean ensurePendingOutputPressure() {
		if (pendingOutput.isEmpty())
			return false;
		if (activeOutputPressure > 0)
			return true;
		int restoredPressure = computeOutputPressure(lastStrokeInputRate);
		activeOutputPressure = restoredPressure > 0 ? restoredPressure : MAX_OUTPUT_PRESSURE;
		return activeOutputPressure > 0;
	}

	private void spawnWaterDrops() {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		Vec3 pos = particleOrigin();
		serverLevel.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y, pos.z,
			6, 0.08, 0.04, 0.08, 0.03);
		level.playSound(null, pos.x, pos.y, pos.z,
			CreateFireFightingAdd.HYDRAULIC_RAM_WATERFLYOFF_SOUND.get(), SoundSource.BLOCKS, 0.10f, 1.0f);
	}

	private void playRamClickSound() {
		if (level == null)
			return;
		Vec3 pos = particleOrigin();
		level.playSound(null, pos.x, pos.y, pos.z,
			CreateFireFightingAdd.HYDRAULIC_RAM_CLICK_SOUND.get(), SoundSource.BLOCKS, 0.55f, 2.0f);
	}

	private Vec3 particleOrigin() {
		Vec3 local = VecHelper.voxelSpace(8, 19, 5);
		Direction facing = getOutputSide();
		Vec3 centered = local.subtract(0.5, 0.5, 0.5);
		Vec3 rotated = switch (facing) {
			case NORTH -> new Vec3(-centered.x, centered.y, -centered.z);
			case EAST -> new Vec3(-centered.z, centered.y, centered.x);
			case WEST -> new Vec3(centered.z, centered.y, -centered.x);
			default -> centered;
		};
		return Vec3.atLowerCornerOf(worldPosition).add(rotated.add(0.5, 0.5, 0.5));
	}

	private int getOutputRate() {
		return Math.max(1, activeOutputPressure / 2);
	}

	private int getRemainingOutputRate() {
		long tick = level == null ? 0 : level.getGameTime();
		if (tick != lastOutputDrainTick) {
			lastOutputDrainTick = tick;
			outputDrainedThisTick = 0;
		}
		return Math.max(0, getOutputRate() - outputDrainedThisTick);
	}

	private FluidStack drainPendingOutput(int amount, FluidAction action) {
		return drainPendingOutput(amount, action, false);
	}

	private FluidStack drainPendingOutput(int amount, FluidAction action, boolean deferPressureClear) {
		if (!outputPerformed || pendingOutput.isEmpty() || amount <= 0)
			return FluidStack.EMPTY;
		int toDrain = Math.min(amount, Math.min(pendingOutput.getAmount(), getRemainingOutputRate()));
		if (toDrain <= 0)
			return FluidStack.EMPTY;
		FluidStack drained = pendingOutput.copyWithAmount(toDrain);
		if (action.execute()) {
			pendingOutput.shrink(toDrain);
			outputDrainedThisTick += toDrain;
			if (pendingOutput.isEmpty())
				pendingOutput = FluidStack.EMPTY;
			if (!deferPressureClear && pendingOutput.isEmpty())
				clearOutputPressure();
			setChanged();
			sendData();
		}
		return drained;
	}

	private void restorePending(FluidStack stack) {
		if (stack.isEmpty())
			return;
		if (pendingOutput.isEmpty()) {
			pendingOutput = stack.copy();
			return;
		}
		if (FluidStack.isSameFluidSameComponents(pendingOutput, stack))
			pendingOutput.grow(stack.getAmount());
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		if (!bufferTank.getFluid().isEmpty())
			tag.put(TAG_BUFFER, bufferTank.getFluid().saveOptional(registries));
		if (!pendingOutput.isEmpty())
			tag.put(TAG_PENDING, pendingOutput.saveOptional(registries));
		tag.putInt(TAG_ACTIVE_PRESSURE, activeOutputPressure);
		tag.putInt("LastStrokeInputRate", lastStrokeInputRate);
		tag.putInt(TAG_STROKE_TICKS, strokeTicks);
		tag.putInt(TAG_PREVIOUS_STROKE_TICKS, previousStrokeTicks);
		tag.putBoolean(TAG_OUTPUT_DONE, outputPerformed);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		bufferTank.setFluid(tag.contains(TAG_BUFFER)
			? FluidStack.parseOptional(registries, tag.getCompound(TAG_BUFFER))
			: FluidStack.EMPTY);
		pendingOutput = tag.contains(TAG_PENDING)
			? FluidStack.parseOptional(registries, tag.getCompound(TAG_PENDING))
			: FluidStack.EMPTY;
		activeOutputPressure = tag.getInt(TAG_ACTIVE_PRESSURE);
		lastStrokeInputRate = tag.getInt("LastStrokeInputRate");
		strokeTicks = tag.getInt(TAG_STROKE_TICKS);
		previousStrokeTicks = tag.getInt(TAG_PREVIOUS_STROKE_TICKS);
		outputPerformed = tag.getBoolean(TAG_OUTPUT_DONE);
	}

	@Override
	public AABB getRenderBoundingBox() {
		return new AABB(worldPosition).inflate(1);
	}

	private class InputHandler implements IFluidHandler {
		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return bufferTank.getFluidInTank(tank);
		}

		@Override
		public int getTankCapacity(int tank) {
			return bufferTank.getTankCapacity(tank);
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return bufferTank.isFluidValid(tank, stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (resource.isEmpty() || strokeTicks > 0 || !pendingOutput.isEmpty())
				return 0;
			int filled = bufferTank.fill(resource, action);
			if (filled > 0 && action.execute())
				recordInput(filled, true);
			return filled;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return FluidStack.EMPTY;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return FluidStack.EMPTY;
		}
	}

	private class OutputHandler implements IFluidHandler {
		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return pendingOutput;
		}

		@Override
		public int getTankCapacity(int tank) {
			return MAX_CAPACITY;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return false;
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			return 0;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			if (resource.isEmpty() || pendingOutput.isEmpty()
				|| !FluidStack.isSameFluidSameComponents(resource, pendingOutput))
				return FluidStack.EMPTY;
			return drainPendingOutput(resource.getAmount(), action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return drainPendingOutput(maxDrain, action);
		}
	}

	private static class ThresholdValueBoxTransform extends ValueBoxTransform {
		private static final Vec3 TANK_TOP_CENTER = VecHelper.voxelSpace(8, 26, 12);

		@Override
		public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
			return VecHelper.rotateCentered(TANK_TOP_CENTER, offsetRotationAngle(state), Axis.Y);
		}

		@Override
		public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack poseStack) {
			TransformStack.of(poseStack)
				.rotateYDegrees(boardRotationAngle(state) + 180)
				.rotateXDegrees(90);
		}

		private static float offsetRotationAngle(BlockState state) {
			return switch (state.getValue(HydraulicRamBlock.FACING)) {
				case NORTH -> 180;
				case EAST -> 90;
				case WEST -> 270;
				default -> 0;
			};
		}

		private static float boardRotationAngle(BlockState state) {
			return switch (state.getValue(HydraulicRamBlock.FACING)) {
				case NORTH -> 180;
				case EAST -> 270;
				case WEST -> 90;
				default -> 0;
			};
		}
	}

	private static class HydraulicRamThresholdBehaviour extends ScrollValueBehaviour {
		public HydraulicRamThresholdBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
			super(label, be, slot);
		}

		@Override
		public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
			return new ValueSettingsBoard(label, THRESHOLD_DIAL_MAX, 10,
				ImmutableList.of(Component.translatable("createfirefightingadd.hydraulic_ram.threshold.value")),
				new ValueSettingsFormatter(settings ->
					Component.literal(Integer.toString(thresholdFromDial(settings.value())))));
		}
	}
}
