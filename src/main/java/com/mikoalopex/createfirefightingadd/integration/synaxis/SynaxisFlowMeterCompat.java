package com.mikoalopex.createfirefightingadd.integration.synaxis;

import java.util.Optional;

import com.mikoalopex.createfirefightingadd.content.blocks.flow_meter.FlowMeterBlockEntity;
import com.verr1.synaxis.foundation.cimulink.game.body.PlantPortProviders;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Loaded only when Synaxis is present; registration follows its native Create gauge adapter. */
public final class SynaxisFlowMeterCompat {
	private SynaxisFlowMeterCompat() {
	}

	public static void register(BlockEntityType<FlowMeterBlockEntity> type) {
		PlantPortProviders.register(type, (blockEntity, endpoint, name) ->
			blockEntity instanceof FlowMeterBlockEntity meter
				? Optional.of(new FlowMeterPlantPort(meter, endpoint, name)) : Optional.empty());
	}
}
