package com.mikoalopex.createfirefightingadd.integration.computercraft;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.api.flowmeter.FlowMeterReading;
import com.mikoalopex.createfirefightingadd.content.blocks.flow_meter.FlowMeterBlockEntity;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

/** A read-only peripheral. All samples are taken on the server thread. */
public final class FlowMeterPeripheral implements IPeripheral {
	private final FlowMeterBlockEntity meter;

	public FlowMeterPeripheral(FlowMeterBlockEntity meter) {
		this.meter = meter;
	}

	@Override
	public String getType() {
		return "createfirefightingadd:flow_meter";
	}

	@Override
	public Object getTarget() {
		return meter;
	}

	@Override
	public boolean equals(@Nullable IPeripheral other) {
		return other instanceof FlowMeterPeripheral peripheral && peripheral.meter == meter;
	}

	/** Returns one consistent sample; the fluid field is absent when no fluid is detected. */
	@LuaFunction(mainThread = true)
	public final Map<String, Object> getReadings() throws LuaException {
		FlowMeterReading reading = read();
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("pressure", reading.pressure());
		result.put("inboundPressure", reading.inboundPressure());
		result.put("outboundPressure", reading.outboundPressure());
		result.put("estimatedFlowRate", reading.estimatedFlowRate());
		result.put("pumpSpeed", reading.pumpSpeed());
		result.put("hasFluid", reading.hasFluid());
		if (reading.hasFluid())
			result.put("fluid", fluidDetails(reading));
		return result;
	}

	@LuaFunction(mainThread = true)
	public final double getPressure() throws LuaException {
		return read().pressure();
	}

	@LuaFunction(mainThread = true)
	public final double getInboundPressure() throws LuaException {
		return read().inboundPressure();
	}

	@LuaFunction(mainThread = true)
	public final double getOutboundPressure() throws LuaException {
		return read().outboundPressure();
	}

	/** Returns estimated mB/t, rather than a count of actual transfers. */
	@LuaFunction(mainThread = true)
	public final double getEstimatedFlowRate() throws LuaException {
		return read().estimatedFlowRate();
	}

	/** Returns the sampled absolute pump speed in RPM, or zero if no running pump was found. */
	@LuaFunction(mainThread = true)
	public final int getPumpSpeed() throws LuaException {
		return read().pumpSpeed();
	}

	/** Returns {name = "namespace:fluid"}, or nil. The meter has no stored fluid amount. */
	@LuaFunction(mainThread = true)
	@Nullable
	public final Map<String, Object> getFluid() throws LuaException {
		return fluidDetails(read());
	}

	@Nullable
	private static Map<String, Object> fluidDetails(FlowMeterReading reading) {
		return reading.hasFluid() ? Map.of("name", reading.fluidId().toString()) : null;
	}

	private FlowMeterReading read() throws LuaException {
		if (meter.isRemoved())
			throw new LuaException("Flow meter is no longer available");
		return meter.getReading();
	}
}
