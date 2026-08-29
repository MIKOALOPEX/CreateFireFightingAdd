package com.mikoalopex.createfirefightingadd.content.blocks;

import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Keeps Create's sneak-wrench pickup behaviour for blocks that do not provide
 * a custom wrench action.
 */
public interface FireFightingWrenchableBlock extends IWrenchable {
	@Override
	default InteractionResult onWrenched(BlockState state, UseOnContext context) {
		return InteractionResult.PASS;
	}
}
