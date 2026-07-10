package com.mikoalopex.createfirefightingadd.content.equipment.backtank;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;

import net.minecraft.util.StringRepresentable;

public enum MultipurposeBacktankMode implements StringRepresentable, INamedIconOptions {
	INPUT("input"),
	OUTPUT("output");

	private final String serializedName;

	MultipurposeBacktankMode(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	@Override
	public AllIcons getIcon() {
		return this == INPUT ? AllIcons.I_ADD : AllIcons.I_MTD_CLOSE;
	}

	@Override
	public String getTranslationKey() {
		return "createfirefightingadd.multipurpose_backtank.mode." + serializedName;
	}
}
