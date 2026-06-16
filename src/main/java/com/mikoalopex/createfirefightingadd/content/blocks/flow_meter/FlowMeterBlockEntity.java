package com.mikoalopex.createfirefightingadd.content.blocks.flow_meter;

import java.util.List;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Experimental flow monitor. It may be changed or removed in a future version.
 * <p>
 * Monitors fluid flow and pressure through an in-line pipe segment. Data is
 * collected by {@link FlowMeterBehaviour} each tick and displayed through
 * Engineer's Goggles via {@link IHaveGoggleInformation}.
 */
public class FlowMeterBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	// Cached monitoring data (written by FlowMeterBehaviour each tick)
	float cachedInboundPressure;
	float cachedOutboundPressure;
	FluidStack cachedFluid = FluidStack.EMPTY;
	boolean cachedInbound;
	int cachedPumpSpeed;
	int cachedPumpDistance = -1;

	private FlowMeterBehaviour meterBehaviour;
	private int syncTimer;

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
		com.simibubi.create.foundation.utility.CreateLang.builder()
			.add(Component.translatable("createfirefightingadd.flow_meter.info"))
			.forGoggles(tooltip);

		if (cachedPumpSpeed > 0) {
			tooltip.add(Component.translatable(
				"createfirefightingadd.flow_meter.pump_speed", cachedPumpSpeed));
		} else {
			tooltip.add(Component.translatable("createfirefightingadd.flow_meter.no_pump"));
		}

		// Pressure is usually close to pump speed on a straight pipe run.
		float pressure = Math.max(cachedOutboundPressure, cachedInboundPressure);
		tooltip.add(Component.translatable(
			"createfirefightingadd.flow_meter.pressure",
			String.format("%.1f", pressure)));

		// Flow rate: derived from pressure (Create pump: transfer = pressure / 2 mb/t)
		float flowRate = pressure / 2f;
		tooltip.add(Component.translatable(
			"createfirefightingadd.flow_meter.flow_rate",
			String.format("%.1f", flowRate)));

		// Current fluid
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
			tag.putFloat("InPressure", cachedInboundPressure);
			tag.putFloat("OutPressure", cachedOutboundPressure);
			tag.putInt("PumpSpeed", cachedPumpSpeed);
			tag.putInt("PumpDist", cachedPumpDistance);
			tag.putBoolean("Inbound", cachedInbound);
			if (!cachedFluid.isEmpty())
				tag.put("Fluid", cachedFluid.saveOptional(registries));
		}
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		if (clientPacket) {
			cachedInboundPressure = tag.getFloat("InPressure");
			cachedOutboundPressure = tag.getFloat("OutPressure");
			cachedPumpSpeed = tag.getInt("PumpSpeed");
			cachedPumpDistance = tag.getInt("PumpDist");
			cachedInbound = tag.getBoolean("Inbound");
			cachedFluid = tag.contains("Fluid")
				? FluidStack.parseOptional(registries, tag.getCompound("Fluid"))
				: FluidStack.EMPTY;
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level != null && !level.isClientSide) {
			syncTimer--;
			if (syncTimer <= 0) {
				sendData();
				syncTimer = 20;
			}
		}
	}
}
