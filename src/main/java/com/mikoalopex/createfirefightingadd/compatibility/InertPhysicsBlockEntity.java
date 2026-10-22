package com.mikoalopex.createfirefightingadd.compatibility;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Retains optional physics data across saves without constructing third-party objects. */
public final class InertPhysicsBlockEntity extends BlockEntity {
    private CompoundTag retained = new CompoundTag();

    public InertPhysicsBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        retained = tag.copy();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        tag.merge(retained.copy());
        super.saveAdditional(tag, registries);
    }
}
