package com.mikoalopex.createfirefightingadd.integration.simulated;

import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.physical_hose.PhysicalHoseBlock;
import dev.simulated_team.simulated.content.blocks.rope.RopeHolderBlock;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Uses native rope assembly callbacks while leaving room for the attached strand at the head. */
public final class SimulatedPhysicalHoseBlock extends PhysicalHoseBlock
        implements RopeHolderBlock<FireHoseBlockEntity>, BlockSubLevelCollisionShape {
    private static final VoxelShaper COLLIDER = VoxelShaper.forDirectional(box(3, 0, 3, 13, 0.25, 13), Direction.UP);
    public SimulatedPhysicalHoseBlock(Properties properties, int width) { super(properties, width); }

    @Override public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return COLLIDER.get(state.getValue(FACING));
    }

    @Override public void afterMove(ServerLevel origin, ServerLevel target, BlockState state, BlockPos oldPos, BlockPos newPos) {
        RopeHolderBlock.super.afterMove(origin, target, state, oldPos, newPos);
        if (target.getBlockEntity(newPos) instanceof SimulatedPhysicalHoseBlockEntity hose) hose.refreshLink();
    }
}
