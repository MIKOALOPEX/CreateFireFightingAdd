package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import net.minecraft.util.StringRepresentable;

/**
 * Cross-section of a fire hose endpoint. The numeric name is the drawn tube
 * width in sixteenths; the port model is two pixels wider on each side.
 */
public enum FireHoseSize implements StringRepresentable {
	EIGHT(8),
	SIX(6),
	FOUR(4);

	private final int width;

	FireHoseSize(int width) {
		this.width = width;
	}

	public int width() {
		return width;
	}

	public FireHoseSize next() {
		FireHoseSize[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public static FireHoseSize of(int width) {
		return width == 4 ? FOUR : width == 6 ? SIX : EIGHT;
	}

	public float tubeWidth() {
		return width;
	}

	public float textureWidth() {
		return width;
	}

	@Override
	public String getSerializedName() {
		return Integer.toString(width);
	}
}
