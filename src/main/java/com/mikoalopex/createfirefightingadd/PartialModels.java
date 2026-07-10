package com.mikoalopex.createfirefightingadd;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

public class PartialModels {

	public static final PartialModel HIGH_PRESSURE_PUMP_COG = block("high_pressure_pump_cog");
	public static final PartialModel MULTIPURPOSE_BACKTANK_COG = block("multipurpose_backtank_cog");
	public static final PartialModel MULTIPURPOSE_BACKTANK_SHAFT = block("multipurpose_backtank_shaft");
	public static final PartialModel FLUID_FLOW_METER_DIAL = block("fluid_flow_meter/dial");
	public static final PartialModel FLUID_FLOW_METER_GREEN_POINTER_EAST = block("fluid_flow_meter/green_pointer_east");
	public static final PartialModel FLUID_FLOW_METER_GREEN_POINTER_WEST = block("fluid_flow_meter/green_pointer_west");
	public static final PartialModel FLUID_FLOW_METER_PURPLE_POINTER_EAST = block("fluid_flow_meter/purple_pointer_east");
	public static final PartialModel FLUID_FLOW_METER_PURPLE_POINTER_WEST = block("fluid_flow_meter/purple_pointer_west");
	public static final PartialModel PNEUMATIC_HAMMER_BASE = item("pneumatic_hammer/base");
	public static final PartialModel PNEUMATIC_HAMMER_COG = item("pneumatic_hammer/cog");

	private static PartialModel block(String path) {
		return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateFireFightingAdd.MODID, "block/" + path));
	}

	private static PartialModel item(String path) {
		return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateFireFightingAdd.MODID, "item/" + path));
	}

	public static void registerAdditional(ModelEvent.RegisterAdditional event) {
		init();
		event.register(ModelResourceLocation.standalone(HIGH_PRESSURE_PUMP_COG.modelLocation()));
		event.register(ModelResourceLocation.standalone(MULTIPURPOSE_BACKTANK_COG.modelLocation()));
		event.register(ModelResourceLocation.standalone(MULTIPURPOSE_BACKTANK_SHAFT.modelLocation()));
		event.register(ModelResourceLocation.standalone(FLUID_FLOW_METER_DIAL.modelLocation()));
		event.register(ModelResourceLocation.standalone(FLUID_FLOW_METER_GREEN_POINTER_EAST.modelLocation()));
		event.register(ModelResourceLocation.standalone(FLUID_FLOW_METER_GREEN_POINTER_WEST.modelLocation()));
		event.register(ModelResourceLocation.standalone(FLUID_FLOW_METER_PURPLE_POINTER_EAST.modelLocation()));
		event.register(ModelResourceLocation.standalone(FLUID_FLOW_METER_PURPLE_POINTER_WEST.modelLocation()));
		event.register(ModelResourceLocation.standalone(PNEUMATIC_HAMMER_BASE.modelLocation()));
		event.register(ModelResourceLocation.standalone(PNEUMATIC_HAMMER_COG.modelLocation()));
	}

	public static void init() {
	}
}
