package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Starts the same two-endpoint placement workflow as the ordinary hose, but
 * places the independently registered copycat endpoint.
 */
public class CopycatFireHoseItem extends FireHoseItem {

	public CopycatFireHoseItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
			TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		if (Screen.hasShiftDown())
			tooltip.add(Component.translatable("createfirefightingadd.copycat_fire_hose.tooltip")
				.withStyle(ChatFormatting.GRAY));
	}
}
