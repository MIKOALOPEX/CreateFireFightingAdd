package com.mikoalopex.createfirefightingadd.content.blocks.flow_meter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Monitors fluid flow and pressure through an in-line pipe segment. Data is
 * collected by {@link FlowMeterBehaviour} each tick and displayed through
 * Engineer's Goggles via {@link IHaveGoggleInformation}.
 */
public class FlowMeterBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
	private static final String TAG_INBOUND_PRESSURE = "InPressure";
	private static final String TAG_OUTBOUND_PRESSURE = "OutPressure";
	private static final String TAG_PUMP_SPEED = "PumpSpeed";
	private static final String TAG_PUMP_DISTANCE = "PumpDist";
	private static final String TAG_INBOUND = "Inbound";
	private static final String TAG_FLUID = "Fluid";

	private static final Map<Long, DisplayState> CLIENT_DISPLAY_STATES = new ConcurrentHashMap<>();
	private static final float MIN_VISIBLE_TARGET = 0.01f;
	private static final int ZERO_TARGET_GRACE_TICKS = 30;
	private static final int SYNC_INTERVAL = 20;

	// Server-side samples written by FlowMeterBehaviour.
	float cachedInboundPressure;
	float cachedOutboundPressure;
	FluidStack cachedFluid = FluidStack.EMPTY;
	boolean cachedInbound;
	int cachedPumpSpeed;
	int cachedPumpDistance = -1;

	// Client-side display state retained across block-entity refreshes.
	float previousDisplayPressure;
	float displayPressure;
	float previousDisplayFlow;
	float displayFlow;
	float lastNonZeroDisplayPressureTarget;
	int zeroDisplayTargetGraceTicks;

	private FlowMeterBehaviour meterBehaviour;
	private int syncTimer;
	private boolean displayInitialized;

	public FlowMeterBlockEntity(BlockPos pos, BlockState state) {
		this(CreateFireFightingAdd.FLOW_METER_BE.get(), pos, state);
	}

	public FlowMeterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		meterBehaviour = new FlowMeterBehaviour(this);
		behaviours.add(meterBehaviour);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CreateLang.builder()
			.add(Component.translatable("createfirefightingadd.flow_meter.info"))
			.forGoggles(tooltip);

		if (cachedPumpSpeed > 0) {
			tooltip.add(Component.translatable(
				"createfirefightingadd.flow_meter.pump_speed", cachedPumpSpeed));
		} else {
			tooltip.add(Component.translatable("createfirefightingadd.flow_meter.no_pump"));
		}

		float pressure = Math.max(cachedOutboundPressure, cachedInboundPressure);
		tooltip.add(Component.translatable(
			"createfirefightingadd.flow_meter.pressure",
			String.format("%.1f", pressure)));

		// Create pumps transfer approximately half their pressure value in mB/t.
		float flowRate = pressure / 2f;
		tooltip.add(Component.translatable(
			"createfirefightingadd.flow_meter.flow_rate",
			String.format("%.1f", flowRate)));

		if (!cachedFluid.isEmpty()) {
			tooltip.add(Component.translatable(
				"createfirefightingadd.flow_meter.fluid",
				cachedFluid.getHoverName()));
		}

		return true;
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		if (clientPacket) {
			tag.putFloat(TAG_INBOUND_PRESSURE, cachedInboundPressure);
			tag.putFloat(TAG_OUTBOUND_PRESSURE, cachedOutboundPressure);
			tag.putInt(TAG_PUMP_SPEED, cachedPumpSpeed);
			tag.putInt(TAG_PUMP_DISTANCE, cachedPumpDistance);
			tag.putBoolean(TAG_INBOUND, cachedInbound);
			if (!cachedFluid.isEmpty())
				tag.put(TAG_FLUID, cachedFluid.saveOptional(registries));
		}
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		if (clientPacket) {
			cachedInboundPressure = tag.getFloat(TAG_INBOUND_PRESSURE);
			cachedOutboundPressure = tag.getFloat(TAG_OUTBOUND_PRESSURE);
			cachedPumpSpeed = tag.getInt(TAG_PUMP_SPEED);
			cachedPumpDistance = tag.getInt(TAG_PUMP_DISTANCE);
			cachedInbound = tag.getBoolean(TAG_INBOUND);
			cachedFluid = tag.contains(TAG_FLUID)
				? FluidStack.parseOptional(registries, tag.getCompound(TAG_FLUID))
				: FluidStack.EMPTY;
			restoreClientDisplayState();
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level != null && level.isClientSide) {
			tickDisplayAnimation();
			return;
		}

		if (level != null && !level.isClientSide) {
			syncTimer--;
			if (syncTimer <= 0) {
				sendData();
				syncTimer = SYNC_INTERVAL;
			}
		}
	}

	private void tickDisplayAnimation() {
		if (!displayInitialized) {
			previousDisplayPressure = displayPressure = 0;
			previousDisplayFlow = displayFlow = 0;
			displayInitialized = true;
		}

		previousDisplayPressure = displayPressure;
		previousDisplayFlow = displayFlow;

		float targetPressure = Math.max(cachedOutboundPressure, cachedInboundPressure);
		targetPressure = resolveDisplayPressureTarget(targetPressure);
		float targetFlow = targetPressure / 2f;
		displayPressure = approachDisplayValue(displayPressure, targetPressure);
		displayFlow = approachDisplayValue(displayFlow, targetFlow);
		storeClientDisplayState();
	}

	private float resolveDisplayPressureTarget(float targetPressure) {
		if (targetPressure > MIN_VISIBLE_TARGET) {
			lastNonZeroDisplayPressureTarget = targetPressure;
			zeroDisplayTargetGraceTicks = ZERO_TARGET_GRACE_TICKS;
			return targetPressure;
		}

		if (zeroDisplayTargetGraceTicks > 0 && lastNonZeroDisplayPressureTarget > MIN_VISIBLE_TARGET) {
			zeroDisplayTargetGraceTicks--;
			return lastNonZeroDisplayPressureTarget;
		}

		lastNonZeroDisplayPressureTarget = 0;
		return 0;
	}

	private static float approachDisplayValue(float current, float target) {
		if (Math.abs(current - target) < 0.01f)
			return target;
		return Mth.lerp(0.25f, current, target);
	}

	private void restoreClientDisplayState() {
		DisplayState state = CLIENT_DISPLAY_STATES.get(worldPosition.asLong());
		if (state == null)
			return;
		previousDisplayPressure = state.previousPressure;
		displayPressure = state.pressure;
		previousDisplayFlow = state.previousFlow;
		displayFlow = state.flow;
		lastNonZeroDisplayPressureTarget = state.lastNonZeroPressureTarget;
		zeroDisplayTargetGraceTicks = state.zeroGraceTicks;
		displayInitialized = true;
	}

	private void storeClientDisplayState() {
		CLIENT_DISPLAY_STATES.put(worldPosition.asLong(), new DisplayState(
			previousDisplayPressure,
			displayPressure,
			previousDisplayFlow,
			displayFlow,
			lastNonZeroDisplayPressureTarget,
			zeroDisplayTargetGraceTicks));
	}

	static void clearClientDisplayState(BlockPos pos) {
		CLIENT_DISPLAY_STATES.remove(pos.asLong());
	}

	private record DisplayState(float previousPressure, float pressure, float previousFlow, float flow,
			float lastNonZeroPressureTarget, int zeroGraceTicks) {
	}
}
