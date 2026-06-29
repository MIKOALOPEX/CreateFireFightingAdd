package com.mikoalopex.createfirefightingadd.content.blocks;

import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Provides Create's sneak-wrench removal behavior for blocks that do not need a
 * normal wrench action.
 */
public interface FireFightingWrenchableBlock extends IWrenchable {
	@Override
	default InteractionResult onWrenched(BlockState state, UseOnContext context) {
		return InteractionResult.PASS;
	}
}
