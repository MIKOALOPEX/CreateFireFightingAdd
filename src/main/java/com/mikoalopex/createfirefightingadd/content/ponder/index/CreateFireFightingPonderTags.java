package com.mikoalopex.createfirefightingadd.content.ponder.index;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;

import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

public class CreateFireFightingPonderTags {

	public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
		PonderTagRegistrationHelper<ItemLike> itemHelper = helper.withKeyFunction(RegisteredObjectsHelper::getKeyOrThrow);

		itemHelper.addToTag(AllCreatePonderTags.FLUIDS)
			.add(CreateFireFightingAdd.HIGH_PRESSURE_PUMP.get())
			.add(CreateFireFightingAdd.CONE_NOZZLE.get())
			.add(CreateFireFightingAdd.FLAT_NOZZLE.get())
			.add(CreateFireFightingAdd.WATER_INTAKE.get())
			.add(CreateFireFightingAdd.BUCKET_CONTROLLER.get())
			.add(CreateFireFightingAdd.FIRE_HOSE_ITEM.get());
	}
}
