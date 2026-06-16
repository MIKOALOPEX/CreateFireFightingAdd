package com.createfireworkadd.createfirefightingadd.content.blocks.flow_meter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * Experimental flow monitor. It may be changed or removed in a future version.
 * <p>
 * Extends {@link FluidTransportBehaviour} to passively monitor pressure and flow
 * on the pipe connections along the FACING axis. Data is read <b>before</b>
 * {@code super.tick()} because the parent tick consumes/clears transient flow
 * and pressure data during fluid transfer.
 */
public class FlowMeterBehaviour extends FluidTransportBehaviour {

	private int scanTimer;
	private static final int SCAN_INTERVAL = 10;

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
		// Read monitoring data before super.tick() consumes transient flow data.
		if (blockEntity instanceof FlowMeterBlockEntity meter
			&& meter.getLevel() != null
			&& !meter.getLevel().isClientSide
			&& interfaces != null) {

			readPressureAndFlow(meter);

			scanTimer--;
			if (scanTimer <= 0) {
				scanForPump(meter);
				scanTimer = SCAN_INTERVAL;
			}
		}

		super.tick();

		// Forward fluid between axial sides after flow processing.
		if (blockEntity instanceof FlowMeterBlockEntity meter
			&& meter.getLevel() != null
			&& !meter.getLevel().isClientSide
			&& interfaces != null
			&& meter.cachedPumpSpeed > 0) {

			forwardFluidBetweenAxialSides(meter);
		}
	}

	private void readPressureAndFlow(FlowMeterBlockEntity meter) {
		if (interfaces == null)
			return;

		Direction facing = meter.getBlockState().getValue(FlowMeterBlock.FACING);

		// Gather pressures from both axial connections, grouped by flow direction
		// ab = FACING -> OPPOSITE (A -> B), ba = OPPOSITE -> FACING (B -> A)
		float abIn = 0, abOut = 0, baIn = 0, baOut = 0;
		FluidStack abFluid = FluidStack.EMPTY, baFluid = FluidStack.EMPTY;

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
				// FACING side: in = A -> B, out = B -> A
				abIn = Math.max(abIn, in);
				baOut = Math.max(baOut, out);
				if (conn.hasFlow() && conn.getProvidedFluid() != null) {
					abFluid = conn.getProvidedFluid();
				}
			} else {
				// OPPOSITE side: in = B -> A, out = A -> B
				baIn = Math.max(baIn, in);
				abOut = Math.max(abOut, out);
				if (conn.hasFlow() && conn.getProvidedFluid() != null) {
					baFluid = conn.getProvidedFluid();
				}
			}
		}

		// Determine dominant flow direction and only expose that one
		float abScore = abIn + abOut;
		float baScore = baIn + baOut;
		if (abScore >= baScore && abScore > 0) {
			meter.cachedInboundPressure = abIn;
			meter.cachedOutboundPressure = abOut;
			meter.cachedFluid = abFluid.isEmpty() ? FluidStack.EMPTY : abFluid;
		} else if (baScore > 0) {
			meter.cachedInboundPressure = baIn;
			meter.cachedOutboundPressure = baOut;
			meter.cachedFluid = baFluid.isEmpty() ? FluidStack.EMPTY : baFluid;
		} else {
			meter.cachedInboundPressure = 0;
			meter.cachedOutboundPressure = 0;
			meter.cachedFluid = FluidStack.EMPTY;
		}
	}

	private void forwardFluidBetweenAxialSides(FlowMeterBlockEntity meter) {
		if (interfaces == null)
			return;

		Level level = meter.getLevel();
		if (level == null || level.isClientSide)
			return;

		Direction facing = meter.getBlockState().getValue(FlowMeterBlock.FACING);
		PipeConnection facingConn = interfaces.get(facing);
		PipeConnection oppositeConn = interfaces.get(facing.getOpposite());
		if (facingConn == null || oppositeConn == null)
			return;
		if (facingConn.getPressure() == null || oppositeConn.getPressure() == null)
			return;

		float facingIn = facingConn.getPressure().getFirst();
		float facingOut = facingConn.getPressure().getSecond();
		float oppositeIn = oppositeConn.getPressure().getFirst();
		float oppositeOut = oppositeConn.getPressure().getSecond();

		if (oppositeIn > 0 && facingOut > 0)
			tryTransfer(level, meter, facing.getOpposite(), facing);
		if (facingIn > 0 && oppositeOut > 0)
			tryTransfer(level, meter, facing, facing.getOpposite());
	}

	private void tryTransfer(Level level, FlowMeterBlockEntity meter, Direction fromDir, Direction toDir) {
		BlockPos fromPos = meter.getBlockPos().relative(fromDir);
		BlockPos toPos = meter.getBlockPos().relative(toDir);

		IFluidHandler target = level.getCapability(
			Capabilities.FluidHandler.BLOCK, toPos, toDir.getOpposite());
		if (target == null)
			return;

		FluidStack sourceFluid = FluidStack.EMPTY;
		IFluidHandler sourceHandler = level.getCapability(
			Capabilities.FluidHandler.BLOCK, fromPos, fromDir.getOpposite());

		if (sourceHandler != null) {
			sourceFluid = sourceHandler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
		} else {
			FluidTransportBehaviour sourcePipe = FluidPropagator.getPipe(level, fromPos);
			if (sourcePipe != null)
				sourceFluid = sourcePipe.getProvidedOutwardFluid(fromDir.getOpposite());
		}

		if (sourceFluid.isEmpty())
			return;

		int rate = Math.max(1, (int) (meter.cachedPumpSpeed / 2f));
		FluidStack toTransfer = sourceFluid.copy();
		toTransfer.setAmount(Math.min(rate, sourceFluid.getAmount()));
		int filled = target.fill(toTransfer, IFluidHandler.FluidAction.EXECUTE);
		if (filled > 0 && sourceHandler != null)
			sourceHandler.drain(filled, IFluidHandler.FluidAction.EXECUTE);
	}

	private void scanForPump(FlowMeterBlockEntity meter) {
		Level level = meter.getLevel();
		if (level == null)
			return;

		meter.cachedPumpSpeed = 0;
		meter.cachedPumpDistance = -1;

		Direction facing = meter.getBlockState().getValue(FlowMeterBlock.FACING);

		for (Direction searchDir : new Direction[] { facing, facing.getOpposite() }) {
			BlockPos startPos = meter.getBlockPos().relative(searchDir);
			int result = bfsFindPump(level, startPos, meter.getBlockPos(), 64);
			if (result > 0) {
				meter.cachedPumpSpeed = result;
				meter.cachedPumpDistance = 1;
				return;
			}
		}
	}

	private int bfsFindPump(Level level, BlockPos start, BlockPos origin, int maxDist) {
		List<Pair<Integer, BlockPos>> frontier = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		frontier.add(Pair.of(1, start));
		visited.add(origin);

		while (!frontier.isEmpty()) {
			Pair<Integer, BlockPos> entry = frontier.remove(0);
			int dist = entry.getFirst();
			BlockPos pos = entry.getSecond();

			if (!level.isLoaded(pos) || !visited.add(pos))
				continue;

			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof PumpBlockEntity pumpBe) {
				return (int) Math.abs(pumpBe.getSpeed());
			}

			if (dist >= maxDist)
				continue;

			FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pos);
			if (pipe == null)
				continue;

			for (Direction face : FluidPropagator.getPipeConnections(level.getBlockState(pos), pipe)) {
				BlockPos next = pos.relative(face);
				if (visited.contains(next))
					continue;
				frontier.add(Pair.of(dist + 1, next));
			}
		}
		return 0;
	}
}
