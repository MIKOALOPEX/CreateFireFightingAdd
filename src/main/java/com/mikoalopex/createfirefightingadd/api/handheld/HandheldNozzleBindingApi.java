package com.mikoalopex.createfirefightingadd.api.handheld;

import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleControllerItem;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Public helpers for external code that needs to inspect or intentionally
 * clear a handheld nozzle controller binding.
 */
public final class HandheldNozzleBindingApi {
	private HandheldNozzleBindingApi() {
	}

	public enum BindingLocation {
		HELD,
		PLAYER_INVENTORY,
		OUTSIDE_PLAYER_INVENTORY,
		FORCED
	}

	public static boolean isBoundController(ItemStack stack) {
		return stack.getItem() instanceof HandheldNozzleControllerItem
			&& HandheldNozzleControllerItem.isBound(stack);
	}

	public static boolean shouldClearBinding(@Nullable Player owner, ItemStack stack, BindingLocation location) {
		if (!isBoundController(stack))
			return false;
		return shouldClearBinding(owner, location);
	}

	public static boolean shouldClearBinding(@Nullable Player owner, BindingLocation location) {
		return switch (location) {
			case HELD, PLAYER_INVENTORY -> false;
			case OUTSIDE_PLAYER_INVENTORY, FORCED -> true;
		};
	}

	public static void clearBinding(Level level, ItemStack stack, @Nullable Player owner) {
		if (isBoundController(stack))
			HandheldNozzleControllerItem.clearBinding(level, stack, owner);
	}
}
