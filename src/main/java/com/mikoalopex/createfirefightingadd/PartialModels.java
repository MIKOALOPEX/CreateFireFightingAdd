package com.mikoalopex.createfirefightingadd;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public class PartialModels {

	public static final PartialModel

		HIGH_PRESSURE_PUMP_COG = block("high_pressure_pump_cog");

	private static PartialModel block(String path) {
		return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateFireFightingAdd.MODID, "block/" + path));
	}

	public static void init() {}
}
