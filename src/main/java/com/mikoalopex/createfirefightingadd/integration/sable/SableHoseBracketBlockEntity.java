package com.mikoalopex.createfirefightingadd.integration.sable;

import java.util.LinkedHashSet;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.HoseBracketBlockEntity;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Exposes every sublevel referenced by the bracket's visual hose route to Sable. */
public class SableHoseBracketBlockEntity extends HoseBracketBlockEntity implements BlockEntitySubLevelActor {
    public SableHoseBracketBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {}

    @Override
    public Iterable<SubLevel> sable$getConnectionDependencies() {
        var dependencies = new LinkedHashSet<SubLevel>();
        if (route != null)
            for (var node : route.nodes)
                for (SubLevel dependency : SableStructureCallbacks.connectionDependencies(getLevel(), node.subLevel()))
                    dependencies.add(dependency);
        return dependencies;
    }
}
