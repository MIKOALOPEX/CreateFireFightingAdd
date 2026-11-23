package com.mikoalopex.createfirefightingadd.integration.computercraft;

import com.mikoalopex.createfirefightingadd.content.blocks.flow_meter.FlowMeterBlockEntity;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Loaded only when ComputerCraft is present. */
public final class ComputerCraftFlowMeterCompat {
	private ComputerCraftFlowMeterCompat() {
	}

	public static void register(RegisterCapabilitiesEvent event, BlockEntityType<FlowMeterBlockEntity> type) {
		event.registerBlockEntity(PeripheralCapability.get(), type,
			(meter, side) -> new FlowMeterPeripheral(meter));
	}
}
