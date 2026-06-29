package com.mikoalopex.createfirefightingadd.integration.sable;

import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlock;

import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public class SableFireHoseBlock extends FireHoseBlock implements BlockSubLevelAssemblyListener {

    public SableFireHoseBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel newLevel, BlockState newState,
                           BlockPos oldPos, BlockPos newPos) {
        SableStructureCallbacks.beforeFireHoseMove(newLevel, oldPos);
    }

    @Override
    public void afterMove(ServerLevel oldLevel, ServerLevel newLevel, BlockState state,
                          BlockPos oldPos, BlockPos newPos) {
        SableStructureCallbacks.afterFireHoseMove(newLevel, state, newPos);
    }
}
