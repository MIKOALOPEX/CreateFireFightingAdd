package com.mikoalopex.createfirefightingadd.integration.synaxis;

import java.util.List;

import com.mikoalopex.createfirefightingadd.api.flowmeter.FlowMeterReading;
import com.mikoalopex.createfirefightingadd.content.blocks.flow_meter.FlowMeterBlockEntity;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentSchema;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentTypeId;
import com.verr1.synaxis.foundation.cimulink.core.component.ExecutionDomain;
import com.verr1.synaxis.foundation.cimulink.core.signal.PortDef;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalType;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalValue;
import com.verr1.synaxis.foundation.cimulink.game.body.GameThreadPlantPort;
import com.verr1.synaxis.foundation.cimulink.game.endpoint.EndpointAddress;
import com.verr1.synaxis.foundation.cimulink.game.endpoint.EndpointId;
import com.verr1.synaxis.foundation.physics.SynaxisPhysics;

import net.minecraft.world.level.Level;

/**
 * Game-tick sensor using Synaxis's native external-device provider.
 * Signal types have no string variant, so fluid identity is available through the Java/CC API only.
 */
public final class FlowMeterPlantPort implements GameThreadPlantPort {
	public static final ComponentTypeId TYPE = ComponentTypeId.of("createfirefightingadd:flow_meter");
	public static final ComponentSchema SCHEMA = ComponentSchema.of(List.of(), List.of(
		PortDef.output("pressure", SignalType.REAL),
		PortDef.output("inbound_pressure", SignalType.REAL),
		PortDef.output("outbound_pressure", SignalType.REAL),
		PortDef.output("estimated_flow_rate", SignalType.REAL),
		PortDef.output("pump_speed", SignalType.REAL),
		PortDef.output("has_fluid", SignalType.BOOLEAN)));

	private final FlowMeterBlockEntity meter;
	private final EndpointId endpoint;
	private final String name;

	public FlowMeterPlantPort(FlowMeterBlockEntity meter, EndpointId endpoint, String name) {
		this.meter = meter;
		this.endpoint = endpoint;
		this.name = name == null || name.isBlank() ? "Flow Meter" : name;
	}

	@Override
	public EndpointId endpointId() {
		return endpoint;
	}

	@Override
	public EndpointAddress address() {
		Level level = meter.getLevel();
		return level == null ? EndpointAddress.of(Level.OVERWORLD, meter.getBlockPos())
			: EndpointAddress.of(level, meter.getBlockPos())
				.withBody(SynaxisPhysics.bodyAt(level, meter.getBlockPos()));
	}

	@Override
	public String deviceName() {
		return name;
	}

	@Override
	public ComponentTypeId componentType() {
		return TYPE;
	}

	@Override
	public ComponentSchema schema() {
		return SCHEMA;
	}

	@Override
	public boolean supportsDomain(ExecutionDomain domain) {
		return domain == ExecutionDomain.GAME_TICK;
	}

	@Override
	public void applyInput(String port, SignalValue value) {
		// This sensor has no input ports and cannot modify the pipe network.
	}

	@Override
	public SignalValue readOutput(String port) {
		FlowMeterReading reading = meter.isRemoved() ? FlowMeterReading.EMPTY : meter.getReading();
		return switch (port) {
			case "pressure" -> new SignalValue.Real(reading.pressure());
			case "inbound_pressure" -> new SignalValue.Real(reading.inboundPressure());
			case "outbound_pressure" -> new SignalValue.Real(reading.outboundPressure());
			case "estimated_flow_rate" -> new SignalValue.Real(reading.estimatedFlowRate());
			case "pump_speed" -> new SignalValue.Real(reading.pumpSpeed());
			case "has_fluid" -> new SignalValue.Bool(reading.hasFluid());
			default -> null;
		};
	}
}
