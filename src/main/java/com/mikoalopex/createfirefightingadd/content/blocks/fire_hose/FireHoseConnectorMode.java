package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import net.minecraft.util.StringRepresentable;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;

public enum FireHoseConnectorMode implements StringRepresentable, INamedIconOptions {
	IDLE("idle"),
	FIXED("fixed"),
	FREE("free");

	private final String serializedName;

	FireHoseConnectorMode(String serializedName) {
		this.serializedName = serializedName;
	}

	public FireHoseConnectorMode next() {
		FireHoseConnectorMode[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	@Override
	public AllIcons getIcon() {
		return switch (this) {
			case IDLE -> AllIcons.I_PAUSE;
			case FIXED -> AllIcons.I_TARGET;
			case FREE -> AllIcons.I_DICE;
		};
	}

	@Override
	public String getTranslationKey() {
		return "createfirefightingadd.fire_hose_connector.mode." + serializedName;
	}
}
