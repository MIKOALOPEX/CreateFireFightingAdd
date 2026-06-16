package com.mikoalopex.createfirefightingadd.content.ponder.index;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.ponder.scenes.FireHoseScenes;
import com.mikoalopex.createfirefightingadd.content.ponder.scenes.HighPressurePumpScenes;
import com.mikoalopex.createfirefightingadd.content.ponder.scenes.NozzleScenes;
import com.mikoalopex.createfirefightingadd.content.ponder.scenes.WaterIntakeAndBucketScenes;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

public class CreateFireFightingPonderScenes {

	public static void register(PonderSceneRegistrationHelper<ResourceLocation> registry) {
		PonderSceneRegistrationHelper<DeferredHolder<?, ?>> helper = registry.withKeyFunction(DeferredHolder::getId);

		helper.forComponents(CreateFireFightingAdd.HIGH_PRESSURE_PUMP)
			.addStoryBoard("high_pressure_pump/range_and_pressure", HighPressurePumpScenes::rangeAndPressure,
				AllCreatePonderTags.FLUIDS);

		helper.forComponents(CreateFireFightingAdd.CONE_NOZZLE, CreateFireFightingAdd.FLAT_NOZZLE)
			.addStoryBoard("nozzle/basic_spray", NozzleScenes::basicSpray, AllCreatePonderTags.FLUIDS)
			.addStoryBoard("nozzle/fire_and_processing", NozzleScenes::fireAndProcessing, AllCreatePonderTags.FLUIDS)
			.addStoryBoard("nozzle/fluid_variants", NozzleScenes::fluidVariants, AllCreatePonderTags.FLUIDS);

		helper.forComponents(CreateFireFightingAdd.WATER_INTAKE, CreateFireFightingAdd.BUCKET_CONTROLLER)
			.addStoryBoard("water_intake/collection_and_binding",
				WaterIntakeAndBucketScenes::collectionAndBinding, AllCreatePonderTags.FLUIDS)
			.addStoryBoard("bucket_controller/large_area_spray",
				WaterIntakeAndBucketScenes::largeAreaSpray, AllCreatePonderTags.FLUIDS);

		helper.forComponents(CreateFireFightingAdd.FIRE_HOSE_ITEM)
			.addStoryBoard("fire_hose/connection_and_relay",
				FireHoseScenes::connectionAndRelay, AllCreatePonderTags.FLUIDS);
	}
}
