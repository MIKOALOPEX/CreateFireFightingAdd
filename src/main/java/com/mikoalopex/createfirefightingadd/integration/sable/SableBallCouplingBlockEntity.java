package com.mikoalopex.createfirefightingadd.integration.sable;

import com.mikoalopex.createfirefightingadd.content.kinetics.coupling.BallCouplingBlockEntity;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class SableBallCouplingBlockEntity extends BallCouplingBlockEntity implements BlockEntitySubLevelActor {
    public SableBallCouplingBlockEntity(BlockPos pos, BlockState state) { super(pos, state); }
    @Override public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double step) {}
    @Override public Iterable<SubLevel> sable$getConnectionDependencies() {
        BallCouplingBlockEntity other = partner();
        return other == null ? java.util.List.of() : SableStructureCallbacks.connectionDependencies(other.getLevel(),
            SableStructureCompat.containingSubLevelId(other.getLevel(), other.getBlockPos()));
    }
}
