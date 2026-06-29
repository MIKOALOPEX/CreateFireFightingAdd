package com.mikoalopex.createfirefightingadd.integration.sable;

import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlockEntity;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SableFireHoseBlockEntity extends FireHoseBlockEntity implements BlockEntitySubLevelActor {

    public SableFireHoseBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    public SableFireHoseBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
    }

    @Override
    @Nullable
    public Iterable<@NotNull SubLevel> sable$getConnectionDependencies() {
        return SableStructureCallbacks.connectionDependencies(getLevel(), getPartnerSubLevelID());
    }
}
