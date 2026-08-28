package com.mikoalopex.createfirefightingadd.api.fire_hose;

import net.minecraft.util.StringRepresentable;

public enum FireHoseEndpointModel implements StringRepresentable {
	DEFAULT("default"),
	BLACK("black"),
	TRANSPARENT("transparent"),
	CUSTOM("custom");

	private final String name;

	FireHoseEndpointModel(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
