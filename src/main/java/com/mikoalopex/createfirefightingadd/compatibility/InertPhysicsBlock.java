package com.mikoalopex.createfirefightingadd.compatibility;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Uses the same saved state properties as the optional block, but never ticks or exposes physics. */
public class InertPhysicsBlock extends WrenchableDirectionalBlock implements EntityBlock {
    private final boolean hook;
    private InertPhysicsBlock(Properties properties, boolean hook) { super(properties); this.hook = hook; }

    public static Block create(Properties properties, boolean hook) {
        return hook ? new Hook(properties) : new InertPhysicsBlock(properties, false);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BooleanProperty.create("axis_along_first"));
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InertPhysicsBlockEntity(hook ? CreateFireFightingAdd.ROPE_LATCH_BE.get()
            : CreateFireFightingAdd.SMART_ROPE_CONNECTOR_BE.get(), pos, state);
    }

    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) { return ItemStack.EMPTY; }

    private static final class Hook extends InertPhysicsBlock {
        private Hook(Properties properties) { super(properties, true); }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(BooleanProperty.create("powered"));
        }
    }
}
