package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.world.item.ItemStack;

public enum HandheldNozzleType {
	NONE,
	CONE,
	FLAT;

	public static HandheldNozzleType fromNozzleStack(ItemStack stack) {
		if (stack.is(CreateFireFightingAdd.CONE_NOZZLE_ITEM.get()))
			return CONE;
		if (stack.is(CreateFireFightingAdd.FLAT_NOZZLE_ITEM.get()))
			return FLAT;
		return NONE;
	}

	public boolean hasNozzle() {
		return this != NONE;
	}
}
