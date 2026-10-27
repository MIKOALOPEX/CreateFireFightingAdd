package com.mikoalopex.createfirefightingadd.integration.sable;

import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.HoseBracketBlock;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.HoseBracketBlockEntity;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Relocates hose route nodes and physical bindings with Sable substructures.
 */
public class SableHoseBracketBlock extends HoseBracketBlock implements BlockSubLevelAssemblyListener {
    public SableHoseBracketBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel newLevel, BlockState state, BlockPos oldPos, BlockPos newPos) {
        if (originLevel.getBlockEntity(oldPos) instanceof HoseBracketBlockEntity bracket)
            bracket.assembling = true;
        if (newLevel.getBlockEntity(oldPos) instanceof HoseBracketBlockEntity bracket)
            bracket.assembling = true;
    }

    @Override
    public void afterMove(ServerLevel oldLevel, ServerLevel newLevel, BlockState state, BlockPos oldPos, BlockPos newPos) {
        if (newLevel.getBlockEntity(newPos) instanceof HoseBracketBlockEntity bracket) {
            bracket.assembling = false;
            if (bracket.physical()) bracket.relocatePhysical();
            else bracket.refreshRoute();
            SableStructureCompat.notifyBlockChanged(newLevel, newPos, state);
        }
    }
}
