package com.mikoalopex.createfirefightingadd.integration.sable;

import com.mikoalopex.createfirefightingadd.content.fluids.coupling.FluidCouplingBlockEntity;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Sable endpoint implementation loaded after capability checks. */
public final class SableFluidCouplingBlockEntity extends FluidCouplingBlockEntity implements BlockEntitySubLevelActor {
    public SableFluidCouplingBlockEntity(BlockPos pos, BlockState state) { super(pos, state); }
    @Override public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double step) {}
    @Override public Iterable<SubLevel> sable$getConnectionDependencies() {
        var other = partner();
        return other == null ? java.util.List.of() : SableStructureCallbacks.connectionDependencies(other.getLevel(),
            SableStructureCompat.containingSubLevelId(other));
    }
}
