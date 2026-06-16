package com.createfireworkadd.createfirefightingadd.content.ponder;

import com.createfireworkadd.createfirefightingadd.Createfirefightingadd;
import com.createfireworkadd.createfirefightingadd.content.ponder.index.CreateFireFightingPonderScenes;
import com.createfireworkadd.createfirefightingadd.content.ponder.index.CreateFireFightingPonderTags;
import com.simibubi.create.foundation.ponder.CreatePonderPlugin;

import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.registration.IndexExclusionHelper;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class CreateFireFightingPonderPlugin extends CreatePonderPlugin {

	@Override
	public String getModId() {
		return Createfirefightingadd.MODID;
	}

	@Override
	public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
		CreateFireFightingPonderScenes.register(helper);
	}

	@Override
	public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
		CreateFireFightingPonderTags.register(helper);
	}

	@Override
	public void registerSharedText(SharedTextRegistrationHelper helper) {
	}

	@Override
	public void onPonderLevelRestore(PonderLevel ponderLevel) {
	}

	@Override
	public void indexExclusions(IndexExclusionHelper helper) {
	}
}
