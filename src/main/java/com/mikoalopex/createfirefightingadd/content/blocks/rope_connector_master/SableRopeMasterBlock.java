package com.mikoalopex.createfirefightingadd.content.blocks.rope_connector_master;

import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Assembly moves the inventory; it must not also drop it at the old position. */
final class SableRopeMasterBlock extends RopeMasterBlock implements BlockSubLevelAssemblyListener {
    SableRopeMasterBlock(Properties properties) { super(properties); }

    @Override public void beforeMove(ServerLevel origin, ServerLevel target, BlockState state, BlockPos oldPos, BlockPos newPos) {
        if (origin.getBlockEntity(oldPos) instanceof RopeMasterBlockEntity master) master.beginMove();
    }
    @Override public void afterMove(ServerLevel origin, ServerLevel target, BlockState state, BlockPos oldPos, BlockPos newPos) {
        if (target.getBlockEntity(newPos) instanceof RopeMasterBlockEntity master) master.finishMove();
    }
}
