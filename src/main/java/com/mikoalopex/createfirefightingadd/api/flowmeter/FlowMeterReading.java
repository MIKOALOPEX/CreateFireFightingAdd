package com.mikoalopex.createfirefightingadd.api.flowmeter;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable sample of the dominant pipe flow direction, before client display smoothing.
 * Pressure uses Create's internal pressure units; pump speed is an absolute RPM sample.
 * A null fluid ID means no fluid was detected, not that the pipe has no pressure.
 * This sample does not describe a storage tank or an amount of transferred fluid.
 */
public record FlowMeterReading(float inboundPressure, float outboundPressure, int pumpSpeed,
		@Nullable ResourceLocation fluidId) {
	public static final FlowMeterReading EMPTY = new FlowMeterReading(0, 0, 0, null);

	public float pressure() {
		return Math.max(inboundPressure, outboundPressure);
	}

	/** Estimated mB/t, matching the gauge; this is not a measured transfer rate. */
	public float estimatedFlowRate() {
		return pressure() / 2f;
	}

	public boolean hasFluid() {
		return fluidId != null;
	}
}
