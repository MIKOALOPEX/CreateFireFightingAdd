package com.mikoalopex.createfirefightingadd.integration.sable;

import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.BucketControllerBlockEntity;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public class SableBucketControllerBlockEntity extends BucketControllerBlockEntity
    implements BlockEntitySubLevelActor, BlockSubLevelAssemblyListener {

    public SableBucketControllerBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        SableStructureCallbacks.applySprayDevicePropulsion(this, subLevel, timeStep);
    }

    @Override
    public void afterMove(ServerLevel newLevel, ServerLevel oldLevel, BlockState state,
                          BlockPos newPos, BlockPos oldPos) {
        SableStructureCompat.notifyBlockChanged(newLevel, newPos, state);
    }
}
