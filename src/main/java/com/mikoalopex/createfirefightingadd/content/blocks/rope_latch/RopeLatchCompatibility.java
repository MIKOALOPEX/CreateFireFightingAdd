package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.fml.ModList;

/** Keeps the experimental physics integration out of installations without Sable 2. */
public final class RopeLatchCompatibility {
    private RopeLatchCompatibility() {}

    public static boolean available() {
        return ModList.get().isLoaded("simulated") && ModList.get().getModContainerById("sable")
            .map(mod -> mod.getModInfo().getVersion().getMajorVersion() == 2).orElse(false);
    }

    public static Block createBlock(BlockBehaviour.Properties properties) {
        return available() ? Loaded.block(properties) : new InertBlock(properties);
    }

    public static BlockEntity createEntity(BlockPos pos, BlockState state) {
        return available() ? Loaded.entity(pos, state) : null;
    }

    private static final class Loaded {
        static Block block(BlockBehaviour.Properties properties) { return new RopeLatchBlock(properties); }
        static BlockEntity entity(BlockPos pos, BlockState state) {
            return new RopeLatchBlockEntity(CreateFireFightingAdd.ROPE_LATCH_BE.get(), pos, state);
        }
    }

    private static final class InertBlock extends WrenchableDirectionalBlock {
        InertBlock(Properties properties) { super(properties); }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(BooleanProperty.create("axis_along_first"), BooleanProperty.create("powered"));
        }
    }
}
