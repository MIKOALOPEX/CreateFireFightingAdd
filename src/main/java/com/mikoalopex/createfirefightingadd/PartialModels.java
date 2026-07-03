package com.mikoalopex.createfirefightingadd;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

public class PartialModels {

	public static final PartialModel HIGH_PRESSURE_PUMP_COG = block("high_pressure_pump_cog");

	private static PartialModel block(String path) {
		return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateFireFightingAdd.MODID, "block/" + path));
	}

	public static void registerAdditional(ModelEvent.RegisterAdditional event) {
		init();
		event.register(ModelResourceLocation.standalone(HIGH_PRESSURE_PUMP_COG.modelLocation()));
	}

	public static void init() {
	}
}
