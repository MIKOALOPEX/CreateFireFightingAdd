package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ExtensionLadderItem extends Item {
	public ExtensionLadderItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
	}
}
