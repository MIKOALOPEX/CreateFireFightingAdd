package com.mikoalopex.createfirefightingadd.content.blocks.flow_meter;

import java.util.List;
import java.util.Map;

import com.mikoalopex.createfirefightingadd.api.flowmeter.FlowMeterApi;
import com.mikoalopex.createfirefightingadd.api.flowmeter.FlowMeterReading;
import com.mikoalopex.createfirefightingadd.api.flowmeter.IFlowMeter;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/** Exercises raw readings and capability access without either optional integration installed. */
public final class FlowMeterApiTest {
	public static void main(String[] args) throws Exception {
		bootstrap();
		for (String optional : List.of("dan200.computercraft.api.peripheral.IPeripheral",
				"com.verr1.synaxis.foundation.cimulink.game.body.PlantPort")) {
			try {
				Class.forName(optional);
				throw new AssertionError("Optional dependency leaked into the core regression classpath: " + optional);
			} catch (ClassNotFoundException expected) {
			}
		}
		Class.forName("com.mikoalopex.createfirefightingadd.CreateFireFightingAdd", false,
			FlowMeterApiTest.class.getClassLoader()).getDeclaredMethods();
		var type = meterType();
		var meter = new FlowMeterBlockEntity(type, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
		check(meter.getReading().equals(FlowMeterReading.EMPTY), "Empty meter sample");
		meter.cachedInboundPressure = 15.5f;
		meter.cachedOutboundPressure = 32.25f;
		meter.cachedPumpSpeed = 64;
		meter.cachedFluid = new FluidStack(Fluids.WATER, 1000);
		meter.displayPressure = 900;
		meter.displayFlow = 450;
		FlowMeterReading sample = meter.getReading();
		check(sample.pressure() == 32.25f && sample.estimatedFlowRate() == 16.125f, "Raw fractional readings");
		check(sample.inboundPressure() == 15.5f && sample.outboundPressure() == 32.25f, "Both pressure samples");
		check(sample.pumpSpeed() == 64 && sample.hasFluid(), "Pump and fluid detection");
		check(sample.fluidId().equals(ResourceLocation.withDefaultNamespace("water")), "Registered fluid ID");
		meter.cachedFluid.setAmount(1);
		check(meter.getReading().equals(sample), "Fluid sample amount is not a measured flow rate");
		meter.cachedFluid = new FluidStack(Fluids.LAVA, 1);
		check(meter.getReading().fluidId().equals(ResourceLocation.withDefaultNamespace("lava")), "Fluid identity changes");
		check(sample.fluidId().equals(ResourceLocation.withDefaultNamespace("water")), "Snapshots remain immutable");
		meter.cachedFluid = FluidStack.EMPTY;
		check(!meter.getReading().hasFluid() && meter.getReading().pressure() == 32.25f, "Pressure without detected fluid");
		var event = registrationEvent();
		event.registerBlockEntity(FlowMeterApi.READING, type, (be, context) -> be);
		IFlowMeter capability = FlowMeterApi.READING.getCapability(null, BlockPos.ZERO,
			meter.getBlockState(), meter, null);
		check(capability == meter && capability.getReading().equals(meter.getReading()), "Read-only capability");
		check(FlowMeterApi.READING.getCapability(null, BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null, null) == null,
			"Unrelated blocks expose no reading");
		System.out.println("Flow meter snapshots, fluid IDs and dependency-free capability passed");
	}

	static void bootstrap() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
		net.minecraft.server.Bootstrap.bootStrap();
	}

	static BlockEntityType<FlowMeterBlockEntity> meterType() {
		var registry = (net.minecraft.core.MappedRegistry<BlockEntityType<?>>)
			net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE;
		registry.unfreeze();
		BlockEntityType<FlowMeterBlockEntity> type = BlockEntityType.Builder
			.of(FlowMeterBlockEntity::new, Blocks.CHEST).build(null);
		net.minecraft.core.Registry.register(registry, "test:flow_meter", type);
		registry.freeze();
		return type;
	}

	static RegisterCapabilitiesEvent registrationEvent() throws Exception {
		var constructor = RegisterCapabilitiesEvent.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		return constructor.newInstance();
	}

	static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
