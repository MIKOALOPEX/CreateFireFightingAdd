package com.mikoalopex.createfirefightingadd.content.kinetics.turbine;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;

import net.minecraft.util.StringRepresentable;

public enum PipelineTurbineDirection implements StringRepresentable, INamedIconOptions {
	CLOCKWISE("clockwise"),
	COUNTER_CLOCKWISE("counter_clockwise");

	private final String serializedName;

	PipelineTurbineDirection(String serializedName) {
		this.serializedName = serializedName;
	}

	public int sign() {
		return this == CLOCKWISE ? 1 : -1;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	@Override
	public AllIcons getIcon() {
		return this == CLOCKWISE ? AllIcons.I_REFRESH : AllIcons.I_ROTATE_CCW;
	}

	@Override
	public String getTranslationKey() {
		return "createfirefightingadd.pipeline_turbine.direction." + serializedName;
	}
}
