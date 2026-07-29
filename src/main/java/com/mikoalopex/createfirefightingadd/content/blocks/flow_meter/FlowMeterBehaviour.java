package com.mikoalopex.createfirefightingadd.content.blocks.flow_meter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import com.mikoalopex.createfirefightingadd.content.blocks.FluidAccessoryDebugLog;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Extends {@link FluidTransportBehaviour} to passively monitor pressure and flow
 * on the pipe connections along the FACING axis. Data is read <b>before</b>
 * {@code super.tick()} because the parent tick consumes/clears transient flow
 * and pressure data during fluid transfer.
 */
public class FlowMeterBehaviour extends FluidTransportBehaviour {

	private static final int SCAN_INTERVAL = 10;
	private static final int MAX_PUMP_SCAN_DISTANCE = 64;

	private int scanTimer;

	public FlowMeterBehaviour(SmartBlockEntity be) {
		super(be);
	}

	@Override
	public boolean canHaveFlowToward(BlockState state, Direction direction) {
		if (!(state.getBlock() instanceof FlowMeterBlock))
			return false;
		return direction.getAxis() == state.getValue(FlowMeterBlock.FACING).getAxis();
	}

	@Override
	public boolean canPullFluidFrom(FluidStack fluid, BlockState state, Direction direction) {
		return direction.getAxis() == state.getValue(FlowMeterBlock.FACING).getAxis();
	}

	@Override
	public void tick() {
		if (debugServerSide())
			logConnectionState("PRE_SUPER");

		// Read monitoring data before super.tick() consumes transient flow data.
		if (blockEntity instanceof FlowMeterBlockEntity meter
			&& meter.getLevel() != null
			&& !meter.getLevel().isClientSide
			&& interfaces != null) {

			readPressureAndFlow(meter);

			scanTimer--;
			if (scanTimer <= 0) {
				updatePumpSample(meter);
				scanTimer = SCAN_INTERVAL;
			}
		}

		super.tick();

		if (debugServerSide())
			logConnectionState("POST_SUPER");
	}

	private void readPressureAndFlow(FlowMeterBlockEntity meter) {
		if (interfaces == null)
			return;

		Direction facing = meter.getBlockState().getValue(FlowMeterBlock.FACING);

		// A is the facing side and B is the opposite side.
		float aToBInbound = 0;
		float aToBOutbound = 0;
		float bToAInbound = 0;
		float bToAOutbound = 0;
		FluidStack aToBFluid = FluidStack.EMPTY;
		FluidStack bToAFluid = FluidStack.EMPTY;

		for (Direction dir : Direction.values()) {
			if (dir.getAxis() != facing.getAxis())
				continue;
			PipeConnection conn = interfaces.get(dir);
			if (conn == null)
				continue;

			Couple<Float> pressure = conn.getPressure();
			float in = pressure != null ? pressure.getFirst() : 0;
			float out = pressure != null ? pressure.getSecond() : 0;

			if (dir == facing) {
				aToBInbound = Math.max(aToBInbound, in);
				bToAOutbound = Math.max(bToAOutbound, out);
				if (conn.hasFlow() && conn.getProvidedFluid() != null)
					aToBFluid = conn.getProvidedFluid();
			} else {
				bToAInbound = Math.max(bToAInbound, in);
				aToBOutbound = Math.max(aToBOutbound, out);
				if (conn.hasFlow() && conn.getProvidedFluid() != null)
					bToAFluid = conn.getProvidedFluid();
			}
		}

		// Expose only the dominant flow direction to the meter display.
		float aToBScore = aToBInbound + aToBOutbound;
		float bToAScore = bToAInbound + bToAOutbound;
		if (aToBScore >= bToAScore && aToBScore > 0) {
			meter.cachedInboundPressure = aToBInbound;
			meter.cachedOutboundPressure = aToBOutbound;
			meter.cachedFluid = aToBFluid.isEmpty() ? FluidStack.EMPTY : aToBFluid;
		} else if (bToAScore > 0) {
			meter.cachedInboundPressure = bToAInbound;
			meter.cachedOutboundPressure = bToAOutbound;
			meter.cachedFluid = bToAFluid.isEmpty() ? FluidStack.EMPTY : bToAFluid;
		} else {
			meter.cachedInboundPressure = 0;
			meter.cachedOutboundPressure = 0;
			meter.cachedFluid = FluidStack.EMPTY;
		}
	}

