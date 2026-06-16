package com.createfireworkadd.createfirefightingadd.content.ponder.index;

import com.createfireworkadd.createfirefightingadd.Createfirefightingadd;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;

import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

public class CreateFireFightingPonderTags {

	public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
		PonderTagRegistrationHelper<ItemLike> itemHelper = helper.withKeyFunction(RegisteredObjectsHelper::getKeyOrThrow);

		itemHelper.addToTag(AllCreatePonderTags.FLUIDS)
			.add(Createfirefightingadd.HIGH_PRESSURE_PUMP.get())
			.add(Createfirefightingadd.CONE_NOZZLE.get())
			.add(Createfirefightingadd.FLAT_NOZZLE.get())
			.add(Createfirefightingadd.WATER_INTAKE.get())
			.add(Createfirefightingadd.BUCKET_CONTROLLER.get())
			.add(Createfirefightingadd.FIRE_HOSE_ITEM.get());
	}
}
