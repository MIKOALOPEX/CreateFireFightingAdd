package com.mikoalopex.createfirefightingadd.integration.sable;

import com.mikoalopex.createfirefightingadd.content.fluids.water_intake.WaterIntakeBlockEntity;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class SableWaterIntakeBlockEntity extends WaterIntakeBlockEntity implements BlockEntitySubLevelActor {

    public SableWaterIntakeBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }
}
