package com.mikoalopex.createfirefightingadd.api.flowmeter;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;

/** Public, read-only capability for fluid meter integrations, independent of CC and Synaxis. */
public final class FlowMeterApi {
	public static final BlockCapability<IFlowMeter, Void> READING = BlockCapability.createVoid(
		ResourceLocation.fromNamespaceAndPath("createfirefightingadd", "flow_meter"), IFlowMeter.class);

	private FlowMeterApi() {
	}

	/**
	 * Must be called on the server thread. Returns empty on the client, for unloaded
	 * chunks, or when no meter is present; does not load chunks.
	 */
	public static Optional<FlowMeterReading> read(Level level, BlockPos pos) {
		if (level.isClientSide || !level.isLoaded(pos))
			return Optional.empty();
		IFlowMeter meter = level.getCapability(READING, pos, null);
		return meter == null ? Optional.empty() : Optional.of(meter.getReading());
	}
}
