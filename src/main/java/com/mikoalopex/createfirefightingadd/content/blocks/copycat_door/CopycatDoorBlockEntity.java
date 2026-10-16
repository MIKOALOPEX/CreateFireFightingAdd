package com.mikoalopex.createfirefightingadd.content.blocks.copycat_door;

import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class CopycatDoorBlockEntity extends CopycatBlockEntity {
    private float progress;
    private float previous;
    private boolean initialized;

    public CopycatDoorBlockEntity(BlockPos pos, BlockState state) {
        super(CopycatDoors.BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        animate(getBlockState());
        if (level != null && !level.isClientSide && progress == target(getBlockState())
                && !getBlockState().getValue(CopycatDoorBlock.VISIBLE))
            level.setBlock(worldPosition, getBlockState().setValue(CopycatDoorBlock.VISIBLE, true), 3);
    }

    public void animate(BlockState state) {
        float target = target(state);
        if (state.getBlock() instanceof MultidirectionalTrapdoorBlock) {
            previous = progress = target;
            initialized = true;
            return;
        }
        if (!initialized) {
            progress = state.getValue(CopycatDoorBlock.VISIBLE) ? target : 1 - target;
            initialized = true;
        }
        previous = progress;
        progress += Mth.clamp(target - progress, -.15f, .15f);
    }

    public float progress(float partialTick) {
        return initialized ? Mth.lerp(partialTick, previous, progress) : target(getBlockState());
    }

    private static float target(BlockState state) { return state.getValue(CopycatDoorBlock.OPEN) ? 1 : 0; }

    @Override
    protected AABB createRenderBoundingBox() { return new AABB(worldPosition).inflate(1); }
}