	private boolean debugServerSide() {
		return FluidAccessoryDebugLog.ENABLED
			&& blockEntity.getLevel() != null
			&& !blockEntity.getLevel().isClientSide
			&& interfaces != null;
	}

	private void logConnectionState(String phase) {
		Level level = blockEntity.getLevel();
		if (level == null)
			return;
		Direction facing = blockEntity.getBlockState().getValue(FlowMeterBlock.FACING);
		for (Direction direction : new Direction[] { facing, facing.getOpposite() }) {
			PipeConnection connection = interfaces.get(direction);
			Couple<Float> pressure = connection == null ? null : connection.getPressure();
			BlockPos adjacentPos = blockEntity.getBlockPos().relative(direction);
			IFluidHandler adjacent = level.getCapability(Capabilities.FluidHandler.BLOCK, adjacentPos,
				direction.getOpposite());
			FluidAccessoryDebugLog.log(
				"FLOW_METER_STATE phase={} tick={} pos={} side={} pressure={}/{} hasFlow={} provided={} adjacent={} adjacentAmount={} adjacentState={}",
				phase, level.getGameTime(), blockEntity.getBlockPos().toShortString(), direction,
				pressure == null ? 0 : pressure.getFirst(), pressure == null ? 0 : pressure.getSecond(),
				connection != null && connection.hasFlow(),
				connection == null ? "no_connection" : FluidAccessoryDebugLog.fluid(connection.getProvidedFluid()),
				adjacentPos.toShortString(), FluidAccessoryDebugLog.amount(adjacent),
				FluidAccessoryDebugLog.contents(adjacent));
		}
	}

	private void updatePumpSample(FlowMeterBlockEntity meter) {
		Level level = meter.getLevel();
		if (level == null)
			return;

		meter.cachedPumpSpeed = 0;
		meter.cachedPumpDistance = -1;

		Direction facing = meter.getBlockState().getValue(FlowMeterBlock.FACING);

		for (Direction searchDir : new Direction[] { facing, facing.getOpposite() }) {
			BlockPos startPos = meter.getBlockPos().relative(searchDir);
			int result = findCreatePumpSpeed(level, startPos, meter.getBlockPos(), MAX_PUMP_SCAN_DISTANCE);
			if (result > 0) {
				meter.cachedPumpSpeed = result;
				meter.cachedPumpDistance = 1;
				return;
			}
		}
	}

	private int findCreatePumpSpeed(Level level, BlockPos start, BlockPos origin, int maxDistance) {
		Deque<Pair<Integer, BlockPos>> frontier = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		frontier.addLast(Pair.of(1, start));
		visited.add(origin);

		while (!frontier.isEmpty()) {
			Pair<Integer, BlockPos> entry = frontier.removeFirst();
			int distance = entry.getFirst();
			BlockPos pos = entry.getSecond();

			if (!level.isLoaded(pos) || !visited.add(pos))
				continue;

			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof PumpBlockEntity pumpBe) {
				return (int) Math.abs(pumpBe.getSpeed());
			}

			if (distance >= maxDistance)
				continue;

			FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pos);
			if (pipe == null)
				continue;

			for (Direction face : FluidPropagator.getPipeConnections(level.getBlockState(pos), pipe)) {
				BlockPos next = pos.relative(face);
				if (visited.contains(next))
					continue;
				frontier.addLast(Pair.of(distance + 1, next));
			}
		}
		return 0;
	}
}
