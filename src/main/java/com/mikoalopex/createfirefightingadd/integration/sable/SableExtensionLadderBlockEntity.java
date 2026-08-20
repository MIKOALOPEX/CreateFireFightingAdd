package com.mikoalopex.createfirefightingadd.integration.sable;

import com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder.ExtensionLadderBlockEntity;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class SableExtensionLadderBlockEntity extends ExtensionLadderBlockEntity implements BlockEntitySubLevelActor {
	public SableExtensionLadderBlockEntity(BlockPos pos, BlockState state) {
		super(pos, state);
	}
}
