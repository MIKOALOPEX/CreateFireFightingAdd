package com.mikoalopex.createfirefightingadd.integration.sable;

import com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder.ExtensionLadderBlock;

import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public class SableExtensionLadderBlock extends ExtensionLadderBlock implements BlockSubLevelAssemblyListener {
	public SableExtensionLadderBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void afterMove(ServerLevel oldLevel, ServerLevel newLevel, BlockState state, BlockPos oldPos, BlockPos newPos) {
		SableStructureCallbacks.afterExtensionLadderMove(newLevel, state, newPos);
	}
}
