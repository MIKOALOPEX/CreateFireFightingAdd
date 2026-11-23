package com.mikoalopex.createfirefightingadd.content.blocks.flow_meter;

import static com.mikoalopex.createfirefightingadd.content.blocks.flow_meter.FlowMeterApiTest.check;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.mikoalopex.createfirefightingadd.integration.computercraft.ComputerCraftFlowMeterCompat;
import com.mikoalopex.createfirefightingadd.integration.computercraft.FlowMeterPeripheral;
import com.mikoalopex.createfirefightingadd.integration.synaxis.SynaxisFlowMeterCompat;
import com.verr1.synaxis.foundation.cimulink.core.component.ExecutionDomain;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalValue;
import com.verr1.synaxis.foundation.cimulink.game.body.GameThreadPlantPort;
import com.verr1.synaxis.foundation.cimulink.game.body.PlantPortProviders;
import com.verr1.synaxis.foundation.cimulink.game.endpoint.EndpointId;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Checks registration and output contracts against the real optional mod APIs. */
public final class FlowMeterIntegrationsTest {
	public static void main(String[] args) throws Exception {
		FlowMeterApiTest.bootstrap();
		var type = FlowMeterApiTest.meterType();
		var meter = new FlowMeterBlockEntity(type, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
		meter.cachedInboundPressure = 80;
		meter.cachedOutboundPressure = 40;
		meter.cachedPumpSpeed = 160;
		meter.cachedFluid = new FluidStack(Fluids.WATER, 1);

		var event = FlowMeterApiTest.registrationEvent();
		ComputerCraftFlowMeterCompat.register(event, type);
		var peripheral = (FlowMeterPeripheral) PeripheralCapability.get()
			.getCapability(null, BlockPos.ZERO, meter.getBlockState(), meter, Direction.NORTH);
		check(peripheral != null && peripheral.getTarget() == meter, "CC capability registration");
		for (Direction side : Direction.values())
			check(peripheral.equals(PeripheralCapability.get().getCapability(null, BlockPos.ZERO,
				meter.getBlockState(), meter, side)), "Same peripheral on every side");
		check(!peripheral.equals(new FlowMeterPeripheral(new FlowMeterBlockEntity(type,
			BlockPos.ZERO, Blocks.CHEST.defaultBlockState()))), "Replaced block entity has a distinct peripheral");
		check(peripheral.getType().equals("createfirefightingadd:flow_meter"), "Namespaced peripheral type");
		Map<String, Object> values = peripheral.getReadings();
		check(values.get("pressure").equals(80f) && values.get("estimatedFlowRate").equals(40f), "CC numeric table");
		check(values.get("fluid").equals(Map.of("name", "minecraft:water")), "CC fluid name format");
		check(peripheral.getFluid().equals(Map.of("name", "minecraft:water")), "CC fluid accessor");
		check(peripheral.getPressure() == 80 && peripheral.getInboundPressure() == 80
			&& peripheral.getOutboundPressure() == 40 && peripheral.getEstimatedFlowRate() == 40
			&& peripheral.getPumpSpeed() == 160, "CC numeric accessors");
		int luaMethods = 0;
		for (var method : FlowMeterPeripheral.class.getDeclaredMethods()) {
			LuaFunction function = method.getAnnotation(LuaFunction.class);
			if (function == null)
				continue;
			luaMethods++;
			check(function.mainThread(), "World readings must run on the server thread");
		}
		check(luaMethods == 7, "CC method discovery");

		SynaxisFlowMeterCompat.register(type);
		EndpointId endpoint = EndpointId.random();
		var port = (GameThreadPlantPort) PlantPortProviders.tryCreate(meter, endpoint, "Meter A").orElseThrow();
		check(port.endpointId().equals(endpoint) && port.deviceName().equals("Meter A"), "Synaxis endpoint identity");
		check(port.componentType().toString().equals("createfirefightingadd:flow_meter"), "Synaxis component ID");
		check(port.address().pos().equals(BlockPos.ZERO), "Synaxis endpoint position");
		check(port.schema().inputs().isEmpty(), "Read-only Synaxis schema");
		check(port.schema().outputs().stream().map(p -> p.name()).collect(Collectors.toSet()).equals(Set.of(
			"pressure", "inbound_pressure", "outbound_pressure", "estimated_flow_rate", "pump_speed", "has_fluid")),
			"Synaxis output names");
		for (var output : port.schema().outputs())
			check(output.type().accepts(port.readOutput(output.name())), "Synaxis output signal type: " + output.name());
		for (ExecutionDomain domain : ExecutionDomain.values())
			check(port.supportsDomain(domain) == (domain == ExecutionDomain.GAME_TICK), "Game-thread sampling only");
		check(port.readOutput("pressure").equals(new SignalValue.Real(80)), "Synaxis pressure");
		check(port.readOutput("estimated_flow_rate").equals(new SignalValue.Real(40)), "Synaxis estimated flow");
		check(port.readOutput("pump_speed").equals(new SignalValue.Real(160)), "Synaxis pump speed");
		check(port.readOutput("has_fluid").equals(new SignalValue.Bool(true)), "Synaxis fluid detection");
		port.applyInput("pressure", new SignalValue.Real(1000));
		check(meter.getReading().pressure() == 80, "Inputs cannot change the meter");
		check(port.readOutput("unknown") == null, "Unknown port follows native sensor convention");

		meter.cachedFluid = FluidStack.EMPTY;
		meter.cachedInboundPressure = meter.cachedOutboundPressure = 0;
		meter.cachedPumpSpeed = 0;
		check(peripheral.getFluid() == null && !peripheral.getReadings().containsKey("fluid"), "No stale CC fluid identity");
		check(peripheral.getReadings().get("hasFluid").equals(false), "CC empty-fluid flag");
		check(port.readOutput("has_fluid").equals(new SignalValue.Bool(false)), "No stale Synaxis fluid detection");
		check(port.readOutput("pressure").equals(new SignalValue.Real(0)), "Live readings after a sample changes");
		meter.setRemoved();
		try {
			peripheral.getReadings();
			throw new AssertionError("Detached meter must not return stale readings");
		} catch (LuaException expected) {
		}
		check(port.readOutput("pressure").equals(new SignalValue.Real(0)), "Removed Synaxis device is neutral");
		System.out.println("CC capability, Lua output contract and Synaxis native provider passed");
	}
}
